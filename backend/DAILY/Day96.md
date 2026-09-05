# Day 96 — MQTT → 项目：Paho Listener + device_data 入库 + Redis 幂等（Phase 5 Week 15 第 5 天）

> **日期**：2026-09-05
> **阶段**：Phase 5 PLC + MQTT + 完整系统上线 · Week 15 第 5 天
> **分支**：`feat/day96-mqtt-ingest`
> **配套文档**：[ADR 0034](../../docs/decision-log/0034-mqtt-listener-paho-redis-idempotency.md) / [mqtt-learning-notes.md](../../docs/notes/mqtt-learning-notes.md) §13
> **验收结果**：✅ **GO**（新增 12 个单测通过 + 后端全量回归全绿 + 配置文档同步）

---

## 一、今日产出

| 模块 | 文件 | 说明 |
|------|------|------|
| 配置属性 | `backend/src/main/java/dev/reboot/config/MqttProperties.java` | `mqtt.*` 前缀：默认关闭、host/port/clientId/topicFilter/QoS/cleanSession/keepAlive/connectionTimeout/persistenceDir |
| 生命周期 | `backend/src/main/java/dev/reboot/config/MqttConfig.java` | `@Profile("!test")` + `@ConditionalOnProperty(mqtt.enabled=true)`；SmartLifecycle 启动建连订阅、停止清理；Paho 回调三层防护 + 自动重连 |
| 入库服务 | `backend/src/main/java/dev/reboot/service/MqttDeviceDataIngestService.java` | 按 Day 95 契约解析顶层工程值 → device 匹配 → `device_data` 字段落库 → Fanout 广播 → AlarmDetector 报警链路 |
| 单元测试 | `backend/src/test/java/dev/reboot/service/MqttDeviceDataIngestServiceTest.java` | 12 用例：完整 Payload / Redis 重复 / Redis 异常 / 无 Redis / 设备不存在 / 非法 ts / 时区 ts / 非法 JSON / 坏字段 / AlarmProducer null / 报警异常 / Mapper 异常 |
| 应用配置 | `backend/src/main/resources/application.yml` | 新增 `mqtt` 段，环境变量均有非敏感默认值，默认关闭 |
| 编排 | `compose.yml` | v1.3 → v1.4；backend 容器注入 `MQTT_ENABLED=true` + `MQTT_HOST=emqx`，`depends_on` 等待 EMQX healthy |
| 环境模板 | `.env.example` | 新增 `MQTT_ENABLED / MQTT_HOST / MQTT_PORT` 非敏感默认变量 |
| ADR | `docs/decision-log/0034-mqtt-listener-paho-redis-idempotency.md` | Paho 正式生产接入 + Redis 字段级幂等决策 |
| 笔记 | `docs/notes/mqtt-learning-notes.md` §13 | 生产 Listener 实现、幂等/降级语义、联调说明 |
| 日日志 | `backend/DAILY/Day96.md` | 本文件 |

---

## 二、实现要点

### 2.1 入站链路

```text
PLC / ESP32 / Java Simulator
  → MQTT publish plc/{siteCode}/{deviceCode}/telemetry (QoS 1)
  → EMQX
  → MqttConfig.MqttLifecycle (Paho subscribe QoS 1)
  → MqttDeviceDataIngestService.ingest(topic, payload)
  → deviceCode 匹配 DeviceMapper.findByCode
  → 每个顶层字段 Redis SETNX 幂等（可选）
  → device_data insert
  → DeviceDataProducer Fanout 广播
  → AlarmDetector 规则检测 → alarm 落库 + AlarmProducer 发送/延迟升级
```

### 2.2 字段契约（Day 95 §2.4）

| Payload 顶层字段 | `device_data.data_type` | unit |
|-----------------|------------------------|------|
| `current` | `CURRENT` | A |
| `windingTemp` | `TEMPERATURE` | °C |
| `pressure` | `PRESSURE` | kPa |
| `speed` | `SPEED` | RPM |

`registerSnapshot` 数组不进入业务表，仅作寄存器审计视图；后端不区分 Java 模拟器与真实 ESP32。

### 2.3 Redis 字段级幂等

```text
key = mqtt:{deviceId}:{yyyyMMddHHmmss}:{dataType}
value = 1
TTL = 24h
```

QoS 1 的重复投递按“字段 + payload 时间戳”去重；Redis 为 null 或异常时降级为不幂等仍写入（宁愿重复不丢数据）。

### 2.4 故障降级

| 故障点 | 行为 |
|--------|------|
| 非法 JSON / 设备不存在 | 跳过整条，返回 false 不抛异常 |
| ts 缺失或非法 | 回退服务端 `LocalDateTime.now()` |
| 单字段非数值/未知字段 | WARN 跳过，不阻塞同 Payload 后续字段 |
| Redis SETNX 异常 | 放弃幂等，仍写库/广播/报警 |
| 广播 RabbitMQ 异常 | 数据已落库，WARN 后继续报警链路 |
| AlarmDetector / AlarmProducer 异常 | 单字段报警链路降级，不阻塞后续字段 |
| MqttCallback 内任何 RuntimeException | 回调层捕获，避免异常传播导致 Paho 断连 |

---

## 三、配置说明

```bash
# application.yml（默认关闭，保证无 EMQX 环境不建连）
mqtt.enabled=${MQTT_ENABLED:false}
mqtt.host=${MQTT_HOST:127.0.0.1}
mqtt.port=${MQTT_PORT:1883}
mqtt.topic-filter=plc/+/+/telemetry
mqtt.qos=1
```

`compose.yml` v1.4 中 backend 容器使用：

```yaml
MQTT_ENABLED: "true"
MQTT_HOST: emqx
MQTT_PORT: "1883"
depends_on:
  emqx:
    condition: service_healthy
```

---

## 四、验证

- `./mvnw -Dtest=MqttDeviceDataIngestServiceTest test` → 12 tests, 0 failures, 0 errors, BUILD SUCCESS；
- `./mvnw test` → 全量回归全绿（见最终结果）；
- 代码仅做入站侧生产接入，不改既有 REST `DeviceDataService.report` 语义；`device_data` 落库后与 REST 上报共用同一套广播/报警链路。

> Day 96 结束。明天 Day 97：模拟多设备并发数据上报 + 压力测试，验证 Listener 在多 Topic/多设备下的吞吐与幂等稳定性。
