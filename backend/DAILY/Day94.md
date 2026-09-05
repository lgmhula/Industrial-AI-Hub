 # Day 94 — Java MQTT 客户端（Eclipse Paho）连接 EMQX 冒烟（Phase 5 Week 15 第 3 天）

> **日期**：2026-09-05
> **阶段**：Phase 5 PLC + MQTT + 完整系统上线 · Week 15 第 3 天
> **分支**：`feat/day93-mqtt-basics`（Day 93+94 连续开发共用）
> **配套笔记**：[mqtt-learning-notes.md](../../docs/notes/mqtt-learning-notes.md) §10-12
> **决策**：Eclipse Paho mqttv3 1.2.5（选型理由见笔记 §10，Day 96 生产接入时升级为 ADR 0034）
> **验收结果**：✅ **GO**（EMQX 连接成功 + publish/subscribe 回环 + QoS 0/1/2 端到端 min 验证 + 后端 343/343 全绿）

---

## 一、今日产出

| 模块 | 文件 | 说明 |
|------|------|------|
| Maven 依赖 | `backend/pom.xml` | 新增 `org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5`（properties 锁 `paho.version`，compile scope，Day 96 生产预埋） |
| 学习代码 | `backend/learning/java-code/day94/PlcMqttClientSmoke.java` | 连接 EMQX 1883 + 订阅 QoS 1 + 依次 publish QoS 0/1/2 + CountDownLatch 等回环 + QoS 端到端 min 验证 |
| 学习笔记 | `docs/notes/mqtt-learning-notes.md` §10-12 | Java 客户端选型对比 + Paho 连接参数速查 + 4 个实测坑位 + Day 94 冒烟结果 |
| 日日志 | `backend/DAILY/Day94.md` | 本文件 |

## 二、学习结论

### 2.1 客户端选型：Eclipse Paho mqttv3 1.2.5

| 候选 | 协议 | 决策 |
|------|------|------|
| **Eclipse Paho mqttv3 1.2.5** | MQTT 3.1.1 | ✅ 选用——官方参考实现、EMQX/ESP32 全支持、JDK 1.8+、同步 API 学习曲线低 |
| Paho mqttv5 | MQTT 5.0 | Day 96 评估是否切到 v5（共享订阅 `$share` 多副本分摊） |
| HiveMQ MQTT Client | 3.1.1/5.0 | 备选——需 RxJava 响应式栈，本项目无 |
| Vert.x MQTT | 3.1.1/5.0 | 不选——需引入 Vert.x 运行时，侵入性大 |

**ADR 约定**：本日作为学习依赖先固化在笔记；**Day 96 生产接入 MQTT Listener 时升级为 ADR 0034**，含版本锁定、Spring Boot 3.5/JDK 25 兼容性、连接池参数。

### 2.2 端到端 QoS = min(发布, 订阅) — 实测验证

订阅端 QoS = 1，依次发布 QoS 0/1/2，回环收到的实际 QoS：

| 发布 QoS | 收到 QoS | 端到端公式 |
|:--------:|:--------:|-----------|
| 0 | 0 | min(0, 1) = 0 |
| 1 | 1 | min(1, 1) = 1 |
| 2 | 1 | min(2, 1) = **1**（订阅端限制） |

→ Day 93 笔记 §4.4 的协议结论在 EMQX 5.8.9 + Paho 1.2.5 上联调实证通过。

### 2.3 关键坑位（实测踩坑）

1. **`deliveryComplete` 回调 `token.getMessage()` 可能为 null**：QoS 0 投递完成时 Paho 不保留消息引用，QoS 1/2 异步回调时也可能被清理。**不做空判断 → NPE → Paho 内部捕获 → 触发 `connectionLost` → 整条连接断开**。Day 94 首跑因此断连，修复后通过。
2. **clientId 必须唯一**：同 clientId 重连会踢前一个；学习代码加 `System.currentTimeMillis()` 后缀。
3. **`MemoryPersistence` 不能用于 QoS 1/2 生产**：JVM 崩溃丢未确认消息；生产用 `MqttDefaultFilePersistence`。
4. **`MqttClient` 非线程安全**：多线程 publish 需加锁或用 `MqttAsyncClient`。

## 三、验证

### 3.1 EMQX 容器

```
emqx  emqx/emqx:5.8.9  Up 15 hours (healthy)
      0.0.0.0:1883->1883/tcp, 0.0.0.0:8083->8083/tcp, 0.0.0.0:18083->18083/tcp
```

### 3.2 Smoker 实测输出（摘要）

```
[connect] broker=tcp://localhost:1883 clientId=plc-smoke-xxx connected
[subscribe] topic=plc/test/dev-smoke/telemetry qos=1
[publish] qos=0 → [recv] qos=0
[publish] qos=1 → [recv] qos=1
[publish] qos=2 → [recv] qos=1   ← min(2,1)=1
[summary] sent=3 received=3 PASS
[disconnect] done
```

### 3.3 后端回归

- `./mvnw test` → **343 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS**
- Paho 依赖（compile scope）不影响 Spring 上下文加载与现有测试；
- 本日未改生产 Java 代码（仅 pom + learning + 笔记），前端 build 沿用 Day 91 Exit Audit 基线。

### 3.4 EMQX Dashboard 验证

- Dashboard `http://localhost:18083`（admin + `.env` 的 `EMQX_DASHBOARD_PASSWORD`）可看到：
  - Clients：`plc-smoke-*` 短暂连接后断开；
  - Topics：`plc/test/dev-smoke/telemetry` 出现 3 条消息；
  - 连接/断开无报错，匿名直连生效（ADR 0033）。

## 四、与 Phase 5 后续任务的关系

```text
Day 92 PLC/Modbus 概念
  ↓
Day 93 MQTT 协议 + EMQX Broker 安装
  ↓
Day 94 Paho Java 客户端冒烟（本日）← 连接/publish/subscribe/QoS 全打通
  ↓
Day 95 模拟 PLC + 真实 ESP32 并行 publish：
  ├ Java 模拟器（寄存器区模型 + 量程）→ plc/{site}/{device}/telemetry
  └ ESP32 #1 + DHT22（comprehensive-review 第八章硬件清单）→ 同一 Topic 命名空间
  ↓
Day 96 后端 MQTT Listener：订阅 plc/+/+/telemetry → device_data 落库（Redis 幂等）
  ↓
Day 97 多设备并发 + 压测（Java 10 台 + ESP32 ×2）
```

## 五、明日计划（Day 95）

1. **Java 模拟 PLC**：`learning/java-code/day95/PlcSimulator.java`——建模线圈区/离散输入区/输入寄存器区/保持寄存器区（按 Day 92 点位表）+ 量程/单位/偏移 + 每 1~5s publish `plc/site-a/dev-sim-XXX/telemetry`（QoS 1）+ `status` 主题保留消息；
2. **真实 ESP32 #1 + DHT22 并行接入**：（由用户硬件侧完成烧录）按同一 Topic/Payload 格式 publish，后端 Day 96 不区分来源；
3. 在 `plc-modbus-learning-notes.md` 追加「模拟器寄存器区映射」小节；
4. 后端测试与前端 build 继续全绿；
5. 创建 Day95.md 日志。

---

> 完成时间：2026-09-05（Asia/Shanghai）
> Phase 5 第 3 天状态：Java MQTT 客户端链路打通，Day 95 进入「模拟 PLC + 真实 ESP32 双路上报」。
> 维护者：AI 助手 + hula0710
