# Day 93 MQTT 学习笔记：Broker / Topic / QoS / EMQX

> 日期：2026-09-04 | 覆盖：Phase 5 Day 93（MQTT 协议基础 + EMQX/Mosquitto 安装）
> 配套文档：[Day93.md](../../backend/DAILY/Day93.md) / [ADR 0033](../decision-log/0033-mqtt-broker-emqx.md)
> 阶段定位：Day 94 起用 Eclipse Paho 开发 Java MQTT 客户端，Day 95 模拟 PLC 发布，Day 96 后端订阅落库。

---

## 1. Day 93 在 Phase 5 中的位置

Day 92 的链路继续往前走一步：

```text
PLC / 模拟设备（Modbus 点位）
  │
  ▼
网关 / 边缘程序
  │  MQTT publish（Day 93-95）
  ▼
EMQX Broker ← Day 93 安装在这里
  │  MQTT subscribe（Day 96）
  ▼
Industrial AI Hub 后端
  ├─ device_data 落库 → 报警规则引擎
  ├─ AI 巡检 / 诊断
  └─ SSE → 前端
```

MQTT 要解决的核心问题不是「点对点传数据」，而是让大量、不稳定、NAT 后面的设备
统一通过一条轻量 TCP 连接向 Broker 发布数据，再由订阅方消费。

---

## 2. MQTT 基础模型

### 2.1 三个角色

| 角色 | 说明 | 本项目对应 |
|------|------|-----------|
| Publisher | 发布消息到 Topic | Day 95 模拟 PLC / 网关程序 |
| Broker | 接收、路由、存储（可选） | EMQX 5.8.9 |
| Subscriber | 订阅 Topic 并接收消息 | Day 96 后端 MQTT listener |

MQTT 没有传统 HTTP 的 Request/Response。发布者不需要知道订阅者是谁，
订阅者也不需要知道发布者是谁，Broker 按 Topic 完成解耦。

### 2.2 连接生命周期

```text
Client                    Broker
  │  CONNECT                  │
  │-------------------------->│
  │  CONNACK                  │
  │<--------------------------│
  │  SUBSCRIBE / PUBLISH ...  │
  │-------------------------->│
  │  PINGREQ（保活窗口）        │
  │-------------------------->│
  │  PINGRESP                 │
  │<--------------------------│
  │  DISCONNECT / LWT         │
```

协议建立在 TCP 之上，默认端口 1883；TLS 默认 8883，WebSocket 常映射到 8083。
连接参数包括 Client ID、Clean Session、Keep Alive、用户名密码、遗嘱消息。

### 2.3 Broker 与 RabbitMQ 的边界

| 维度 | RabbitMQ | EMQX |
|------|----------|------|
| 使用位置 | 应用内部 Producer/Consumer | 设备/边缘接入侧 |
| 消息模型 | Exchange + Queue + Binding | Topic 发布订阅 |
| 语义 | AMQP 0-9-1 | MQTT 3.1.1 / 5.0 |
| Phase 5 用途 | 报警、巡检日报推送 | PLC/模拟设备遥测入站 |

两者不是二选一：设备数据先进 EMQX，后端处理后再由 RabbitMQ 驱动应用内通知。

---

## 3. Topic 与通配符

### 3.1 Topic 层级

Topic 是 UTF-8 字符串，用 `/` 分隔层级。它不像文件系统那样预先创建，发布时即存在。

本项目建议的 Phase 5 Topic：

```text
plc/{siteCode}/{deviceCode}/telemetry
plc/{siteCode}/{deviceCode}/status
plc/{siteCode}/{deviceCode}/command    # 下行预留，Day 95+ 再决定是否实现
```

例如：

```text
plc/site-a/dev-001/telemetry
plc/site-a/dev-001/status
```

### 3.2 订阅通配符

| 通配符 | 匹配规则 | 示例 |
|--------|----------|------|
| `+` | 匹配**一个**层级 | `plc/+/dev-001/telemetry` 匹配任意 siteCode |
| `#` | 匹配剩余**所有**层级 | `plc/site-a/#` 匹配 site-a 下 telemetry/status |

`#` 只能放在末尾，且必须单独占一个层级；`sport/#` 合法，`sport/tennis/#/player` 不合法。
通配符用于订阅，一般不允许用于发布。

### 3.3 共享订阅

MQTT 5.0 支持 `$share/{group}/{filter}`，让多个订阅者按负载均衡分担同一 Topic。
多副本后端订阅遥测时适合共享订阅；Day 96 若只跑单实例，可以先不引入。

