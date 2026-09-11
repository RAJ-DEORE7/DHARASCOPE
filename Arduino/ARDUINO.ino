#include <SPI.h>
#include <RF24.h>

// ===============================
// nRF24 - SAME WORKING PINS
// ===============================

#define CE_PIN 2
#define CSN_PIN 3

RF24 radio(CE_PIN, CSN_PIN);

const byte address[6] = "NODE1";


// ===============================
// SENSOR PINS
// ===============================

#define IR_PIN 4

#define TRIG_PIN 6
#define ECHO_PIN 7


// ===============================
// DATA PACKET
// ===============================

struct SensorData {

  unsigned long sequence;

  float distance;

  int ir;

};

SensorData data;


// ===============================
// ULTRASONIC FUNCTION
// ===============================

float readDistance() {

  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);

  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);

  digitalWrite(TRIG_PIN, LOW);

  long duration = pulseIn(ECHO_PIN, HIGH, 30000);

  if (duration == 0) {
    return -1.0;
  }

  float distance = duration * 0.0343 / 2.0;

  return distance;
}


// ===============================
// SETUP
// ===============================

void setup() {

  Serial.begin(115200);

  pinMode(IR_PIN, INPUT);

  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);


  Serial.println();
  Serial.println("================================");
  Serial.println("MINE MONITORING SENSOR NODE");
  Serial.println("================================");


  // nRF24

  if (!radio.begin()) {

    Serial.println("ERROR: nRF24 NOT DETECTED!");

    while (1) {
      delay(1000);
    }
  }

  Serial.println("nRF24 detected!");


  radio.setPALevel(RF24_PA_LOW);

  radio.setDataRate(RF24_250KBPS);

  radio.openWritingPipe(address);

  radio.stopListening();


  data.sequence = 0;

  Serial.println("Sensor node started.");
  Serial.println();
}


// ===============================
// LOOP
// ===============================

void loop() {

  // Packet number

  data.sequence++;


  // Read sensors

  data.distance = readDistance();

  data.ir = digitalRead(IR_PIN);


  // Send data

  bool success = radio.write(
    &data,
    sizeof(data)
  );


  // ===============================
  // SERIAL MONITOR
  // ===============================

  Serial.print("Packet: ");
  Serial.print(data.sequence);

  Serial.print(" | Distance: ");
  Serial.print(data.distance);
  Serial.print(" cm");

  Serial.print(" | IR: ");
  Serial.print(data.ir);

  Serial.print(" | Radio: ");

  if (success) {
    Serial.println("SENT");
  }
  else {
    Serial.println("FAILED");
  }


  delay(1000);
}
