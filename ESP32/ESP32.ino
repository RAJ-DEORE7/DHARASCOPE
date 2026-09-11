#include <WiFi.h>
#include <WebServer.h>
#include <SPI.h>
#include <RF24.h>
#include <time.h>

// =====================================================
// WIFI
// =====================================================

const char* WIFI_SSID = "YOUR_WIFI_NAME";
const char* WIFI_PASSWORD = "YOUR_WIFI_PASSWORD";

// =====================================================
// NRF24 - SAME CONNECTION AS BEFORE
// =====================================================

#define CE_PIN 4
#define CSN_PIN 5
#define SCK_PIN 18
#define MISO_PIN 19
#define MOSI_PIN 23

RF24 radio(CE_PIN, CSN_PIN);

const byte address[6] = "NODE1";

// =====================================================
// WEB SERVER
// =====================================================

WebServer server(80);

// =====================================================
// MINE / SENSOR SETTINGS
// =====================================================

// Mine/prototype reference distance
const float BASELINE_DISTANCE = 25.00;

// Small ultrasonic changes below this are treated as noise
const float CHANGE_THRESHOLD = 0.20;

// Warning / critical levels
const float WARNING_DEVIATION = 1.00;
const float CRITICAL_DEVIATION = 3.00;

// =====================================================
// DATA FROM ARDUINO
// =====================================================

struct SensorData {
  unsigned long sequence;
  float distance;
  int ir;
};

SensorData data;

// =====================================================
// MONITORING VARIABLES
// =====================================================

float previousDistance = -1.0;

float currentChange = 0.0;
float changeRate = 0.0;

int previousIR = -1;

unsigned long lastPacketMillis = 0;
unsigned long lastChangeMillis = 0;

bool hasPreviousReading = false;
bool hasChange = false;

String lastChangeTime = "N/A";
String lastSensorSource = "NONE";

unsigned long resetCount = 0;

// =====================================================
// TIME
// =====================================================

// India = UTC + 5:30
const long GMT_OFFSET_SEC = 19800;
const int DAYLIGHT_OFFSET_SEC = 0;

bool timeSynced = false;

// Return real date/time
String getRealTime() {

  struct tm timeinfo;

  if (!getLocalTime(&timeinfo)) {
    return "TIME_NOT_SYNCED";
  }

  char buffer[30];

  strftime(
    buffer,
    sizeof(buffer),
    "%d-%m-%Y %H:%M:%S",
    &timeinfo
  );

  return String(buffer);
}

// =====================================================
// SENSOR STATUS
// =====================================================

String getStatus() {

  if (data.distance < 0) {
    return "INVALID";
  }

  float deviation = abs(data.distance - BASELINE_DISTANCE);

  if (deviation >= CRITICAL_DEVIATION) {
    return "CRITICAL";
  }

  if (deviation >= WARNING_DEVIATION) {
    return "WARNING";
  }

  return "NORMAL";
}

// =====================================================
// CHANGE DIRECTION
// =====================================================

String getDirection() {

  if (!hasPreviousReading) {
    return "STABLE";
  }

  if (abs(currentChange) < CHANGE_THRESHOLD) {
    return "STABLE";
  }

  if (currentChange > 0) {
    return "INCREASING";
  }

  return "DECREASING";
}

// =====================================================
// RESET MONITORING
// =====================================================

void resetMonitoring() {

  data.sequence = 0;

  data.distance = -1.0;
  data.ir = -1;

  previousDistance = -1.0;

  currentChange = 0.0;
  changeRate = 0.0;

  previousIR = -1;

  lastPacketMillis = 0;
  lastChangeMillis = 0;

  hasPreviousReading = false;
  hasChange = false;

  lastChangeTime = "N/A";
  lastSensorSource = "NONE";

  resetCount++;

  Serial.println();
  Serial.println("================================");
  Serial.println("MONITORING RESET");
  Serial.println("================================");
  Serial.println("Baseline: 25.00 cm");
  Serial.println("Packet counter: 0");
  Serial.println("Previous reading: CLEARED");
  Serial.println("Change: 0");
  Serial.println("Change rate: 0");
  Serial.println("================================");
  Serial.println();
}

// =====================================================
// JSON API
// =====================================================