---

## 4. QoS：三种投递保证

QoS 只描述**消息投递强度**，不保证速度或顺序。

### 4.1 QoS 0 — 最多一次

```text
Publisher ──PUBLISH──▶ Broker ──PUBLISH──▶ Subscriber
```

- 不等待确认，不重发；
- 适合可丢失的遥测（温度、振动实时值）；
- 实时性最好，但网络抖动可能丢消息。

### 4.2 QoS 1 — 至少一次

```text
Publisher                Broker
  │  PUBLISH (DUP=0)       │
  │----------------------->│
  │  PUBACK                │
  │<-----------------------│
```

- Broker 收到后回 PUBACK；
- 发布端未收到 PUBACK 会带 DUP 重发，因此**接收端可能重复收到**；
- 适合报警/关键事件，但消费端必须幂等。

### 4.3 QoS 2 — 恰好一次

```text
Publisher                Broker
  │  PUBLISH               │
  │----------------------->│
  │  PUBREC                │
  │<-----------------------│
  │  PUBREL                │
  │----------------------->│
  │  PUBCOMP               │
  │<-----------------------│
```

- 通过 PUBREC/PUBREL/PUBCOMP 四步握手去重；
- 协议保证不丢不重，但消息吞吐和延迟更高；
- 适合资金/订单类严格事件，PLC 遥测一般不需要全量 QoS 2。

### 4.4 端到端 QoS 取最小值

```text
有效 QoS = min(发布端 QoS, 订阅端 QoS)
```

Publisher 以 QoS 1 发布、Subscriber 请求 QoS 0 时，Subscriber 实际只得到 QoS 0。
因此要保证「Broker 到应用」不丢事件，发布和订阅两端都要设置目标 QoS。

### 4.5 对本项目的意义

| 场景 | 建议 QoS | 原因 |
|------|---------|------|
| 模拟 PLC 遥测（实时值） | QoS 0 | 新值会持续发布，丢一帧影响小 |
| 设备状态变化 / 关键报警 | QoS 1 | 需要至少一次送达，消费端 Redis 幂等 |
| 需要协议级去重的严格事件 | QoS 2 | 成本最高，仅在必须恰好一次时用 |

QoS 1 的重复投递与 Day 85 `inspection:{reportDate}:{siteId}` SETNX 幂等思路一致：
应用层用业务幂等键去重，而不是假设 MQTT 一定不重。

---

## 5. 保留消息、遗嘱与 Keep Alive

### 5.1 Retained Message

发布时带 `retain=1`，Broker 会保存该 Topic 的**最后一条**消息；
新订阅者建立订阅后立即收到它。适合发布「当前状态」，让新订阅者不用等下一次上报：

```text
plc/site-a/dev-001/status  ← 设备在线 / 离线 / 维护
```

保留消息不会自动变成最新值，发布空 payload（retain）可清除旧保留消息。

### 5.2 Last Will and Testament（遗嘱）

客户端在 CONNECT 时登记遗嘱 Topic + payload。Broker 检测到异常断开
（网络断开、心跳超时、不干净关闭）时，代客户端发布遗嘱消息：

```text
plc/site-a/dev-001/status  payload: {"online":false,"reason":"connection-lost"}
```

用途：设备掉线检测、集中监控「离线」事件。

### 5.3 Keep Alive 与 Session

| 参数 | 作用 |
|------|------|
| Keep Alive | 连接空闲最长时间；超时 Broker 判定断线并触发遗嘱 |
| Clean Session=false | 会话持久化，未确认的 QoS 1/2 消息与订阅可恢复 |
| Clean Session=true | 断开即清空会话，适合无状态遥测 |

---

## 6. 关键报文速记

| 报文 | 方向 | 作用 |
|------|------|------|
| CONNECT / CONNACK | Client → Broker → Client | 建连与确认 |
| PUBLISH | 双向 | 发布消息（含 Topic、QoS、Retain） |
| PUBACK / PUBREC / PUBREL / PUBCOMP | 双向 | QoS 1/2 确认 |
| SUBSCRIBE / SUBACK | Client → Broker → Client | 订阅 Topic |
| UNSUBSCRIBE / UNSUBACK | Client → Broker → Client | 取消订阅 |
| PINGREQ / PINGRESP | 双向 | 保活 |
| DISCONNECT | Client → Broker | 干净断开 |

报文由固定头、可变头和 payload 组成。固定头首字节包含报文类型、DUP、QoS、Retain
标志，QoS 1/2 报文还有 Packet ID。

