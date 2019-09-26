package com.example.immortarjourney

import android.Manifest
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.lang.Exception
import java.util.*
import android.bluetooth.BluetoothGattDescriptor



object BluetoothHelper {
    private const val LOCATION_PERMISSION_CODE = 1
    private const val LOCATION_PERMISSION = Manifest.permission.ACCESS_FINE_LOCATION

    /** Check to see we have the necessary permissions for this app.  */
    fun hasLocationPermission(activity: Activity): Boolean {
        return ContextCompat.checkSelfPermission(activity, LOCATION_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    /** Check to see we have the necessary permissions for this app, and ask for them if we don't.  */
    fun requestLocationPermission(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity, arrayOf(LOCATION_PERMISSION), LOCATION_PERMISSION_CODE
        )
    }

    /** Check to see if we need to show the rationale for this permission.  */
    fun shouldShowRequestPermissionRationale(activity: Activity): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, LOCATION_PERMISSION)
    }
}

class BluetoothConnection(private val mixing: Mixing, private val hardwareAddress: Array<String>,
                          private val service: String?, private val characteristic: String?) {
    companion object {
        const val REQUEST_ENABLE_BT = 1
        private const val SCAN_PERIOD: Long = 10000

    }
    private val handler = Handler()
    private var connected = false
    private var connection: BluetoothGatt? = null

    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = mixing.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val scanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)

            if (result?.device?.address !in hardwareAddress)
                return

            // Stop after the first result
            stopScanning()
            if (connected)
                return
            connected = true

            try {
                result?.device?.connectGatt(
                    mixing.applicationContext,
                    false,
                    object : BluetoothGattCallback() {
                        override fun onCharacteristicChanged(
                            gatt: BluetoothGatt?,
                            char: BluetoothGattCharacteristic?
                        ) {
                            super.onCharacteristicChanged(gatt, char)
                            val device = gatt?.device?.address
                            val uuid = char?.uuid
                            Log.println(Log.INFO, "Mortar-BLE", "$device changed $uuid")

                            if (char != null && characteristic != null && uuid == UUID.fromString(characteristic)) {
                                gatt!!.readCharacteristic(char)
                            }
                        }

                        override fun onCharacteristicRead(
                            gatt: BluetoothGatt?,
                            characteristic: BluetoothGattCharacteristic?,
                            status: Int
                        ) {
                            super.onCharacteristicRead(gatt, characteristic, status)
                            // current viscosity value,smoothed viscosity,moistness sensor,humidity,temparature
                            val va = characteristic?.getStringValue(0)
                            val values = va?.split(',')
                            if (va != null && values!!.size == 5) {
                                mixing.sensorData.viscosity = values[1].toFloatOrNull()
                                mixing.sensorData.humidity = values[3].toFloatOrNull()
                                mixing.sensorData.temperature = values[4].toFloatOrNull()

                                mixing.runOnUiThread {
                                    mixing.updateSensorData()
                                }
                            } else {
                                Log.println(Log.INFO, "Mortar-BLE", "Failed to get sensor values from $va")
                            }
                        }

                        override fun onConnectionStateChange(
                            gatt: BluetoothGatt?,
                            status: Int,
                            newState: Int
                        ) {
                            super.onConnectionStateChange(gatt, status, newState)
                            val device = gatt?.device?.address
                            when (newState) {
                                BluetoothProfile.STATE_CONNECTED -> {
                                    Log.println(Log.INFO, "Mortar-BLE", "$device connected")
                                    // Start service discovery
                                    if (!gatt!!.discoverServices()) {
                                        Log.println(Log.INFO, "Mortar-BLE", "Failed to discover services")
                                    } else {
                                        Log.println(Log.INFO, "Mortar-BLE", "Started service discovery")
                                    }
                                    connection = gatt
                                }
                                BluetoothProfile.STATE_DISCONNECTED -> {
                                    Log.println(Log.INFO, "Mortar-BLE", "$device disconnected")
                                    connection?.close()
                                    connection = null
                                }
                            }
                        }

                        override fun onDescriptorRead(
                            gatt: BluetoothGatt?,
                            descriptor: BluetoothGattDescriptor?,
                            status: Int
                        ) {
                            super.onDescriptorRead(gatt, descriptor, status)
                            val device = gatt?.device?.address
                            val desc = descriptor.toString()
                            Log.println(Log.INFO, "Mortar-BLE", "$device has descriptor $desc")
                        }

                        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                            Log.println(Log.INFO, "Mortar-BLE", "Services discovered")
                            super.onServicesDiscovered(gatt, status)
                            val device = gatt?.device?.address
                            when (status) {
                                BluetoothGatt.GATT_SUCCESS -> {
                                    val res = onConnected(gatt!!)
                                    Log.println(Log.INFO, "Mortar-BLE", "$device got $res")
                                }
                                else -> Log.println(Log.INFO, "Mortar-BLE", "$device discovered $status")
                            }
                        }
                    })
            } catch (e: Exception) {
                Log.println(Log.INFO, "Mortar-BLE", "Connection failed")
                e.printStackTrace()
                Toast.makeText(mixing.applicationContext, "Bluetooth connection failed", Toast.LENGTH_SHORT).show()
            }

        }
    }

    private var scanner: BluetoothLeScanner? = null

    fun setup() {
        if (bluetoothAdapter == null) {
            Toast.makeText(mixing.applicationContext, "Bluetooth is not supported", Toast.LENGTH_SHORT).show()
            return
        }

        bluetoothAdapter?.takeIf { !it.isEnabled }?.apply {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            mixing.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }

        val filters = mutableListOf<ScanFilter>()
        /*if (hardwareAddress.isNotEmpty()) {
            filters.add(ScanFilter.Builder().apply {
                //setServiceUuid(ParcelUuid(UUID.fromString(uuid)))
                setDeviceAddress(hardwareAddress)
            }.build())
        }*/

        scanner = bluetoothAdapter!!.bluetoothLeScanner
        // Null when bluetooth is off
        if (scanner != null) {
            Log.println(Log.INFO, "Mortar-BLE", "Starting scan")
            scanner!!.startScan(filters, ScanSettings.Builder().build(), scanCallback)

            handler.postDelayed({
                scanner?.stopScan(scanCallback)
                Log.println(Log.INFO, "Mortar-BLE", "Stopping scan")
                scanner = null
            }, SCAN_PERIOD)
        }
    }

    private fun onConnected(gatt: BluetoothGatt): String? {
        if (service == null)
            return "Service null"

        Log.println(Log.INFO, "Mortar-BLE", "Getting information")

        val srv = gatt.getService(UUID.fromString(service))
        if (srv == null) {
            Log.println(Log.INFO, "Mortar-BLE", "Service $service not found")
            return "service not found"
        }

        val char = srv.getCharacteristic(UUID.fromString(characteristic))
        if (char == null) {
            Log.println(Log.INFO, "Mortar-BLE", "Characteristic $characteristic not found")
            return "char not found"
        }

        if (!gatt.setCharacteristicNotification(char, true)) {
            Log.println(Log.INFO, "Mortar-BLE", "Enabling notifications failed")
            return "no notifications"
        }
        // 0x2902 org.bluetooth.descriptor.gatt.client_characteristic_configuration.xml
        val uuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val descriptor = char.getDescriptor(uuid)
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        gatt.writeDescriptor(descriptor)

        if (!gatt.readCharacteristic(char)) {
            Log.println(Log.INFO, "Mortar-BLE", "Failed to read characteristic")
            return "Failed to read characteristic"
        }

        return null
    }

    private fun stopScanning() {
        scanner?.stopScan(scanCallback)
    }

    fun teardown() {
        Log.println(Log.INFO, "Mortar-BLE", "Teardown")
        scanner?.stopScan(scanCallback)
        connection?.disconnect()
        scanner = null
        connected = false
    }
}