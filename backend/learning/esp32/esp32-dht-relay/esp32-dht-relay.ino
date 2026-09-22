/*
 * Day 99 — ESP32-S3 #1 + DHT22 + 1 路继电器（ADR 0035 command 契约）
 *
 * 功能：
 *   1. WiFi 连接 → MQTT 连接 EMQX（匿名，1883）
 *   2. 每 2s 读取 DHT22 并发布 telemetry（QoS 1，非 retained）：
 *        plc/PLANT_A/esp32-dht-001/telemetry
 *      Payload: {"deviceCode":"esp32-dht-001","siteCode":"PLANT_A",
 *                "temperature":28.5,"humidity":62.3,"status":"running"}
 *      ※ 故意不带 ts：ESP32 无 RTC/NTP 时 ts 不可靠，后端会在 ts 缺失时用服务端时间，
 *        避免“同秒同键”造成幂等误判（服务端每 2s 采样的秒级键不冲突）。
 *   3. 订阅下行 command（QoS 1）：
 *        plc/PLANT_A/esp32-dht-001/command
 *      收到 {"cmd":"RELAY_ON",...} → 继电器吸合 3s 后自动复位；
 *      3s 窗口内的重复 RELAY_ON 忽略（去抖，配合后端 Redis 5s 节流双保险）。
 *   4. status 主题发布 retained（online/offline），断线由 LWT 遗嘱发布 offline。
 *
 * 依赖库（Arduino IDE 库管理器安装）：
 *   - PubSubClient (Nick O'Leary)
 *   - DHT sensor library (Adafruit)  +  Adafruit Unified Sensor
 * 开发板：ESP32S3 Dev Module（板型/烧录设置见同目录 README.md）
 *
 * @author AI 助手 + hula0710
 * @date 2026-09-06
 */

#include <WiFi.h>
#include <PubSubClient.h>
#include <DHT.h>

// ───────────────────────── 用户必填（烧录前修改） ─────────────────────────
const char* WIFI_SSID     = "YOUR_WIFI_SSID";          // TODO 你的 Wi-Fi
const char* WIFI_PASSWORD = "YOUR_WIFI_PASSWORD";      // TODO 你的 Wi-Fi 密码
const char* MQTT_HOST     = "192.168.123.120";         // TODO 跑 EMQX 的电脑局域网 IP
const uint16_t MQTT_PORT  = 1883;
// ───────────────────────────────────────────────────────────────────────────

// 设备标识（与 DB 种子/后端一致）
const char* SITE_CODE   = "PLANT_A";
const char* DEVICE_CODE = "esp32-dht-001";

// Topic 契约（ADR 0033/0034/0035）
const char* TELEMETRY_TOPIC = "plc/PLANT_A/esp32-dht-001/telemetry";
const char* STATUS_TOPIC    = "plc/PLANT_A/esp32-dht-001/status";
const char* COMMAND_TOPIC   = "plc/PLANT_A/esp32-dht-001/command";

// 引脚（接线见 README.md；避免 strapping pin 0/3/45/46）
const uint8_t DHT_PIN      = 4;    // DHT22 DATA
const uint8_t RELAY_PIN    = 5;    // 继电器 IN
const bool    RELAY_ON_LEVEL = LOW;  // true=高电平触发, false=低电平触发(常见光耦低触发)

// 采样与去抖
const unsigned long TELEMETRY_INTERVAL_MS = 2000;
const unsigned long RELAY_HOLD_MS         = 3000;  // 吸合保持后自动复位
const unsigned long RELAY_DEBOUNCE_MS     = 3000;  // 命令去抖窗口

const char* MQTT_CLIENT_ID = "esp32-dht-001";

WiFiClient net;
PubSubClient mqtt(net);
DHT dht(DHT_PIN, DHT22);

unsigned long lastTelemetryMs = 0;
unsigned long relayOnUntilMs  = 0;   // 自动复位时刻（0=无任务）
unsigned long relayIgnoreCmdUntilMs = 0; // 命令去抖截止

// ───────────────────────── 辅助函数 ─────────────────────────

void relaySet(bool on) {
  digitalWrite(RELAY_PIN, on ? RELAY_ON_LEVEL : !RELAY_ON_LEVEL);
  Serial.print("[relay] ");
  Serial.println(on ? "ON（吸合）" : "OFF（复位）");
}

