/*
 * Jembatan WiFi (TCP) -> UART untuk Robot Kotak
 *
 *   HP (aplikasi) --WiFi TCP--> ESP8266 / ESP32 --UART--> STM32 USART1
 *
 * ESP membuat hotspot sendiri:
 *   SSID     : RobotKotak
 *   Password : robot1234
 *   IP ESP   : 192.168.4.1   (default di aplikasi)
 *   Port TCP : 8888          (default di aplikasi)
 *
 * Kabel (logika 3,3 V, GND harus disambung):
 *   ESP8266 : TX (GPIO1) -> STM32 PA10 (RX1) | RX (GPIO3) <- STM32 PA9 (TX1)
 *   ESP32   : GPIO17 (TX2) -> STM32 PA10     | GPIO16 (RX2) <- STM32 PA9
 *
 * UART 115200 baud, 8N1 -> samakan dengan USART1 di CubeMX.
 * Jangan pakai Serial.print untuk debug di ESP8266 (Serial = jalur ke STM32).
 */
#if defined(ESP32)
  #include <WiFi.h>
  #define STM_SERIAL Serial2
#else
  #include <ESP8266WiFi.h>
  #define STM_SERIAL Serial
#endif

const char *AP_SSID = "RobotKotak";
const char *AP_PASS = "robot1234";      // minimal 8 karakter
const uint16_t TCP_PORT = 8888;
const uint32_t UART_BAUD = 115200;

const char *STOP_FRAME = "0,0,STOP,NONE,0,NONE,NONE\n";

WiFiServer server(TCP_PORT);
WiFiClient client;

void setup() {
#if defined(ESP32)
  STM_SERIAL.begin(UART_BAUD, SERIAL_8N1, 16, 17);  // RX2 = 16, TX2 = 17
#else
  STM_SERIAL.begin(UART_BAUD);
#endif
  WiFi.mode(WIFI_AP);
  WiFi.softAP(AP_SSID, AP_PASS);
  server.begin();
  server.setNoDelay(true);
}

void loop() {
  // Klien baru menggantikan klien lama
  if (server.hasClient()) {
    if (client) client.stop();
    client = server.available();
    client.setNoDelay(true);
  }

  if (client && client.connected()) {
    // TCP -> UART (apa adanya, baris diakhiri '\n')
    while (client.available()) {
      STM_SERIAL.write((uint8_t)client.read());
    }
  } else if (client) {
    // Koneksi putus -> suruh robot berhenti
    client.stop();
    STM_SERIAL.print(STOP_FRAME);
  }
}
