/*
    Video: https://www.youtube.com/watch?v=oCMOYS71NIU
    Based on Neil Kolban example for IDF: https://github.com/nkolban/esp32-snippets/blob/master/cpp_utils/tests/BLE%20Tests/SampleNotify.cpp
    Ported to Arduino ESP32 by Evandro Copercini
    updated by chegewara

   Create a BLE server that, once we receive a connection, will send periodic notifications.
   The service advertises itself as: 4fafc201-1fb5-459e-8fcc-c5c9c331914b
   And has a characteristic of: beb5483e-36e1-4688-b7f5-ea07361b26a8

   The design of creating the BLE server is:
   1. Create a BLE Server
   2. Create a BLE Service
   3. Create a BLE Characteristic on the Service
   4. Create a BLE Descriptor on the characteristic
   5. Start the service.
   6. Start advertising.

   A connect hander associated with the server starts a background task that performs notification
   every couple of seconds.
*/
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

#include "Adafruit_Si7021.h"

#define MOISTURE_PIN 34
#define SERVO_PIN 35

//#define RESISTANCES_NUM 500
//#define RESISTANCES_STEP 10
#define RESISTANCES_STEP_DIV 1.01

#include <Wire.h>
#include "Adafruit_INA219.h"

Adafruit_INA219 ina219;

BLEServer* pServer = NULL;
BLECharacteristic* pCharacteristic = NULL;
bool deviceConnected = false;
bool oldDeviceConnected = false;
uint32_t value = 0;
char messege[100], line[30], lineTemp[30];
int line_i;
//float resistances[200];
double resistance_sum;

Adafruit_Si7021 sensor = Adafruit_Si7021();

// See the following for generating UUIDs:
// https://www.uuidgenerator.net/

#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"


class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      deviceConnected = true;
      BLEDevice::startAdvertising();
    };

    void onDisconnect(BLEServer* pServer) {
      deviceConnected = false;
    }
};

void readLine() {
  while (Serial2.available()) {
    char c = 0;
    do {
      c = Serial2.read();
      lineTemp[line_i] = c;
      line_i++;
      lineTemp[line_i] = 0;
//      if(!Serial2.available()) delay(3);
    } while(Serial2.available() and c != '\n' and line_i < 25);
    if(c == '\n' or line_i >= 24) {
      lineTemp[line_i-1] = 0;
      for(int i = 0; i < line_i; i++) line[i] = lineTemp[i];
      line_i = 0;
    }
  }
}


float getResistance(float new_resistance) {
  resistance_sum /= RESISTANCES_STEP_DIV;
  resistance_sum += new_resistance;
  return resistance_sum / 101;
}

//float getResistance(float new_resistance) {
//  resistances[resistance_i] = new_resistance;
//  float multiplier = 1, sum = 0, divisor = 0;
//  for(int i = resistance_i, j = 0; i != (resistance_i + 1) % RESISTANCES_NUM; i--, j++) {
//    if(i < 0) {
//      i = RESISTANCES_NUM - 1;
//      if(i == (resistance_i + 1)) break;
//    }
//    if(j == RESISTANCES_STEP) {
//      j = 0;
//      multiplier /= RESISTANCES_STEP_DIV;
//    }
//    divisor += multiplier;
//    sum += resistance
//  }
//  resistance_i = (resistance_i + 1) % RESISTANCES_NUM;
//}


void setup() {
  line_i = 0;
  Serial.begin(115200);
  Serial2.begin(115200);

  // Create the BLE Device
  BLEDevice::init("ESP32");

  // Create the BLE Server
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  // Create the BLE Service
  BLEService *pService = pServer->createService(SERVICE_UUID);

  // Create a BLE Characteristic
  pCharacteristic = pService->createCharacteristic(
                      CHARACTERISTIC_UUID,
                      BLECharacteristic::PROPERTY_READ   |
                      BLECharacteristic::PROPERTY_WRITE  |
                      BLECharacteristic::PROPERTY_NOTIFY |
                      BLECharacteristic::PROPERTY_INDICATE
                    );

  // https://www.bluetooth.com/specifications/gatt/viewer?attributeXmlFile=org.bluetooth.descriptor.gatt.client_characteristic_configuration.xml
  // Create a BLE Descriptor
  pCharacteristic->addDescriptor(new BLE2902());

  // Start the service
  pService->start();

  // Start advertising
  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(false);
  pAdvertising->setMinPreferred(0x0);  // set value to 0x00 to not advertise this parameter
  BLEDevice::startAdvertising();
  Serial.println("Waiting a client connection to notify...");

  sensor.begin();
  
  pinMode(MOISTURE_PIN, INPUT);
  
  ina219.begin();
}

void loop() {
    // notify changed value
    if (deviceConnected) {
        float humidity = sensor.readHumidity();
        float temperature = sensor.readTemperature();
        float shuntvoltage = ina219.getShuntVoltage_mV();
        float busvoltage = ina219.getBusVoltage_V();
        float current_mA = ina219.getCurrent_mA();
        float loadvoltage = busvoltage + (shuntvoltage / 1000);
        float resistance = loadvoltage / current_mA * 1000;
        float moisture = analogRead(MOISTURE_PIN) / 4096.0;

        readLine();
        float mean_resistance = getResistance(resistance);
//        line[0] = 'a';
//        line[1] = 0;
      
        sprintf(messege, "%f,%f,%f,%s", resistance, mean_resistance, moisture, line);
        Serial.println(messege);
        pCharacteristic->setValue(messege);
        pCharacteristic->notify();
        value++;
        delay(80); // bluetooth stack will go into congestion, if too many packets are sent, in 6 hours test i was able to go as low as 3ms
    }
    // disconnecting
    if (!deviceConnected && oldDeviceConnected) {
        delay(500); // give the bluetooth stack the chance to get things ready
        pServer->startAdvertising(); // restart advertising
        Serial.println("start advertising");
        oldDeviceConnected = deviceConnected;
    }
    // connecting
    if (deviceConnected && !oldDeviceConnected) {
        // do stuff here on connecting
        oldDeviceConnected = deviceConnected;
    }
}