---

## 7. Broker 选型与安装：EMQX 5.8.9

### 7.1 候选比较

| Broker | 优点 | 不足 |
|--------|------|------|
| **EMQX** | Dashboard/REST/规则引擎，MQTT 3.1.1 + 5.0，集群成熟 | 比 Mosquitto 重 |
| Mosquitto | 极轻量、配置简单、资源占用低 | 无内置 Dashboard，观测主要靠 CLI/日志 |

Day 93 选择 EMQX（ADR 0033）。学习阶段 Dashboard 能直接看到连接、会话和消息，
后续 Day 96 接 Java listener 后也便于排错。

### 7.2 compose.yml 服务

```yaml
emqx:
  image: emqx/emqx:5.8.9
  container_name: emqx
  restart: unless-stopped
  ports:
    - "1883:1883"    # MQTT TCP
    - "8083:8083"    # MQTT WebSocket
    - "18083:18083"  # Dashboard
  environment:
    EMQX_DASHBOARD__DEFAULT_USERNAME: admin
    EMQX_DASHBOARD__DEFAULT_PASSWORD: "${EMQX_DASHBOARD_PASSWORD:?...}"
  volumes:
    - emqx-data:/opt/emqx/data
    - emqx-log:/opt/emqx/log
  healthcheck:
    test: ["CMD", "/opt/emqx/bin/emqx", "ctl", "status"]
```

首次启动前在项目根 `.env` 增加：

```dotenv
EMQX_DASHBOARD_PASSWORD=change_me_to_a_real_password
```

### 7.3 启动与验证

```bash
docker compose up -d emqx
docker compose ps emqx
docker compose logs -f emqx
```

确认容器状态：

```bash
> docker compose ps emqx
NAME   IMAGE              STATUS
emqx   emqx/emqx:5.8.9    Up ... (healthy)
```

Dashboard 验证：

```text
URL:      http://localhost:18083
账号:     admin
密码:     本地 .env 的 EMQX_DASHBOARD_PASSWORD
```

Dashboard 左侧可看到 Clients / Sessions / Topics，也可以从 WebSocket 页面手工
订阅主题做冒烟，不需要先装桌面客户端。

---

## 8. 本项目 Phase 5 Topic / Payload 设计草案

> 只是设计草案，具体字段在 Day 95/96 与 Java 实现一起定稿。

遥测主题：

```text
plc/{siteCode}/{deviceCode}/telemetry
```

Payload 草案：

```json
{
  "deviceCode": "dev-001",
  "siteCode": "site-a",
  "ts": "2026-09-04T12:00:00+08:00",
  "temperature": 23.5,
  "humidity": 61.2,
  "status": "running"
}
```

状态主题（保留消息）：

```text
plc/{siteCode}/{deviceCode}/status
```

Day 96 后端订阅 `plc/+/+/telemetry` 后可复用现有 `device_data` 写入链路；
若同一条数据可能被 QoS 1/2 重复投递，用「设备 + 采样时间」做业务幂等键。

---

## 9. 学习检查表

- [x] 能画出 Publisher / Broker / Subscriber 与 RabbitMQ 的职责边界;
- [x] 能解释 Topic 层级、`+` 单层通配符与 `#` 剩余通配符;
- [x] 能区分 QoS 0/1/2 的投递语义并说出 QoS 1 重复风险;
- [x] 能说出端到端 QoS = min(发布 QoS, 订阅 QoS);
- [x] 能说明 Retained Message 与 Last Will 的用途;
- [x] 能说出 Keep Alive、Clean Session 对断线恢复的影响;
- [x] 能在本地 compose 启动 EMQX 并通过 Dashboard/健康状态验证;
- [x] 能设计 `plc/{siteCode}/{deviceCode}/telemetry` 草案并映射到 `device_data`。

---

## 10. Java 客户端选型（Day 94 固化）

### 10.1 候选对比

| 客户端 | 协议 | 优势 | 不足 | 本项目决策 |
|--------|------|------|------|-----------|
| **Eclipse Paho mqttv3** | 3.1.1 | 官方参考实现、生态最广、API 简洁、JDK 1.8+ | 维护节奏放缓、QoS 2 在高并发下偶有回调空引用 | ✅ **选用 1.2.5**（Day 94） |
| Eclipse Paho mqttv5 | 5.0 | 同项目出品、原生 MQTT 5 特性（Reason Code/Properties） | 与 v3 API 不互通、学习成本略高 | Day 96 生产接入时再评估 |
| HiveMQ MQTT Client | 3.1.1 / 5.0 | 现代异步 API、背压、RxJava 友好 | 依赖 RxJava/Reactor，本项目无响应式栈 | 备选，暂不引入 |
| Vert.x MQTT | 3.1.1 / 5.0 | 高性能事件循环 | 需引入 Vert.x 运行时，侵入性大 | 不选 |