void sendSensorData() {

  String now = getRealTime();

  String json = "{";

  json += "\"node\":\"NODE1\",";

  json += "\"timestamp\":\"";
  json += now;
  json += "\",";

  json += "\"sequence\":";
  json += String(data.sequence);
  json += ",";

  json += "\"baselineDistance\":";
  json += String(BASELINE_DISTANCE, 2);
  json += ",";

  json += "\"distance\":";
  json += String(data.distance, 2);
  json += ",";

  json += "\"change\":";
  json += String(currentChange, 2);
  json += ",";

  json += "\"changeRate\":";
  json += String(changeRate, 4);
  json += ",";

  json += "\"direction\":\"";
  json += getDirection();
  json += "\",";

  json += "\"ir\":";
  json += String(data.ir);
  json += ",";

  json += "\"sensorSource\":\"";
  json += lastSensorSource;
  json += "\",";

  json += "\"status\":\"";
  json += getStatus();
  json += "\",";

  json += "\"lastChangeTime\":\"";
  json += lastChangeTime;
  json += "\",";

  json += "\"resetCount\":";
  json += String(resetCount);
  json += ",";

  json += "\"uptimeMs\":";
  json += String(millis());

  json += "}";

  // Allow app to access ESP32 API
  server.sendHeader("Access-Control-Allow-Origin", "*");

  server.send(
    200,
    "application/json",
    json
  );
}

// =====================================================
// RESET API
// =====================================================

void resetAPI() {

  resetMonitoring();

  server.sendHeader(
    "Access-Control-Allow-Origin",
    "*"
  );

  server.send(
    200,
    "application/json",
    "{\"success\":true,\"message\":\"Monitoring reset successfully\"}"
  );
}

// =====================================================
// ROOT PAGE
// =====================================================

void rootPage() {

  String html = "";

  html += "<!DOCTYPE html>";
  html += "<html>";
  html += "<head>";

  html += "<meta name='viewport' content='width=device-width, initial-scale=1'>";

  html += "<title>Mine Monitoring Gateway</title>";

  html += "<style>";

  html += "body{font-family:Arial;text-align:center;padding:30px;}";
  html += "button{font-size:20px;padding:15px 30px;cursor:pointer;}";
  html += "#data{margin-top:20px;text-align:left;display:inline-block;}";

  html += "</style>";

  html += "</head>";

  html += "<body>";

  html += "<h1>Mine Monitoring Gateway</h1>";

  html += "<p>Baseline Distance: <b>25.00 cm</b></p>";

  html += "<button onclick='resetSystem()'>RESET MONITORING</button>";

  html += "<div id='data'>Loading...</div>";

  html += "<script>";

  html += "async function loadData(){";
  html += "let r=await fetch('/api/data');";
  html += "let d=await r.json();";

  html += "document.getElementById('data').innerHTML=";

  html += "'Time: '+d.timestamp+'<br>'+";
  html += "'Packet: '+d.sequence+'<br>'+";
  html += "'Distance: '+d.distance+' cm<br>'+";
  html += "'Baseline: '+d.baselineDistance+' cm<br>'+";
  html += "'Change: '+d.change+' cm<br>'+";
  html += "'Speed: '+d.changeRate+' cm/s<br>'+";
  html += "'Direction: '+d.direction+'<br>'+";
  html += "'Sensor: '+d.sensorSource+'<br>'+";
  html += "'Status: '+d.status+'<br>'+";
  html += "'Last Change: '+d.lastChangeTime;";

  html += "}";

  html += "async function resetSystem(){";
  html += "await fetch('/api/reset');";
  html += "alert('Monitoring reset successfully');";
  html += "loadData();";
  html += "}";

  html += "loadData();";
  html += "setInterval(loadData,1000);";

  html += "</script>";

  html += "</body>";
  html += "</html>";

  server.send(200, "text/html", html);
}

// =====================================================
// SETUP
// =====================================================

void setup() {

  Serial.begin(115200);

  delay(1000);

  Serial.println();
  Serial.println("======================================");
  Serial.println("ESP32 MINE MONITORING GATEWAY");
  Serial.println("======================================");

  // ---------------------------------------------------
  // NRF24
  // ---------------------------------------------------

  SPI.begin(
    SCK_PIN,
    MISO_PIN,
    MOSI_PIN,
    CSN_PIN
  );

  if (!radio.begin()) {

    Serial.println(
      "ERROR: nRF24 NOT DETECTED!"
    );

    while (1) {
      delay(1000);
    }
  }

  Serial.println("nRF24 detected!");

  radio.setPALevel(RF24_PA_LOW);

  radio.setDataRate(RF24_250KBPS);

  radio.openReadingPipe(
    1,
    address
  );

  radio.startListening();

  // ---------------------------------------------------
  // WIFI
  // ---------------------------------------------------

  WiFi.begin(
    WIFI_SSID,
    WIFI_PASSWORD
  );

  Serial.print("Connecting to WiFi");

  while (
    WiFi.status() != WL_CONNECTED
  ) {

    delay(500);

    Serial.print(".");
  }

  Serial.println();

  Serial.println("WiFi connected!");

  Serial.print("ESP32 IP: ");

  Serial.println(
    WiFi.localIP()
  );

  // ---------------------------------------------------
  // REAL TIME CLOCK / NTP
  // ---------------------------------------------------

  configTime(
    GMT_OFFSET_SEC,
    DAYLIGHT_OFFSET_SEC,
    "pool.ntp.org",
    "time.nist.gov"
  );

  Serial.print("Synchronizing real time");

  struct tm timeinfo;

  int attempts = 0;

  while (
    !getLocalTime(&timeinfo) &&
    attempts < 20
  ) {

    delay(500);

    Serial.print(".");

    attempts++;
  }

  if (getLocalTime(&timeinfo)) {

    timeSynced = true;

    Serial.println();
    Serial.println("Real time synchronized!");

    Serial.print("Current time: ");
    Serial.println(getRealTime());

  } else {

    Serial.println();
    Serial.println(
      "WARNING: Real time not synchronized!"
    );
  }

  // ---------------------------------------------------
  // WEB SERVER
  // ---------------------------------------------------

  server.on(
    "/",
    rootPage
  );

  server.on(
    "/api/data",
    sendSensorData
  );

  server.on(
    "/api/reset",
    resetAPI
  );

  server.begin();

  Serial.println(
    "Web server started!"
  );

  Serial.println();

  Serial.println(
    "API: /api/data"
  );

  Serial.println(
    "RESET: /api/reset"
  );

  Serial.println();

  Serial.println(
    "Baseline = 25.00 cm"
  );

  Serial.println(
    "Monitoring started."
  );

  Serial.println();
}

