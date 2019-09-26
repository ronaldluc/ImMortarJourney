package com.example.immortarjourney

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.media.AudioManager
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import kotlinx.coroutines.*
import java.util.*

class Instruction(var until: Date) {
    // Add water in liters
    var addWater: Float? = null
    // Stir until
    var stir: Boolean = false
    // Wait until
    var wait: Boolean = false
    // Add the mortar mixture in kilogram
    var addCement: Float? = null
    // Wait until cooler
    var cool: Boolean = false

    fun applyToTextView(view: TextView) {
        when {
            addWater != null -> {
                view.text = "Add %.2f l of water".format(addWater)
                view.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_water_white, 0, 0, 0)
            }
            stir -> {
                val now = Date()
                // Difference in seconds
                val diff = (until.time - now.time) / 1000
                val time = if (diff > 60)
                    (diff / 60).toString() + " min"
                else
                    "1 min"
                view.text = "Stir for $time"
                view.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_stirrer_black, 0, 0, 0)
            }
            wait -> {
                val now = Date()
                // Difference in seconds
                val diff = (until.time - now.time) / 1000
                val time = if (diff > 60)
                    (diff / 60).toString() + " min"
                else
                    "1 min"
                view.text = "Wait for $time"
                view.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_tutorial_white, 0, 0, 0)
            }
            addCement != null -> {
                view.text = "Add %.2f kg of the mortar mixture".format(addCement)
                view.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_stirrer_black, 0, 0, 0)
            }
            cool -> {
                view.text = "Too hot, wait a while"
                view.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_temperature_black, 0, 0, 0)
            }
            else -> {
                view.text = "Do something"
                view.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            }
        }
    }
}

class SensorData {
    companion object {
        // bad < 0. < ok < 1. < perfect < 2. < ok < 3. < bad
        val temperatureRange = arrayOf(5F, 20F, 25F, 35F)
        val humidityRange = arrayOf(30F, 40F, 60F, 80F)
        const val bad: Int = 0xffff0000.toInt()
        const val ok: Int = 0xffbbbb00.toInt()
        const val perfect: Int = 0xff00ff00.toInt()

        fun getColor(value: Float, range: Array<Float>): Int {
            return when {
                value <= range[0] -> bad
                value <= range[1] -> ok
                value <= range[2] -> perfect
                value <= range[3] -> ok
                else -> bad
            }
        }
    }

    var temperature: Float? = null
    var humidity: Float? = null
    var viscosity: Float? = null
    var moistness: Float? = null

    // Perfect moistness is not a sensor value but computed from the config
    // In percent
    var perfectMoistness: Float = 0F
    // The planned amount of powder in kg
    var perfectPowder: Float = 0F
}