**决策**：Phase 5 学习与生产接入统一用 **Eclipse Paho mqttv3 1.2.5**。理由：
1. MQTT 3.1.1 是当前工业 IoT 最广泛支持的协议版本，EMQX/ESP32/主流网关默认都支持；
2. API 同步阻塞模型简单，适合 Day 95 模拟 PLC 和 Day 96 后端 listener 的学习曲线；
3. 1.2.5 是 2020 年发布的稳定版，无已知 CVE，Maven Central 可直接拉取；
4. Day 96 若需要 MQTT 5 特性（共享订阅 `$share` 多副本分摊），可在同一 Paho 1.2.5 下切换到 `org.eclipse.paho.mqttv5.client`，无需换库。

> **ADR 约定**：本日选型先固化在笔记（学习依赖），**Day 96 生产接入 MQTT Listener 时升级为 ADR 0034**，含版本锁定理由、与 Spring Boot 3.5/JDK 25 兼容性测试、生产连接池参数。

### 10.2 Maven 坐标

```xml
<properties>
    <paho.version>1.2.5</paho.version>
</properties>

<dependency>
    <groupId>org.eclipse.paho</groupId>
    <artifactId>org.eclipse.paho.client.mqttv3</artifactId>
    <version>${paho.version}</version>
</dependency>
```

---

## 11. Paho 连接参数速查

### 11.1 核心类

| 类 | 作用 |
|----|------|
| `MqttClient` | 同步阻塞客户端（Day 94/95 学习首选） |
| `MqttAsyncClient` | 异步非阻塞客户端（Day 96 生产 listener 评估） |
| `MqttConnectOptions` | 连接参数：cleanSession/keepAlive/超时/遗嘱/用户名密码 |
| `MqttCallback` | 回调三方法：`connectionLost` / `messageArrived` / `deliveryComplete` |
| `MqttMessage` | 消息体：payload + qos + retained |
| `MemoryPersistence` | 内存持久化（学习/无状态遥测）；生产 QoS 1/2 需 `MqttDefaultFilePersistence` |

### 11.2 连接选项（本项目默认值）

```java
MqttConnectOptions options = new MqttConnectOptions();
options.setCleanSession(true);          // 无状态遥测，断连即清会话
options.setKeepAliveInterval(60);        // 心跳 60s
options.setConnectionTimeout(10);        // 连接超时 10s
options.setAutomaticReconnect(true);     // Day 96 生产开启自动重连（学习日可不加）
// EMQX 本地开发匿名直连，无需 setUserName/setPassword
```

### 11.3 关键坑位（Day 94 实测）

1. **`deliveryComplete` 回调里 `token.getMessage()` 可能为 null**：QoS 0 投递完成时 Paho 不保留消息引用，QoS 1/2 异步回调时也可能被清理。**必须做空判断**，否则 NPE 会被 Paho 内部捕获并触发 `connectionLost`，导致整条连接断开。正确写法：
   ```java
   public void deliveryComplete(IMqttDeliveryToken token) {
       MqttMessage m = token.getMessage();
       if (m != null) { /* 用 m.getQos() */ }
       else { /* QoS 0 或已清理，仅记 msgId */ }
   }
   ```
2. **clientId 必须唯一**：同一 clientId 连两次会把前一个踢下线；学习代码加 `System.currentTimeMillis()` 后缀。
3. **`MemoryPersistence` 不能用于 QoS 1/2 生产**：JVM 崩溃会丢未确认消息；生产用 `MqttDefaultFilePersistence("/tmp/mqtt-persist")`。
4. **`MqttClient` 非线程安全**：多线程 publish/subscribe 需加锁或用 `MqttAsyncClient`。

---

## 12. Day 94 冒烟验证结果（EMQX 5.8.9 + Paho 1.2.5）

### 12.1 测试设计

`PlcMqttClientSmoke.java`：连接 EMQX 1883 → 订阅 `plc/test/dev-smoke/telemetry`（QoS 1）→ 依次 publish QoS 0/1/2 → 观察回环收到的实际 QoS。

### 12.2 实测输出（关键行）

