# Decision 0034: MQTT 生产 Listener — Eclipse Paho + Redis 字段级幂等入库

| 属性 | 值 |
|------|------|
| **状态** | ✅ 已采纳（Day 96 落地） |
| **决策日期** | 2026-09-05 |
| **决策者** | hula0710 + AI 助手 |
| **关联** | Day 94 Paho 学习选型 / Day 95 PLC 模拟器契约 / Day 96 生产接入 / ADR 0033（EMQX）/ ADR 0031（RabbitMQ 推送与幂等模式）/ Day 85 Redis SETNX 先例 |

## 1. 背景

Day 94 已在 `pom.xml` 引入 `org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5`，
并在学习笔记中约定：Day 96 生产接入时升级为 ADR，覆盖版本锁定、Spring Boot 3.5 /
JDK 25 兼容性、连接与幂等参数。

Day 95 已定稿 Topic/Payload 契约：模拟 PLC 与 ESP32 统一发布
`plc/{siteCode}/{deviceCode}/telemetry`（QoS 1，顶层含 `deviceCode/siteCode/ts` 与
`current/windingTemp/pressure/speed` 工程值，`registerSnapshot` 仅作寄存器审计视图）。

本项目已有 RabbitMQ，但它承担应用内部消息总线（报警/巡检/推送）；设备侧接入应使用
MQTT，二者是上下游而非替换关系。后端需要把 MQTT 遥测可靠地变成 `device_data` 记录，
并与 REST 上报共用广播和报警链路。

## 2. 决策

### 2.1 客户端与生命周期

- **沿用 Eclipse Paho mqttv3 1.2.5**（MQTT 3.1.1），不因生产接入切到 mqttv5；
- `MqttProperties` 使用 `mqtt.*` 前缀，默认 `enabled=false`，保证没有 EMQX 的
  dev/test 环境不会建连；容器内由 `compose.yml` 注入启用参数；
- `MqttConfig` 标注 `@Profile("!test")`，test profile 不创建任何 Socket；
- 只有 `mqtt.enabled=true` 时注册 `MqttLifecycle`（内部 `SmartLifecycle`）：
  Spring 启动阶段最后连接 Broker，订阅 QoS 1，关闭阶段优先断开；
- Paho 使用 `MqttDefaultFilePersistence`（可配置目录，默认 `java.io.tmpdir/paho-iah-backend-mqtt`），
  `setAutomaticReconnect(true)`，保留 QoS 未确认消息并自动重连；
- `deliveryComplete` 对 `token.getMessage()` 判空，落实 Day 94 实测坑位；
- `messageArrived` 内所有业务异常由回调层捕获，防止异常传播触发 Paho 断连。

### 2.2 Topic 与字段映射

- 默认订阅 `plc/+/+/telemetry`，QoS 1（与模拟器发布 QoS 对齐）；
- payload 中的 `deviceCode` 是权威设备标识，`DeviceMapper.findByCode` 匹配；
  Topic 第三段与 payload 不一致时只 WARN 不拒绝；
- 只把顶层 4 个工程值字段映射进 `device_data`：
  `current→CURRENT/A`、`windingTemp→TEMPERATURE/°C`、`pressure→PRESSURE/kPa`、
  `speed→SPEED/RPM`；
- `registerSnapshot` 与未知字段不落业务表，避免设备侧增加审计字段影响业务 schema；
- `ts` 使用 ISO-8601 带时区，保留 payload 本地墙钟时间；缺失/非法时回退服务端时间。

### 2.3 Redis 字段级幂等

QoS 1 是“至少一次”投递，MQTT 层可能重复。按每个字段独立做幂等：

```text
SETNX mqtt:{deviceId}:{yyyyMMddHHmmss}:{dataType} = 1  EX 86400
```

- 命中已有键 → 跳过该字段落库/广播/报警；
- Redis 为 null 或 SETNX 异常 → 降级为“不幂等仍写入”（宁愿重复不丢数据）；
- 幂等键覆盖 24h，匹配模拟 PLC 每秒级遥测场景；
- 该模式与 ADR 0031 / AiAlarmAutoCreator 的 Redis SETNX 语义保持同一降级哲学。

### 2.4 与业务链路复用

落库后先 `DeviceDataProducer.publish` 广播（Fanout），再 `AlarmDetector.check`
触发报警并 `AlarmProducer.send + sendDelayCheck`；单字段异常只降级该字段，不阻塞
同一 Payload 的其他字段。

## 3. 备选方案（未采纳）

| 方案 | 未采纳原因 |
|------|-----------|
| Paho mqttv5 | Day 94 已基于 v3 API 完成学习与模拟器；当前无 MQTT5 专属需求，多副本共享订阅可延后评估 |
| `MqttAsyncClient` | 当前单实例/低频率遥测，同步 `MqttClient` 生命周期与 Spring `SmartLifecycle` 更易治理；多副本/高吞吐时再评估异步客户端 |
| 直接扩展现有 REST 上报 | 不改变设备协议，失去 PLC/ESP32 低成本接入与离线缓冲优势 |

## 4. 影响与验证

- `backend/src/main/resources/application.yml` 新增 `mqtt` 默认段；
- `compose.yml` v1.3 → v1.4：backend 注入 `MQTT_ENABLED=true / MQTT_HOST=emqx /
  MQTT_PORT=1883`，`depends_on` 等待 `emqx` healthy；
- `.env.example` 新增非敏感默认变量；
- 新增 `MqttProperties` / `MqttConfig` / `MqttDeviceDataIngestService` /
  `MqttDeviceDataIngestServiceTest`（12 用例）；
- 文档：DAILY_ROADMAP / Day96.md / mqtt-learning-notes §13 / AGENTS /
  Application-Architecture / Infrastructure-Baseline；
- 验证：目标单测 12/12、后端全量回归全绿、`docker compose config` 可解析。

## 5. 风险与后续收口

| 风险 | 缓解 / 后续 |
|------|------------|
| 启动时 EMQX 不可用导致应用失败 | compose 已加 healthcheck 依赖；独立开发环境默认 `mqtt.enabled=false`，不误伤核心服务 |
| 多副本同时订阅同一 Topic 造成重复处理 | 已用 Redis 幂等兜底；若 Day 97+ 多副本高吞吐，可评估 EMQX 共享订阅 `$share` |
| Broker 匿名直连（本地开发） | 沿用 ADR 0033 安全基线；生产部署前必须开启 MQTT 认证与 ACL |
| MQTT 消息洪峰阻塞回调线程 | 当前同步入库低速率可接受；后续可在 Listener 与 Service 之间加有界队列/线程池隔离 |