class Mixing : AppCompatActivity(), TextToSpeech.OnInitListener {
    private val viewModelJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)

    var uploading: Int = 0
    private val bluetooth = BluetoothConnection(this, arrayOf("B4:E6:2D:A8:37:17", "CC:50:E3:9C:2A:16"),
        "4FAFC201-1FB5-459E-8FCC-C5C9C331914B", "BEB5483E-36E1-4688-B7F5-EA07361B26A8")
    lateinit var tts: TextToSpeech
    var ttsReady = false
    var sensorData = SensorData()
    var cameraDevice: CameraDevice? = null
    private var imageReader: ImageReader? = null
    var instruction = Instruction(Date(Date().time + 1000 * 10)).apply {
        addCement = sensorData.perfectPowder
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mixing)
        tts = TextToSpeech(this, this)

        if (!CameraHelper.hasCameraPermission(this)) {
            CameraHelper.requestCameraPermission(this)
            return
        }

        if (!BluetoothHelper.hasLocationPermission(this)) {
            BluetoothHelper.requestLocationPermission(this)
            return
        }

        val surfaceView = findViewById<SurfaceView>(R.id.surfaceView)
        surfaceView.holder.addCallback(object: SurfaceHolder.Callback {
            override fun surfaceChanged(p0: SurfaceHolder?, p1: Int, p2: Int, p3: Int) {
                Log.println(Log.INFO, "Mortar", "Surface changed")
            }
            override fun surfaceDestroyed(p0: SurfaceHolder?) {
                Log.println(Log.INFO, "Mortar", "Surface destroyed")
                cameraDevice?.close()
            }

            override fun surfaceCreated(p0: SurfaceHolder?) {
                Log.println(Log.INFO, "Mortar", "Surface created")
                startCamera()
            }
        })

        bluetooth.setup()

        val moisture = intent.getFloatExtra("moisture", 0.33F)
        if (moisture == 0F)
            finish()
        sensorData.perfectMoistness = moisture
        sensorData.moistness = 0F
        sensorData.perfectPowder = intent.getFloatExtra("cement", 50F)

        instruction.applyToTextView(findViewById(R.id.instruction_text))

        uiScope.launch {
            updateSensorData()
            while (true) {
                delay(1000)
                val now = Date()
                Log.println(Log.INFO, "Mortar-Instructions", "${instruction.until.time} <= ${now.time}")
                if (instruction.until.time <= now.time) {
                    nextInstruction()
                }
            }
        }
    }

    private fun startCamera() {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var bestCam: String? = null
        try {
            if (manager.cameraIdList.isEmpty()) {
                Toast.makeText(applicationContext, "You need a camera", Toast.LENGTH_SHORT).show()
                return
            }

            for (cameraId in manager.cameraIdList) {
                val chars = manager.getCameraCharacteristics(cameraId)
                val facing = chars[CameraCharacteristics.LENS_FACING]
                if (facing != CameraCharacteristics.LENS_FACING_FRONT) {
                    bestCam = cameraId
                }
            }

            if (bestCam == null) {
                bestCam = manager.cameraIdList[0]
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(applicationContext, "Failed to find a camera", Toast.LENGTH_SHORT).show()
        }

        try {
            manager.openCamera(bestCam!!, object: CameraDevice.StateCallback() {
                override fun onDisconnected(p0: CameraDevice) {
                    Log.println(Log.INFO, "Mortar", "Closed camera")
                }
                override fun onError(p0: CameraDevice, p1: Int) {
                    Log.println(Log.INFO, "Mortar", "Failed to open camera")
                }

                override fun onOpened(cam: CameraDevice) {
                    cameraDevice = cam
                    useCamera(cam)
                }
            }, Handler { true })
        } catch (e: SecurityException) {
            e.printStackTrace()
            Toast.makeText(applicationContext, "Failed to obtain camera permissions", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(applicationContext, "Failed to use camera", Toast.LENGTH_SHORT).show()
        }
    }

    private fun useCamera(cameraDevice: CameraDevice) {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraCharacteristics = manager.getCameraCharacteristics(cameraDevice.id)

        cameraCharacteristics[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]?.let { streamConfigurationMap ->
            val res = listOf(ImageFormat.JPEG).mapNotNull {
                when (val sizes = streamConfigurationMap.getOutputSizes(it)) {
                    null -> null
                    else -> Pair(sizes, it)
                }
            }.firstOrNull()
            if (res == null) {
                Toast.makeText(applicationContext, "No suitable format found", Toast.LENGTH_SHORT).show()
                return
            }
            val (outputSizes, format) = res
            val previewSize = outputSizes.first()

            val displayRotation = windowManager.defaultDisplay.rotation
            val swappedDimensions = CameraHelper.areDimensionsSwapped(displayRotation, cameraCharacteristics)
            val rotatedPreviewWidth = if (swappedDimensions) previewSize.height else previewSize.width
            val rotatedPreviewHeight = if (swappedDimensions) previewSize.width else previewSize.height

            val surfaceView = findViewById<SurfaceView>(R.id.surfaceView)
            surfaceView.holder.setFixedSize(rotatedPreviewWidth, rotatedPreviewHeight)

            imageReader = ImageReader.newInstance(rotatedPreviewWidth, rotatedPreviewHeight,
                format, 2)
            imageReader!!.setOnImageAvailableListener({
                try {
                    imageReader!!.acquireLatestImage()?.let { image ->
                        processImage(image)
                    }
                } catch(e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(applicationContext, "Failed to process camera image", Toast.LENGTH_SHORT).show()
                }
            }, Handler { true })

            val previewSurface = surfaceView.holder.surface
            val recordingSurface = imageReader!!.surface

            val captureCallback = object : CameraCaptureSession.StateCallback()
            {
                override fun onConfigureFailed(session: CameraCaptureSession) {}

                override fun onConfigured(session: CameraCaptureSession) {
                    // session configured
                    val previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        .apply {
                            addTarget(previewSurface)
                            addTarget(recordingSurface)
                        }
                    session.setRepeatingRequest(previewRequestBuilder.build(),
                        object: CameraCaptureSession.CaptureCallback() {},
                        Handler { true }
                    )
                }
            }

            cameraDevice.createCaptureSession(mutableListOf(previewSurface, recordingSurface),
                captureCallback, Handler { true })
        }
    }

    private fun processImage(image: Image) {
        if (uploading != 0) {
            image.close()
            return
        }
        uploading += 1

        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        image.close()
        WebUploadTask(this).execute(bytes)
    }

    private fun setStartMargin(view: View, margin: Int) {
        val params = view.layoutParams as ConstraintLayout.LayoutParams
        params.marginStart = margin
        view.layoutParams = params
    }

    fun updateSensorData() {
        val tempText = findViewById<TextView>(R.id.temparature_text)
        if (sensorData.temperature == null) {
            tempText.text = "- ᵒC"
            tempText.setTextColor(Color.BLACK)
        } else {
            tempText.text = "%.2f ᵒC".format(sensorData.temperature)
            tempText.setTextColor(SensorData.getColor(sensorData.temperature!!, SensorData.temperatureRange))
        }

        val humText = findViewById<TextView>(R.id.humidity_text)
        if (sensorData.humidity == null) {
            humText.text = "- %"
            humText.setTextColor(Color.BLACK)
        } else {
            humText.text = "%.2f %%".format(sensorData.humidity)
            humText.setTextColor(SensorData.getColor(sensorData.humidity!!, SensorData.humidityRange))
        }

        // Moisture: Put perfect at 85%
        val moistSize = sensorData.perfectMoistness / 0.8
        val perfectMoistureText = findViewById<TextView>(R.id.perfect_moisture_text)
        val perfectLevel = findViewById<View>(R.id.perfect_level)
        val currentMoistureText = findViewById<TextView>(R.id.current_moisture_text)
        val currentLevel = findViewById<View>(R.id.current_level)
        var moistureBar = findViewById<ConstraintLayout>(R.id.moisture_bar)
        val size = moistureBar.width

        setStartMargin(perfectLevel, (0.8 * size).toInt())
        setStartMargin(perfectMoistureText, (0.8 * size).toInt() + 5)
        perfectMoistureText.text = "%.2f l".format(sensorData.perfectMoistness * sensorData.perfectPowder)
        if (sensorData.moistness == null) {
            setStartMargin(currentLevel, 0)
            setStartMargin(currentMoistureText, 5)
            currentMoistureText.text = "0 l"
        } else {
            val curPercentage = sensorData.moistness!!

            val params = currentLevel.layoutParams as ConstraintLayout.LayoutParams
            params.width = (curPercentage / moistSize * size).toInt()
            currentLevel.layoutParams = params

            currentMoistureText.text = "%.2f l".format(curPercentage * sensorData.perfectPowder)
            setStartMargin(currentMoistureText, (curPercentage / moistSize * size).toInt() + 5)
        }
    }

    private fun nextInstruction() {
        if (sensorData.moistness == null)
            return

        if (instruction.addWater != null || instruction.addCement != null) {
            instruction = Instruction(Date(Date().time + 1000 * 20)).apply {
                stir = true
            }
        } else if (sensorData.moistness!! < sensorData.perfectMoistness - 0.05) {
            instruction = Instruction(Date(Date().time + 1000 * 10)).apply {
                addWater = sensorData.perfectPowder * (sensorData.perfectMoistness - sensorData.moistness!!)
            }
        } else if (sensorData.moistness!! > sensorData.perfectMoistness + 0.05) {
            instruction = Instruction(Date(Date().time + 1000 * 10)).apply {
                addCement = sensorData.perfectPowder * (sensorData.moistness!! - sensorData.perfectMoistness)
            }
        } else {
            instruction = Instruction(Date(Date().time + 1000 * 60 * 5)).apply {
                wait = true
            }
        }

        instruction.applyToTextView(findViewById(R.id.instruction_text))
        val text = findViewById<TextView>(R.id.instruction_text).text
        if (ttsReady && !tts.isSpeaking) {
            if (tts.speak(
                    text,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    ""
                ) != TextToSpeech.SUCCESS
            ) {
                Log.println(Log.INFO, "Mortar-TTS", "Failed to start tts")
                Toast.makeText(
                    applicationContext,
                    "Text to Speech failed",
                    Toast.LENGTH_SHORT
                ).show()
            }

            val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val musicVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)

            if (musicVolume <= 2) {
                Toast.makeText(applicationContext, "Turn up your music volume!", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    fun skipInstruction(view: View) {
        nextInstruction()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (!CameraHelper.hasCameraPermission(this)) {
            Toast.makeText(this, "Bro, we need the camera", Toast.LENGTH_LONG).show()
            if (!CameraHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraHelper.launchPermissionSettings(this)
            }
            finish()
        }
        if (!BluetoothHelper.hasLocationPermission(this)) {
            Toast.makeText(this, "Bro, we need blue teeth", Toast.LENGTH_LONG).show()
            if (!BluetoothHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraHelper.launchPermissionSettings(this)
            }
            finish()
        }

        recreate()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == BluetoothConnection.REQUEST_ENABLE_BT) {
            if (resultCode != Activity.RESULT_OK) {
                Toast.makeText(this, "Bro, we need blue teeth", Toast.LENGTH_LONG).show()
            } else {
                recreate()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetooth.teardown()
        ttsReady = false
        tts.shutdown()
    }
}
