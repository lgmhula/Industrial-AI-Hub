# Day 99 — 完整系统联调 + 真实硬件 6 段闭环（Phase 5 Week 16 第 1 天，P1 完成）

> **日期**：2026-09-06
> **阶段**：Phase 5 Week 16 · DAILY_ROADMAP「第 15 周：系统整合 + 运维」第 1 天
> **分支**：`feat/day99-hw-closed-loop`
> **配套**：[ADR 0035](../../docs/decision-log/0035-mqtt-relay-command-downlink.md) + mqtt-learning-notes §15 + learning/esp32/esp32-dht-relay/
> **状态**：P1 软件链路 ✅ GO（359/359）｜P2 真实 ESP32 烧录 + P3 哈气闭环 ⏳ 待硬件侧配合

---

## 一、方案锁定（开工前与 hula0710 对齐）

- 硬件范围：**ESP32#1 + DHT22 + 1 路继电器**（最小可靠闭环；OLED/BH1750/ESP32#2 后续）；
- 触发演示：**哈气 → 湿度 > 90% → OVER_HUMIDITY**（全局规则不改，可靠触发）；
- Payload 画像：固件发**真实字段** `temperature/humidity`（后端扩展字段映射，不伪装 PLC 字段）；
- 继电器语义：**后端下行 MQTT command** 驱动（ESP32 本地 3s 自动复位 + 命令去抖）。

## 二、P1 软件链路（已完成，无需硬件）

### 2.1 后端改动（ADR 0035 落地）

| 文件 | 改动 |
|------|------|
| `service/MqttCommandGateway.java`（新） | MQTT 下行发布端口（ADR 0035） |
| `config/MqttConfig.java` | MqttLifecycle 实现 Gateway + `publish()`；`start()` 连接后运行时注入 ingest（`stop()` 置 null）——打破 Lifecycle→Ingest→Gateway 循环依赖 |
| `service/MqttDeviceDataIngestService.java` | FIELD_MAP 增 `temperature→TEMPERATURE/°C`、`humidity→HUMIDITY/%`；siteCode 贯通（payload 优先/Topic 兜底）；报警联动下行（SENSOR 设备 + `{OVER_HUMIDITY, OVER_TEMP}` 白名单）+ Redis `mqtt:relay:{deviceId}:{alarmType}` 5s 节流；网关 volatile null 降级 |
| 种子 `seed_demo_data.sql` | +`esp32-dht-001`(SENSOR/一车间/PLANT_A)、+`PLC-SIM-001`；50→52 台 |
| `DevSeedDemoDataTest` | 设备断言 50→52 + 新增 2 台存在性断言 |
| `MqttDeviceDataIngestServiceTest` | +4 用例（ESP32 温湿落库+RELAY_ON 下行 / 网关 null 降级 / 5s 节流只发 1 次 / PLC 类型不空发），12→16 |

### 2.2 验证（模拟 ESP32 两轮）

```text
FakeESP32Device 发布 16 条（湿度 55,55,96×4,55,55 ×2 轮，QoS1）
  → device_data 32 行（HUMIDITY×16 + TEMPERATURE×16，device_id=51，min55/max96）
  → 8 条 OVER_HUMIDITY alarm（status=0 未处理）
  → 后端 RELAY_ON 下行 4 次 + 节流命中 4 次（5s 窗）
  → 模拟订阅端实收 2 条/轮：{"cmd":"RELAY_ON","trigger":"OVER_HUMIDITY","ts":…,"value":96.0,"deviceCode":"esp32-dht-001"}
```

- 后端 `./mvnw test` → **359/359 全绿**（355 + 4 新）；前端 build 沿用基线；
- 修复 `scripts/seed-dev.sh` bash 3.2 中文全角括号被误并入变量名导致 `DB: unbound` 的 bug（`$DB` → `${DB}`）。

## 三、P2 交付物（固件已写好，烧录/接线待 hula0710）

| 文件 | 说明 |
|------|------|
| `learning/esp32/esp32-dht-relay/esp32-dht-relay.ino` | DHT22 2s 采样发布（不带 ts，避免无 RTC 导致幂等误判）+ 订阅 command → RELAY_ON 吸合 3s 自动复位 + 命令 3s 去抖 + status retained/LWT |
| `learning/esp32/esp32-dht-relay/README.md` | 工具链安装（Arduino IDE + esp32 包 + 2 库）、接线表、烧录设置（USB CDC On Boot）、排错速查 |
| `learning/java-code/day99/FakeEsp32Device.java` | P1 验证工具（publish 序列 / watch 模式，P3 时 watch 可旁路观察下行） |

## 四、验证环境操作备忘

```bash
# 启动后端（dev + MQTT）：
MQTT_ENABLED=true ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   # backend/
# P3 时 watch 下行：
java -cp "$HOME/.m2/.../org.eclipse.paho.client.mqttv3-1.2.5.jar:/tmp/day99" day99.FakeEsp32Device watch
# 设备 id=51（esp32-dht-001），EMQX 匿名 tcp://192.168.123.120:1883（IP 以 ipconfig getifaddr en0 为准）
```

## 五、待办（P2/P3，需要 hula0710 配合）

1. **P2**：装 Arduino IDE + esp32 包 + 2 库 → 按 README 接线 → 填 `.ino` 顶部 WiFi/Broker IP → 插 USB 烧录（板型/串口不确定时先拍丝印给我）→ Serial 确认 `[wifi]/[mqtt]` 正常；
2. **P3**：我启动后端 → 你对 DHT22 哈气 → 湿度 >90% → 后端 alarm 落库 → 下行 RELAY_ON → 继电器跳变 3s（Serial 与 DB 双向确认）；
3. Day99.md 追记 P2/P3 结果 + AGENTS/ROADMAP 同步 + Git 收口合并。

---

> Day 99 P1 结束。软件链路（模拟设备→落库→报警→下行 command）已验证，真实硬件接入位已就绪，
> 固件与接线文档交付完毕 —— 下一步 P2 需要你装工具链并完成接线烧录。
> 维护者：AI 助手 + hula0710
