/* Sweep
 by BARRAGAN <http://barraganstudio.com>
 This example code is in the public domain.

 modified 8 Nov 2013
 by Scott Fitzgerald
 http://www.arduino.cc/en/Tutorial/Sweep
*/

#include <Servo.h>
#include "Adafruit_Si7021.h"

Adafruit_Si7021 sensor = Adafruit_Si7021();

Servo myservo;  // create servo object to control a servo
// twelve servo objects can be created on most boards

int pos = 0;    // variable to store the servo position

void setup() {
  Serial.begin(115200);
  myservo.attach(13);  // attaches the servo on pin 9 to the servo object
  myservo.write(180);
  
  sensor.begin();
}

void loop() {
  Serial.print(sensor.readHumidity(), 2);
  Serial.print(",");
  Serial.println(sensor.readTemperature(), 2);
  delay(50);
}
