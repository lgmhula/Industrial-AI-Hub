# Decision 0035: MQTT 下行 command — 报警联动继电器执行器（Day 99 硬件闭环）

| 属性 | 值 |
|------|------|
| **状态** | ✅ 已采纳（Day 99 落地） |
| **决策日期** | 2026-09-06 |
| **决策者** | hula0710 + AI 助手 |
| **关联** | Day 95 Topic/Payload 契约（`command` 下行预留）/ Day 96 Listener 接入（ADR 0034）/ Day 98 Week 15 复盘（继电器下行 = Day 99 待办）/ comprehensive-review-2026-08-22 第八章（已购 1 路继电器 + ESP32-S3 + DHT22）/ ADR 0031（Redis SETNX 幂等降级哲学） |

## 1. 背景

Day 95 在 Topic 命名空间里预留了下行通道：

```text
plc/{siteCode}/{deviceCode}/command    # 下行预留，Day 95+ 再决定是否实现
```

Day 96 的后端 Listener 只有**订阅遥测**能力（Paho 单连接只 subscribe），后端不能向
设备发任何命令。Week 15 复盘（Day 98）把「真实硬件 6 段闭环：手捂/哈气 DHT22 →
超限 → alarm 落库 → SSE/通知 → 继电器跳变」列为 Day 99 完整系统联调的核心目标，
其中「继电器跳变由谁触发」存在三个候选：ESP32 本地阈值自触发 / 后端下行 MQTT command /
前端手动控制。最终选定**后端下行 MQTT command**：继电器作为系统级执行器，必须由
平台（后端报警联动）驱动，才能验证「数据 → 决策 → 执行」的完整业务闭环，而不是
设备端自说自话。

## 2. 决策

### 2.1 契约：command Topic 与 Payload

```text
Topic:   plc/{siteCode}/{deviceCode}/command     QoS 1（与遥测一致）
Payload: {
           "cmd": "RELAY_ON",                     // 命令字，当前仅 RELAY_ON
           "trigger": "OVER_HUMIDITY",            // 触发报警类型
           "ts": "2026-09-06T10:00:00",           // 报警对应数据采样时间（墙钟）
           "value": 95.5,                          // 触发时的工程值
           "deviceCode": "esp32-dht-001"          // 目标设备编码
         }
```

- `siteCode` 取自 payload（契约字段），缺失时回退遥测 Topic 第二段；
- 设备固件订阅自己的 `command` Topic，收到 `RELAY_ON` 后吸合继电器并在**本地 3s 后
  自动复位**（无 RELAY_OFF 上行握手，避免闭环依赖设备在线状态查询）；
- 固件侧对同一命令做 3s 去抖（连续重复 RELAY_ON 不抖动）。

### 2.2 触发策略（报警联动）

- 仅**传感器执行器设备**（`device.device_type = SENSOR`）在命中可执行报警时下发，
  避免向无执行器的 PLC 模拟器/普通传感器空发命令；
- 报警类型白名单当前 = `{OVER_HUMIDITY, OVER_TEMP}`（湿度哈气演示 + 温度热源演示）；
- 触发点在 MQTT 入站链路 `MqttDeviceDataIngestService`：字段落库 → AlarmDetector
  产生 AlarmVO → 除 RabbitMQ 报警消息外，追加下行发布（REST 上报路径不改，保持
  「下行仅对 MQTT 实时链路生效」的窄边界）。

### 2.3 重复抑制（双保险）

| 层 | 机制 | 说明 |
|----|------|------|
| 后端 | Redis SETNX `mqtt:relay:{deviceId}:{alarmType}` TTL 5s | 持续超限的每 2s 样本不重复下发；Redis null/异常降级为放行（固件去抖兜底） |
| 固件 | 收到 RELAY_ON 后 3s 窗口忽略重复命令 + 3s 自动复位 | 即使后端降级放行，继电器也不抖动 |

### 2.4 注入方式：运行时注入打破构造期循环依赖

`MqttLifecycle`（建连）→ `MqttDeviceDataIngestService`（业务）→ 下行端口 三者若都用
构造器注入会成环（Lifecycle 需要 Ingest，Ingest 需要 Gateway，Gateway 又来自
Lifecycle）。因此：

