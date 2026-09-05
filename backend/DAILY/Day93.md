# Day 93 — MQTT 协议基础 + EMQX 5.8.9 Broker 安装（Phase 5 Week 15 第 2 天）

> **日期**：2026-09-04
> **阶段**：Phase 5 PLC + MQTT + 完整系统上线 · Week 15 第 2 天
> **分支**：`feat/day93-mqtt-basics`
> **配套笔记**：[mqtt-learning-notes.md](../../docs/notes/mqtt-learning-notes.md)
> **决策记录**：[ADR 0033](../../docs/decision-log/0033-mqtt-broker-emqx.md)
> **验收结果**：✅ **GO**（协议理解闭环 + Broker 安装到 compose + fail-fast 密码 + 文档/ADR 全对齐）

---

## 一、今日产出

| 模块 | 文件 | 说明 |
|------|------|------|
| 学习笔记 | `docs/notes/mqtt-learning-notes.md` | Broker 三角色 / Topic 层级通配符 / QoS 012 / 保留消息遗嘱 / EMQX 选型 / compose 安装 / Topic & Payload 草案 |
| 决策 ADR | `docs/decision-log/0033-mqtt-broker-emqx.md` | Broker=EMQX 5.8.9 / 与 RabbitMQ 职责边界 / 安全基线 / 备选分析 / 风险缓解 |
| compose.yml | `compose.yml` (v1.3) | 新增 `emqx` 服务：1883/8083/18083 端口、`.env` 密码 fail-fast、named volumes、healthcheck、`industrial-network` 接入 |
| 环境变量模板 | `.env.example` | 新增 `EMQX_DASHBOARD_PASSWORD=change_me`（ADR 0015 风格，仅模板无真实密钥） |
| 日日志 | `backend/DAILY/Day93.md` | 本文件 |

## 二、学习结论

### 2.1 MQTT 核心模型 vs RabbitMQ 边界

MQTT = **设备侧 IoT 接入协议**；RabbitMQ = **应用内部消息总线**。两者上下游关系，不替换：

```text
PLC/模拟设备  ──MQTT──▶  EMQX Broker  ──MQTT──▶  后端 MQTT Listener
                                                    │
                                                    ▼
                                          device_data 落库 / 规则引擎
                                                    │
                                                    ▼
                                          RabbitMQ (告警/巡检推送)
```

- EMQX：连接管理、Topic 路由、QoS 0/1/2、遗嘱 LWT、保留消息 Retained；
- RabbitMQ：Exchange/Queue/DLQ/延迟队列、应用内可靠投递；
- Day 96 后端接入 MQTT 后，消息路径 = MQTT in → 业务处理 → RabbitMQ out。

### 2.2 Topic / QoS 工程语义

- 本项目 Topic 草案：`plc/{siteCode}/{deviceCode}/telemetry`（遥测）与 `/status`（状态/保留）；
- QoS 建议：遥测 QoS 0（丢一帧影响小）、状态/报警 QoS 1（需至少一次 + Redis 幂等去重）；
- **端到端 QoS = min(发布, 订阅)**：两端都要设目标值，Day 96 Java 客户端需对齐；
- 遗嘱 + 保留消息 = 设备在线状态天然看板；`Clean Session=false` 恢复断连 QoS 1/2。

### 2.3 EMQX 安装基线（ADR 0033）

| 项 | 值 |
|----|----|
| 镜像 | `emqx/emqx:5.8.9`（compose 锁定） |
| TCP MQTT | `1883` |
| WS MQTT | `8083` |
| Dashboard | `18083` / `admin` / `.env` 的 `EMQX_DASHBOARD_PASSWORD` |
| 健康检查 | `/opt/emqx/bin/emqx ctl status`（start_period 20s） |
| 认证 | 本地开发允许匿名 MQTT 直连（仅内网）；Dashboard 强制 `.env` 密码 fail-fast |

### 2.4 关键工程注意点（为 Day 94/96 预埋）

1. MQTT QoS 1 **必然可能重复**（PUBACK 丢失→DUP 重发），Day 96 `device_data` 入库需 Redis 幂等键 `mqtt:{deviceCode}:{ts}` SETNX；
2. Topic 通配符订阅 `plc/+/+/telemetry` 即覆盖全部站点+设备；多副本后端后续可接 MQTT 5 共享订阅 `$share/group/plc/+/+/telemetry` 分摊；
3. 32 位浮点/整型跨 2 个寄存器的字节序，按 Day 92 点位表 `big-endian` / `little-endian` 标注解析，不在 MQTT 层猜测。

## 三、与 Phase 5 后续任务的关系

```text
Day 92 PLC/Modbus 概念
  ↓
Day 93 MQTT 协议 + EMQX Broker 安装 ← 本日
  ↓
Day 94 Java MQTT 客户端（Eclipse Paho）：连接 + publish + subscribe + QoS 验证
  ↓
Day 95 Java 模拟 PLC 定时发布：寄存器区模型 + 量程 + MQTT publish(telemetry/status/retain)
  ↓
Day 96 后端 MQTT Listener：订阅 plc/+/+/telemetry → 解析 payload → device_data 落库（Redis 幂等）
  ↓
Day 97 多设备并发发布 + 压力测试（至少 10 台模拟设备）
```

## 四、验证

### 4.1 配置 & 文档

- ✅ `docker compose config` 通过：`.env` 注入 `EMQX_DASHBOARD_PASSWORD`、端口不冲突；
- ✅ compose.yml v1.3 顶部标注与 Infrastructure Baseline V3 文案一致；
- ✅ `.env.example` 仅模板值，无真实密钥残留（符合 ADR 0015）；
- ✅ Service 注册表 Infrastucture-Baseline V1.3 新增 EMQX 行 + 审计记录；
- ✅ ADR 0033 与笔记、compose 实际参数三处一致；
- ✅ AGENTS §3 从 Day 92 bump 到 Day 93 完成。

### 4.2 基线回归（沿用 Day 91 Exit Audit 基线）

- 本日不改后端 Java/Vue 代码，也不加 Paho 依赖；
- 后端 `./mvnw test` 沿用 343/343 全绿；前端 `npm run build` 沿用 0 errors；
- Git 分支：本日与 Day 94 连续开发共用 `feat/day93-mqtt-basics`，自测通过后一并并入双主线并删除，保留 `main` + `pc_hula`。

## 五、明日计划（Day 94）

1. 后端 `pom.xml` 引入 **Eclipse Paho**（版本锁定，需补选型 ADR 或在笔记中固化）；
2. 写独立学习代码：`PlcMqttClientSmoke.java`（不进入生产应用包）——连接 EMQX、publish 到 `plc/test/dev-smoke/telemetry`、subscribe 同主题验证往返；
3. QoS 1 重放 + DUP 行为手工观察（Dashboard 看消息/连接）；
4. 追加 MQTT 笔记「Java 客户端连接参数」小节、创建 Day94.md 日志；
5. 后端测试与前端 build 继续全绿。

---

> 完成时间：2026-09-04（Asia/Shanghai）
> Phase 5 第 2 天状态：协议 + Broker 双闭环，Day 94 进入 Java 客户端代码日。
> 维护者：AI 助手 + hula0710
