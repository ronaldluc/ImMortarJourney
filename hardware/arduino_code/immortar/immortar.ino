#define MOISTURE_PIN 34
#define SERVO_PIN 35

#include <Wire.h>
#include "Adafruit_INA219.h"

Adafruit_INA219 ina219;

void setup(void) 
{
  delay(5000);
  Serial.begin(115200);
  while (!Serial) {
      // will pause Zero, Leonardo, etc until serial console opens
      delay(1);
  }
  pinMode(MOISTURE_PIN, INPUT);

  uint32_t currentFrequency;
    
  Serial.println("Hello!");
  
  // Initialize the INA219.
  // By default the initialization will use the largest range (32V, 2A).  However
  // you can call a setCalibration function to change this range (see comments).
  ina219.begin();
  // To use a slightly lower 32V, 1A range (higher precision on amps):
  //ina219.setCalibration_32V_1A();
  // Or to use a lower 16V, 400mA range (higher precision on volts and amps):
  //ina219.setCalibration_16V_400mA();

  Serial.println("Measuring voltage and current with INA219 ...");
}

void loop(void) 
{
  float shuntvoltage = ina219.getShuntVoltage_mV();
  float busvoltage = ina219.getBusVoltage_V();
  float current_mA = ina219.getCurrent_mA();
//  power_mW = ina219.getPower_mW();
  float loadvoltage = busvoltage + (shuntvoltage / 1000);
  float resistance = loadvoltage / current_mA * 1000;

  int moisture = analogRead(MOISTURE_PIN);
  
//  Serial.print(""); Serial.print(moisture);
//  Serial.print(","); 
  Serial.print(resistance);
  Serial.println("");

  delay(200);
}
