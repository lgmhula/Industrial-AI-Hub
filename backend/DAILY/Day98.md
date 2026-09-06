# Day 98 — Week 15 周复盘 + PLC/MQTT 笔记整理（Phase 5 Week 15 第 7 天）

> **日期**：2026-09-06
> **阶段**：Phase 5 PLC + MQTT + 完整系统上线 · Week 15 第 7 天（周复盘日）
> **分支**：`feat/day98-week15-review`
> **配套产出**：[Week15.md](../../backend/REVIEW/Week15.md) 周复盘 + 两份学习笔记通读修订
> **验收结果**：✅ **GO**（后端 355/355 全绿 + 前端 build 1.16s 0 errors + Day 97 分支收口合并 main）

---

## 一、今日产出

| 模块 | 文件 | 说明 |
|------|------|------|
| 周复盘 | `backend/REVIEW/Week15.md` | Day 92-97（6 天）全段复盘：目标 vs 实际 / 5 项关键收获 / 演进全景 / 测试·Flyway·ADR·基础设施·文档指标 / 遗留&风险 / Week 16 计划 |
| 笔记整理 | `docs/notes/mqtt-learning-notes.md` §8 | 通读修订：设计草案标注「已定稿并落地（Day 95-97）」状态 + 交叉引用 plc §10.3/§10.4 与自身 §13/§14，澄清温度/湿度为 ESP32 画像、PLC 画像字段以定稿契约为准 |
| 笔记整理 | `docs/notes/plc-modbus-learning-notes.md` §10.6 | 通读修订：补「Week 15 收口（Day 96-97）」段——契约已被 `MqttDeviceDataIngestService` 消费落库 + Day 97 压测验证，ESP32 固件烧录排入 Day 99 |
| 日日志 | `backend/DAILY/Day98.md` | 本文件 |

> Git 收口（Day 97 补）：前一会话遗留的 Day 97 产物（Day97.md + day97 压测代码 + 3 份文档修订）
> 先在 `feat/day97-multi-device-stress` 提交（`1dc2dc2`），再 `--no-ff` 合并 main（`36f1699`），分支已删除。

## 二、Week 15 复盘要点（详见 Week15.md）

### 2.1 阶段达成：MQTT 从「学习项」变「真实入站通道」

Day 92-97 六天走完 **概念 → Broker → 客户端 → 模拟设备 → 生产接入 → 并发压测** 全链：

| Day | 主题 | 关键产物 |
|-----|------|---------|
| 92 | PLC/Modbus 概念 | plc-modbus-learning-notes.md（4 类数据区/功能码/点位表映射） |
| 93 | MQTT + EMQX 安装 | ADR 0033 + compose v1.3 emqx 服务 + mqtt notes §1-9 |
| 94 | Paho 客户端冒烟 | pom 引 paho 1.2.5 + PlcMqttClientSmoke（端到端 QoS=min 实测） |
| 95 | Java 模拟 PLC | PlcSimulator（4 区点模型 + 状态机）+ **Topic/Payload 契约定稿** |
| 96 | MQTT → 项目生产接入 | MqttProperties/MqttConfig/MqttDeviceDataIngestService + Redis SETNX 幂等 + ADR 0034，单测 +12 |
| 97 | 多设备并发压测 | MultiDeviceStressTest：**201.3 msg/s，3020 条 100% 确认，p99 5ms** |

### 2.2 基线指标（Week 15 变化）

| 指标 | Week 14 末 | Week 15 末 | 变化 |
|------|-----------|-----------|------|
| 后端单测 | 343 | **355** | +12（Day 96 MqttDeviceDataIngestServiceTest） |
| Flyway | V15 | V15 | 无新迁移（复用 device_data/alarm 表） |
| ADR | 0031 | **0034** | +2（0033 EMQX / 0034 Paho 幂等） |
| compose.yml | v1.2 | **v1.4** | +emqx 服务、+backend MQTT 注入 |

### 2.3 本周最值得复用的三条经验

1. **决策先行**：选中间件先落 ADR（0033/0034），本周两条边界都是「先选型、后编码」；
2. **契约先行**：模拟器与真实 ESP32 共享 `plc/{site}/{device}/telemetry` 命名空间，后端不区分数据源——模拟闭环后可无缝切真实硬件（6 段闭环排 Day 99）；
3. **幂等模式跨协议复用**：Day 85 RabbitMQ 的 Redis SETNX 思路直接平移到 MQTT QoS1 重复投递（`mqtt:{deviceId}:{ts}:{dataType}`），入站幂等成为默认契约。

## 三、笔记整理（通读修订明细）

| 笔记 | 修订点 | 原因 |
|------|--------|------|
| mqtt-learning-notes.md §8 | 标题下加状态横幅：草案已定稿落地（Day 95-97），链接最终契约与实现 | §8 保留 Day 93 原稿（temperature/humidity 为 ESP32 画像），避免后续读者把草案当最终契约 |
| plc-modbus-learning-notes.md §10.6 | 追加「Week 15 收口」段：契约已被消费（Day 96）并压测验证（Day 97），ESP32 接入位排 Day 99 | 文件止于 Day 95 视角，补齐契约落地后的追溯闭环 |

## 四、验证

- 后端全量回归：`./mvnw test` → surefire 报告汇总 **355 tests / 0 failures / 0 errors / 0 skipped**（54 个测试类），BUILD SUCCESS（Day 98 纯文档日，无生产代码变更）；
- 前端 build：`npm run build` → **1.16s，0 errors**（产物无回退）；
- Git：Day 97 分支收口（commit `1dc2dc2` + 合并 `36f1699` + 删分支），Day 98 在 `feat/day98-week15-review` 上作业，main 无直推。

## 五、明日计划（Day 99 — Phase 5 Week 16 第 1 天）

1. **完整系统联调**：MQTT 数据 → 业务处理 → 报警 → AI 分析 → 通知 全链路（DAILY_ROADMAP「第 15 周：系统整合 + 运维」首日）；
2. **真实 ESP32 + DHT22 硬件 6 段闭环**：手捂 DHT22 → 温度超限 → alarm 落库 → SSE → 继电器跳变（固件按 Day 95 契约烧录 `plc/PLANT_A/esp32-dht-001/telemetry`）；
3. 联调前置：`scripts/seed-dev.sh` 预置模拟器/压测设备（`PLC-SIM-001`/`PLC-STRESS-NNN`），数据层核实幂等去重行数；
4. 真实 SSE 端到端验证（Week 14 遗留 5）+ Git 远端同步（Day 92-98 本地提交 push/PR 收口，ADR 0017）。

---

> Day 98 结束。Phase 5 Week 15 收官：模拟 PLC → EMQX → 后端 → 报警链路闭环 + 201 msg/s 压测基线确立，
> 契约与接入位已为真实硬件预留。下一步 Day 99：完整系统联调 + ESP32 硬件 6 段闭环。
> 维护者：AI 助手 + hula0710