- 新增端口接口 `service.MqttCommandGateway`（`publish(topic, payload, qos)`）；
- `MqttLifecycle` 实现该接口，并在 `start()` 建连/订阅成功后调用
  `ingestService.setCommandGateway(this)`（`volatile` 字段），`stop()` 置 null；
- `MqttDeviceDataIngestService` 内网关为 null（MQTT 未启用/未连接/已停止）时下行
  静默降级，不影响遥测落库与 RabbitMQ 报警链路；
- test profile 无 MQTT Socket，单测通过显式 `setCommandGateway(mock)` 覆盖下行路径。

## 3. 备选方案（未采纳）

| 方案 | 未采纳原因 |
|------|-----------|
| ESP32 本地阈值自触发继电器 | 执行器逻辑逃出平台治理：报警记录、节流、审计都在设备端，后端看不到执行证据，无法验证「平台→执行器」闭环 |
| 复用 RabbitMQ 报警消息触发下行（另起 Consumer） | 为一条下行命令引入跨总线（MQTT 进 → RabbitMQ 中转 → MQTT 出）的额外延迟与失败面；直接在同一入站事务语义内发布最简 |
| 前端/人工手动控制继电器 | 不是自动化闭环，仅作为后续 UI 控制扩展点 |
| 构造器注入下行端口 + 别名 Bean | 造成 Lifecycle → Ingest → Gateway → Lifecycle 循环依赖，需要 ObjectProvider 或 setter，反而复杂 |

## 4. 影响与验证

- 后端改动（Day 99）：
  - `MqttDeviceDataIngestService`：FIELD_MAP 增 `temperature→TEMPERATURE`、
    `humidity→HUMIDITY`（ESP32/DHT22 画像真实字段）；siteCode 贯通；报警联动下行 +
    Redis 5s 节流；网关 setter（volatile null 安全）；
  - 新增 `service.MqttCommandGateway` 端口 + `MqttConfig.MqttLifecycle.publish()` +
    `start()/stop()` 注入/清空网关；
  - 单测新增 4 个（ESP32 高温湿落库 + RELAY_ON 下行、网关 null 降级、5s 节流只发一次、
    PLC 类型设备不空发），`MqttDeviceDataIngestServiceTest` 12 → 16；
- 设备固件（P2）：Arduino .ino 订阅 `plc/PLANT_A/esp32-dht-001/command`，RELAY_ON →
  继电器 3s 吸合后复位 + 3s 命令去抖；
- 种子：新增 `esp32-dht-001`(SENSOR/一车间/PLANT_A) 与 `PLC-SIM-001`；
- 验证：单测 16/16 → 全量回归 → 软件链路（模拟 ESP32 发布湿度 95 → device_data +
  OVER_HUMIDITY alarm + command 被独立 subscriber 收到）→ 真实硬件 P3 闭环
  （哈气 → alarm → 继电器跳变，人工确认）；
- 文档：DAILY_ROADMAP / Day99.md / mqtt-learning-notes §15 / AGENTS /
  ADR 0035（本文件）。

## 5. 风险与后续收口

| 风险 | 缓解 / 后续 |
|------|------------|
| ESP32 掉线时命令无人消费 | QoS 1 语义仅到 Broker；继电器闭环为演示/边缘联动场景，真实产线需设备侧在线确认（`status` retained + 遗嘱）与命令 ACK Topic |
| 报警每样本落一条（AlarmDetector 无去抖） | 维持现状（与 PLC 模拟一致）；若需抑制可在规则引擎层加「同类型未恢复不重复告警」 |
| `SENSOR` 类型粗粒度：误伤普通传感器（无执行器） | 白名单报警类型 + 按 deviceCode 精确订阅已足够窄；后续可引入 device_profile 物模型（comprehensive-review 阶段四） |
| 命令无鉴权（EMQX 匿名直连） | 沿用 ADR 0033 安全基线：生产部署前开启 MQTT 认证 + ACL，command Topic 仅允许 backend 发布 |

---

> 维护者：AI 助手 + hula0710