// =====================================================
// LOOP
// =====================================================

void loop() {

  server.handleClient();

  // ---------------------------------------------------
  // RECEIVE nRF24 DATA
  // ---------------------------------------------------

  if (radio.available()) {

    radio.read(
      &data,
      sizeof(data)
    );

    unsigned long nowMillis = millis();

    // -----------------------------------------------
    // FIRST READING AFTER RESET
    // -----------------------------------------------

    if (!hasPreviousReading) {

      previousDistance = data.distance;

      previousIR = data.ir;

      lastPacketMillis = nowMillis;

      hasPreviousReading = true;

      currentChange = 0.0;

      changeRate = 0.0;

      lastSensorSource = "INITIAL_READING";

    }

    // -----------------------------------------------
    // NORMAL READING
    // -----------------------------------------------

    else {

      currentChange =
        data.distance - previousDistance;

      // ---------------------------------------------
      // CHANGE SPEED
      // ---------------------------------------------

      unsigned long timeDifference =
        nowMillis - lastPacketMillis;

      if (
        timeDifference > 0 &&
        data.distance >= 0 &&
        previousDistance >= 0
      ) {

        float seconds =
          timeDifference / 1000.0;

        changeRate =
          currentChange / seconds;
      }

      // ---------------------------------------------
      // SENSOR SOURCE
      // ---------------------------------------------

      bool ultrasonicChanged =
        abs(currentChange) >= CHANGE_THRESHOLD;

      bool irChanged =
        (data.ir != previousIR);

      if (
        ultrasonicChanged &&
        irChanged
      ) {

        lastSensorSource = "BOTH";

      } else if (
        ultrasonicChanged
      ) {

        lastSensorSource = "ULTRASONIC";

      } else if (
        irChanged
      ) {

        lastSensorSource = "IR";

      } else {

        lastSensorSource = "NONE";
      }

      // ---------------------------------------------
      // SIGNIFICANT CHANGE TIME
      // ---------------------------------------------

      if (
        ultrasonicChanged ||
        irChanged
      ) {

        hasChange = true;

        lastChangeMillis = nowMillis;

        lastChangeTime = getRealTime();

      }

      // ---------------------------------------------
      // UPDATE PREVIOUS VALUES
      // ---------------------------------------------

      previousDistance =
        data.distance;

      previousIR =
        data.ir;

      lastPacketMillis =
        nowMillis;
    }

    // ------------------------------------------------
    // SERIAL MONITOR
    // ------------------------------------------------

    Serial.println(
      "--------------------------------------"
    );

    Serial.print("Time: ");
    Serial.println(getRealTime());

    Serial.print("Packet: ");
    Serial.println(data.sequence);

    Serial.print("Baseline: ");
    Serial.print(BASELINE_DISTANCE, 2);
    Serial.println(" cm");

    Serial.print("Distance: ");
    Serial.print(data.distance, 2);
    Serial.println(" cm");

    Serial.print("Change: ");
    Serial.print(currentChange, 2);
    Serial.println(" cm");

    Serial.print("Change Speed: ");
    Serial.print(changeRate, 4);
    Serial.println(" cm/s");

    Serial.print("Direction: ");
    Serial.println(getDirection());

    Serial.print("IR: ");
    Serial.println(data.ir);

    Serial.print("Sensor Source: ");
    Serial.println(lastSensorSource);

    Serial.print("Last Change Time: ");
    Serial.println(lastChangeTime);

    Serial.print("Status: ");
    Serial.println(getStatus());

    Serial.println(
      "--------------------------------------"
    );
  }
}