void handleCommand(char* topic, byte* payload, unsigned int length) {
  String msg;
  for (unsigned int i = 0; i < length; i++) {
    msg += (char)payload[i];
  }
  Serial.print("[cmd] topic=");
  Serial.print(topic);
  Serial.print(" payload=");
  Serial.println(msg);

  if (msg.indexOf("\"cmd\":\"RELAY_ON\"") >= 0) {
    unsigned long now = millis();
    if (now < relayIgnoreCmdUntilMs) {
      Serial.println("[relay] 去抖窗口内忽略重复 RELAY_ON");
      return;
    }
    relayOnUntilMs = now + RELAY_HOLD_MS;
    relayIgnoreCmdUntilMs = now + RELAY_DEBOUNCE_MS;
    relaySet(true);
  } else {
    Serial.println("[cmd] 未识别命令，忽略");
  }
}

bool mqttConnect() {
  // 重试直到连上
  for (int i = 0; i < 20 && !mqtt.connected(); i++) {
    Serial.print("[mqtt] 尝试连接 EMQX …");
    // 遗嘱：非正常断线时 Broker 代发 offline retained
    String offline = "{\"deviceCode\":\"esp32-dht-001\",\"siteCode\":\"PLANT_A\","
                     "\"online\":false,\"status\":\"offline\"}";
    if (mqtt.connect(MQTT_CLIENT_ID, STATUS_TOPIC, 1, true,
                     offline.c_str())) {
      Serial.println(" 已连接");
      mqtt.subscribe(COMMAND_TOPIC, 1);
      String online = "{\"deviceCode\":\"esp32-dht-001\",\"siteCode\":\"PLANT_A\","
                      "\"online\":true,\"status\":\"running\"}";
      mqtt.publish(STATUS_TOPIC, online.c_str(), true);
      Serial.print("[mqtt] 已订阅 ");
      Serial.println(COMMAND_TOPIC);
      return true;
    }
    Serial.println(" 失败，1s 后重试");
    delay(1000);
  }
  return mqtt.connected();
}

void publishTelemetry() {
  float h = dht.readHumidity();
  float t = dht.readTemperature();
  if (isnan(h) || isnan(t)) {
    Serial.println("[dht] 读取失败（采样间隔不足或接线错误），跳过本次发布");
    return;
  }
  char tempStr[8];
  char humStr[8];
  dtostrf(t, 4, 1, tempStr);
  dtostrf(h, 4, 1, humStr);

  char payload[200];
  snprintf(payload, sizeof(payload),
           "{\"deviceCode\":\"esp32-dht-001\",\"siteCode\":\"PLANT_A\","
           "\"temperature\":%s,\"humidity\":%s,\"status\":\"running\"}",
           tempStr, humStr);

  bool ok = mqtt.publish(TELEMETRY_TOPIC, payload);
  Serial.print("[telemetry] ");
  Serial.print(ok ? "OK" : "FAIL");
  Serial.print("  temp=");
  Serial.print(tempStr);
  Serial.print("°C  hum=");
  Serial.print(humStr);
  Serial.println("%");
}

// ───────────────────────── Arduino 入口 ─────────────────────────

void setup() {
  Serial.begin(115200);
  delay(300);
  Serial.println();
  Serial.println("[boot] ESP32-S3 Day99 DHT22+Relay 固件启动");

  pinMode(RELAY_PIN, OUTPUT);
  relaySet(false);
  dht.begin();

  // WiFi
  Serial.print("[wifi] 连接 ");
  Serial.println(WIFI_SSID);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  int tries = 0;
  while (WiFi.status() != WL_CONNECTED && tries < 40) {
    delay(500);
    Serial.print(".");
    tries++;
  }
  if (WiFi.status() == WL_CONNECTED) {
    Serial.print("\n[wifi] 已连接，IP=");
    Serial.println(WiFi.localIP());
  } else {
    Serial.println("\n[wifi] 连接失败，请检查 SSID/密码");
  }

  mqtt.setServer(MQTT_HOST, MQTT_PORT);
  mqtt.setCallback(handleCommand);
  mqttConnect();
  lastTelemetryMs = millis();
}

void loop() {
  if (!mqtt.connected()) {
    mqttConnect();
  }
  mqtt.loop();

  unsigned long now = millis();
  // 继电器到点自动复位
  if (relayOnUntilMs != 0 && now >= relayOnUntilMs) {
    relayOnUntilMs = 0;
    relaySet(false);
  }

  if (now - lastTelemetryMs >= TELEMETRY_INTERVAL_MS) {
    lastTelemetryMs = now;
    publishTelemetry();
  }
}
