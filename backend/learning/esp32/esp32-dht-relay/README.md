# ESP32-S3 #1 + DHT22 + 继电器 — 烧录/接线指南（Day 99 P2）

> 固件：`esp32-dht-relay.ino`（契约见 ADR 0033/0034/0035，先读 `docs/notes/mqtt-learning-notes.md` §15）
> 目标：真实硬件接入 EMQX 完成 6 段闭环 —— DHT22 采集 → MQTT 上报 → 后端落库 →
> 报警 → 下行 RELAY_ON → 继电器跳变（P3 演示）。

## 1. 需要的硬件（已购清单，见 comprehensive-review 第八章 §8.1）

| 硬件 | 用途 |
|------|------|
| ESP32-S3-N16R8 ×1 | 主控（Day 99 用 #1） |
| DHT22 ×1 | 温湿度采集（哈气 → 湿度 >90% 触发演示） |
| 1 路继电器模块 ×1 | 执行器（后端 RELAY_ON → 吸合 3s 自动复位） |
| USB Type-C 线 ×1 | 供电 + 烧录 |
| 杜邦线若干 / 面包板 | Arduino 学习套件里有 |

## 2. 工具链（未装 → 一步步来，约 15 分钟）

**推荐 Arduino IDE 2.x**（最简单）：

1. 官网 https://www.arduino.cc/en/software 下载 **Arduino IDE 2.x** 并安装；
2. 打开 IDE：`File → Preferences → Additional boards manager URLs` 填入：
   ```
   https://espressif.github.io/arduino-esp32/package_esp32_index.json
   ```
3. `Tools → Board → Boards Manager`，搜索 **esp32**，安装 **esp32 by Espressif Systems**（3.x）；
4. `Tools → Board` 选 **ESP32S3 Dev Module**；
5. 库管理器（`Sketch → Include Library → Manage Libraries`）安装：
   - **PubSubClient**（Nick O'Leary）
   - **DHT sensor library**（Adafruit）+ 自动依赖 **Adafruit Unified Sensor**

> 备选 PlatformIO / ESP-IDF 亦可，可再问我给对应工程。

## 3. 接线

| ESP32-S3 引脚 | 接到 | 说明 |
|:---:|------|------|
| 3V3 | DHT22 VCC（+/V） | DHT22 工作电压 3.3~5.5V，用 3.3V 即可 |
| GND | DHT22 GND（-） | 共地 |
| **GPIO4** | DHT22 DATA（S/out） | 若 DHT22 是 4 引脚裸件，DATA 与 3V3 之间加 **10kΩ 上拉**；模块版通常自带 |
| 5V | 继电器模块 VCC（JD-VCC/VCC） | 继电器线圈用 5V 供电更稳 |
| GND | 继电器模块 GND | 共地 |
| **GPIO5** | 继电器模块 IN（信号） | 低电平触发常见；烧录后看 Serial 日志与模块指示灯确认 |

继电器**输出侧**：COM 与 NO 串入演示负载（如 220V 小灯泡、12V 蜂鸣器/风扇 + 独立电源，或用
万用表电阻档观察 COM-NO 通断即可，最安全）。手边没有合适负载时，**用万用表（9205）蜂鸣档
测 COM-NO 通断**即可验证跳变，不必接强电。

⚠️ **ESP32-S3 引脚提醒**：避开 GPIO0/3/45/46（strapping）。若你的模块接线不同（如 GPIO4 被占用），
改 `.ino` 顶部 `DHT_PIN` / `RELAY_PIN` 常量。

## 4. 烧录前修改 `.ino` 顶部 3 处

```cpp
const char* WIFI_SSID     = "你的WiFi名";
const char* WIFI_PASSWORD = "你的WiFi密码";
const char* MQTT_HOST     = "192.168.123.120";   // 跑 EMQX 的电脑局域网 IP（P1 验证时为本机）
```

> ⚠️ ESP32 需要能**访问到该 IP**：Mac 当前是**有线网** 192.168.123.120，ESP32 必须连**同一路由器**
> 的 Wi-Fi（同 192.168.123.x 网段）。若网络不通，P2 时告诉我，可改用端口转发/热点方案。

## 5. 烧录设置（板型相关的两个关键项）

USB 插入电脑后 `Tools → Port` 选择新出现的串口。

| 你的板子 | 设置 | 说明 |
|---------|------|------|
| **带板载 USB-UART**（丝印有 CP2102/CH340 之类芯片）| 默认即可 | 串口直接可用 |
| **原生 USB（无串口芯片）**（很多 N16R8 通用板只有 Type-C + 原生 USB）| `Tools → USB CDC On Boot: Enabled` | 否则 Upload/Serial 都连不上 |

> 不确定是哪类？插上电脑后把 **Tools→Port 里的串口名** 或 **板子丝印照片**发我，我来判断。
> 上传成功后打开 `Tools → Serial Monitor`（波特率 **115200**）能看到 `[wifi]` / `[mqtt]` 日志。

## 6. 验证顺序（P2 → P3）

1. 烧录 → Serial Monitor 看到 `[wifi] 已连接` 与 `[mqtt] 已订阅 .../command`；
2. 我先启动后端（一条命令），你在 Serial Monitor 看到每 2s 一条 `[telemetry] OK`；
3. **P3 演示**：对 DHT22 哈气数秒 → 湿度 >90% → 后端 alarm 落库 →
   后端下行 RELAY_ON → 继电器吸合 3s 自动复位（Serial 打 `[cmd]` + `[relay]`）。
4. 可选观察：EMQX Dashboard `http://192.168.123.120:18083`（admin/`.env` 的密码）看
   esp32-dht-001 会话与消息。

> 排错速查：WiFi 连不上→SSID/密码/信道；MQTT 连不上→电脑 IP 变了（`ipconfig getifaddr en0` 重查）；
> DHT 读取 NaN→采样间隔或接线（3.3V 上拉）；继电器不动→IN 触发电平反（改 `RELAY_ON_LEVEL`）。