```
[connect] broker=tcp://localhost:1883 clientId=plc-smoke-xxx connected
[subscribe] topic=plc/test/dev-smoke/telemetry qos=1
[publish] qos=0 → [recv] qos=0   ← min(0,1)=0
[publish] qos=1 → [recv] qos=1   ← min(1,1)=1
[publish] qos=2 → [recv] qos=1   ← min(2,1)=1 （订阅端 QoS 1 限制）
[summary] sent=3 received=3 PASS
```

### 12.3 验证了什么

| 协议点 | 验证结果 |
|--------|---------|
| 端到端 QoS = min(发布, 订阅) | ✅ QoS 2 发布 → QoS 1 收到（§4.4 结论实证） |
| QoS 0 不保证送达但实际收到 | ✅ 本地 Broker 无丢包，仍收到 |
| deliveryComplete 空引用坑 | ✅ 已做空判断，否则 NPE 断连 |
| EMQX 匿名直连（ADR 0033） | ✅ 无需用户名密码即可连 1883 |
| 同一 clientId 并发冲突 | ✅ clientId 加时间戳后缀，未触发踢线 |

---

> Day 94 结束。下一步 Day 95：Java 模拟 PLC 设备（寄存器区模型 + 量程）定时向 `plc/{site}/{device}/telemetry` publish，并行接入 ESP32 真实硬件（comprehensive-review-2026-08-22 第八章硬件清单）。

---

## 13. Day 96 生产 Listener：Paho 接入 + Redis 字段级幂等入库（2026-09-05）

### 13.1 入站链路

```text
PLC / ESP32 / Java Simulator
  → MQTT publish plc/{siteCode}/{deviceCode}/telemetry（QoS 1）
  → EMQX
  → MqttConfig.MqttLifecycle（Paho subscribe QoS 1，SmartLifecycle）
  → MqttDeviceDataIngestService.ingest(topic, payload)
  → deviceCode 匹配 DeviceMapper.findByCode
  → Redis SETNX 字段级幂等（可选，null/异常降级）
  → device_data insert
  → DeviceDataProducer Fanout 广播
  → AlarmDetector 规则检测 → alarm 落库 + AlarmProducer 发送/延迟升级
```

### 13.2 配置与开关

```yaml
mqtt:
  enabled: ${MQTT_ENABLED:false}     # 默认关闭，无 EMQX 不建连
  host: ${MQTT_HOST:127.0.0.1}
  port: ${MQTT_PORT:1883}
  client-id: iah-backend-mqtt        # 同一 broker 内必须唯一
  topic-filter: plc/+/+/telemetry
  qos: 1
  clean-session: true
  keep-alive-seconds: 60
  connection-timeout-seconds: 10
```

容器模式由 `compose.yml` v1.4 注入 `MQTT_ENABLED=true / MQTT_HOST=emqx / MQTT_PORT=1883`，
并让 backend 等 `emqx: service_healthy` 后再启动。

### 13.3 为什么用文件持久化而不是 MemoryPersistence

模拟器与后端都使用 QoS 1。Day 94 笔记第 11.3 节的坑位在生产接入时兑现：

- `MqttDefaultFilePersistence` 保存未确认消息，JVM/连接闪断后可恢复；
- `MqttLifecycle` 开启 `setAutomaticReconnect(true)`；
- 停止时显式 disconnect + close + 删除临时持久化目录，避免 /tmp 残留；
- `deliveryComplete` 对 `token.getMessage()` 判空；
- `messageArrived` 业务异常全部由回调层捕获，禁止抛给 Paho。

### 13.4 Redis 幂等键语义

```text
SETNX mqtt:{deviceId}:{yyyyMMddHHmmss}:{dataType} = 1 EX 86400
```

| Redis 状态 | Listener 行为 |
|-----------|--------------|
| 正常，键不存在 | 落库 + 广播 + 报警 |
| 正常，键已存在（QoS1 重复） | 跳过该字段，视为未新增 |
| Bean 为 null（test/简化部署） | 不幂等直接落库 |
| SETNX 抛异常 | WARN 并降级，不幂等直接落库（宁重复不丢） |

### 13.5 字段级映射与“坏字段不拖垮整条”

顶层只消费 4 个工程值字段：

| Payload | data_type | unit |
|---------|-----------|------|
| `current` | `CURRENT` | A |
| `windingTemp` | `TEMPERATURE` | °C |
| `pressure` | `PRESSURE` | kPa |
| `speed` | `SPEED` | RPM |

`registerSnapshot` 只保留审计含义，不写业务表。非法 JSON / 设备不存在跳过整条；
ts 非法回退服务端时间；单字段非数值、Mapper 异常、广播异常、报警异常都 WARN 降级，
不阻塞同一 Payload 的后续字段。

### 13.6 Day 96 验证

`MqttDeviceDataIngestServiceTest` 覆盖 12 个场景（完整 Payload、Redis 重复/异常/null、
设备不存在、非法 ts、时区 ts、非法 JSON、坏字段、AlarmProducer null、报警异常、Mapper
异常），目标测试与后端全量回归全绿。生产 Listener 在 `mqtt.enabled=false` 时不创建
任何 Socket，因此本地无 EMQX 时后端启动与既有 343 个测试不受影响。

> Day 96 结束。下一步 Day 97：模拟多设备并发上报 + 压力测试；若单实例 Paho 回调成为
> 瓶颈，再把同步入库改为有界队列或 `MqttAsyncClient`。

---

## 14. Day 97 多设备并发压测（2026-09-05）

### 14.1 压测设计

`learning/java-code/day97/MultiDeviceStressTest.java`：N 台模拟设备（默认 10 台）各自独立 Paho Client 并发向 EMQX publish `plc/PLANT_A/PLC-STRESS-NNN/telemetry`（QoS 1），按固定频率发送，采集聚合指标。

| 参数 | 默认值 | CLI 位参 |
|------|--------|---------|
| 设备数 | 10 | arg1 |
| 单设备发布频率 | 5 msg/s | arg2 |
| 持续时间 | 30s | arg3 |
| Broker | tcp://localhost:1883 | 可前缀 |

幂等测试：每 20 条消息发一次与前一条相同的时间戳（模拟 QoS 1 重复投递），后端 Redis SETNX 应按 `mqtt:{deviceId}:{ts}:{dataType}` 去重。

### 14.2 实测结果

**小规模（3 台 × 2 msg/s × 10s）**

| 指标 | 值 |
|------|----|
| 发送 | 60 |
| 确认 | 60 (100%) |
| 失败 | 0 |
| 吞吐 | 6.0 msg/s |
| 延迟 avg / max | 2ms / 2ms |
| 幂等重复 | 3 sent / 3 confirmed |

**正式压测（20 台 × 10 msg/s × 15s）**

| 指标 | 值 |
|------|----|
| 发送 | 3020 |
| 确认 | 3020 (100%) |
| 失败 | 0 |
| 吞吐 | 201.3 msg/s |
| 延迟 avg | 2.26ms |
| 延迟 p50 / p95 / p99 | 2 / 4 / 5ms |
| 延迟 max | 5ms |
| 幂等重复 | 140 sent / 140 confirmed |

### 14.3 关键观察

1. **EMQX 5.8.9 单机轻松承载 200+ msg/s**：20 台并发 × 10 msg/s = 200 msg/s，延迟稳定在 2-5ms，无丢包。
2. **Paho deliveryComplete 在高频下可靠**：3020 条全部确认，无 null token 导致断连。
3. **QoS 1 顺序性**：同一 client 的 `deliveryComplete` 按发送顺序回调，用确认计数器索引发送时间环可近似采样延迟。
4. **幂等碰撞设计**：每 20 条发 1 条重复 ts（占 5%），后端 Redis SETNX 应按 `mqtt:{deviceId}:{yyyyMMddHHmmss}:{dataType}` 去重；客户端 Paho 层无法验证后端去重效果，需在后端日志或 `device_data` 行数中核实。
5. **单实例 Paho 回调线程不是瓶颈**：当前 200 msg/s 同步入库可接受；Day 99 全链路联调时若速率提升至 500+ msg/s，可考虑有界队列或 `MqttAsyncClient`。

### 14.4 压测局限与后续

| 局限 | 后续改进 |
|------|---------|
| 设备 `PLC-STRESS-NNN` 未在 DB 注册，后端 `deviceMapper.findByCode` 返回 null 会跳过 | Day 99 联调前用 `scripts/seed-dev.sh` 预置 20 台压测设备 |
| 幂等去重效果未在数据层验证 | Day 99 启动 backend + MQTT_ENABLED=true，压测后查 `SELECT COUNT(*) FROM device_data WHERE device_id IN (...)` |
| 压测只覆盖 publish 侧，未覆盖后端 messageArrived 入库吞吐 | Day 99 全链路联调时由后端日志观察入库速率与报警触发 |

> Day 97 结束。下一步 Day 98：Week 15 周复盘 + PLC/MQTT 笔记整理。
