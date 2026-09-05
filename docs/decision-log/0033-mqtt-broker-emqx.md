# Decision 0033: Phase 5 MQTT Broker — EMQX 5.8.9 与接入边界

| 属性 | 值 |
|------|------|
| **状态** | ✅ 已采纳（Day 93 安装落地） |
| **决策日期** | 2026-09-04 |
| **决策者** | hula0710 + AI 助手 |
| **关联** | Day 93 / ADR 0001 unified compose / DAILY_ROADMAP Week 15 / RabbitMQ ADR 0031 推送链路 |

## 1. 背景

Phase 5 需要把 PLC/模拟设备数据接入 Industrial AI Hub。Day 92 已经确定数据链路：
Modbus 点位 → 网关/边缘程序 → MQTT publish → Broker → 后端 MQTT subscribe →
`device_data` 落库 → 规则引擎/报警/AI。Day 93 的任务是补齐协议理解并安装 Broker，
避免到 Day 94（Java MQTT 客户端）和 Day 96（MQTT 入站落库）时被环境问题打断。

项目已有 RabbitMQ，但它服务的是**应用内部消息**（巡检日报、告警等），不是设备接入协议。
MQTT 是设备侧物联网协议，Broker 承担设备连接、Topic 路由、QoS 与遗嘱/保留消息职责。
两者不是替换关系，而是 Phase 5 设备入口与现有消息总线的上下游关系。

## 2. 决策

### 2.1 Broker：EMQX 5.8.9

在 `compose.yml` 增加固定版本 `emqx/emqx:5.8.9`，默认随基础服务启动：

| 项 | 决策 |
|----|------|
| Broker | **EMQX 5.8.9**（Docker，compose.yml 锁定） |
| MQTT TCP | `1883` |
| MQTT WebSocket | `8083` |
| Dashboard | `18083`（`admin` + `.env` 的 `EMQX_DASHBOARD_PASSWORD`） |
| 数据/日志 | named volumes `emqx-data` / `emqx-log`，不提交运行时数据 |
| Healthcheck | `/opt/emqx/bin/emqx ctl status` |
| 接入期认证 | 默认允许匿名直连，仅限本地开发；Dashboard 使用 `.env` 密码引导 |

选择 EMQX 的理由：

- 开箱即用提供 Dashboard、REST API、规则引擎与 WebSocket 监听，适合学习时观察连接和消息；
- 原生支持 MQTT 3.1.1 / 5.0，Java Paho（Day 94）无需额外网关即可接入；
- 资源占用适中，单容器足以覆盖本项目模拟 PLC 阶段；
- Dashboard 能直观验证 `1883` 上的连接数、Topic 和 QoS，Day 93-95 调试效率高。

### 2.2 职责边界与暂不接入

- Day 93 只安装 Broker 并验证容器健康，不改后端代码、不加 Java MQTT 依赖；
- RabbitMQ 继续承担应用消息（`alarm` / `inspection`），不因为引入 MQTT 而替换；
- 设备数据主题、payload 与后端 listener 在 Day 95/96 落地时再定稿；
- QoS 1/2 重复投递的幂等语义将在 Day 96 与 Redis 幂等键对齐，Day 93 只在笔记中固化协议理解。

### 2.3 安全基线

EMQX Dashboard 密码通过 `EMQX_DASHBOARD_PASSWORD` 从项目根 `.env` 注入，
缺失时 compose 显式报错（fail-fast，符合 ADR 0015 密钥 SSOT 精神）。
容器默认允许匿名客户端，只面向本地开发网络；公网/生产部署前必须开认证与 ACL。

## 3. 备选方案（未采纳）

| 方案 | 未采纳原因 |
|------|-----------|
| Mosquitto | 更轻量，但无 Dashboard/REST API，学习期观测成本高；保留为轻量备选 |
| 自建 TCP Broker | 偏离学习目标，重复造轮子，且无 QoS/遗嘱/保留消息生态 |
| 直接采购云 MQTT | 增加账号与网络依赖，当前本地学习阶段不需要 |

## 4. 影响与验证

- `compose.yml` 从 v1.2 → v1.3，基础设施默认多一个 `emqx` 服务；
- `.env.example` 新增 `EMQX_DASHBOARD_PASSWORD` 模板变量；
- 端口不与现有服务冲突：RabbitMQ `5672/15672`、Backend `8080`、EMQX `1883/8083/18083`；
- 文档：`docs/notes/mqtt-learning-notes.md`、`DAILY_ROADMAP`、`AGENTS.md`、
  `docs/Architecture/Infrastructure-Baseline.md`、`Application-Architecture.md` 同步；
- 验证：`docker compose config` 通过、`docker compose up -d emqx` 后健康检查为 healthy。

## 5. 风险与后续收口

| 风险 | 缓解 |
|------|------|
| 匿名直连被外部访问 | 仅本地开发启用；生产部署前配置用户名密码 + ACL，并将端口收口到内网 |
| Dashboard 默认密码未替换 | `.env` fail-fast；首次引导即使用用户提供密码 |
| MQTT QoS 1/2 重复消息导致重复入库 | Day 96 复用 Redis 幂等键模式（参考 Day 85 巡检链路 SETNX） |
| 与 RabbitMQ 概念混淆 | ADR 明确职责边界：MQTT = 设备接入，RabbitMQ = 应用内消息总线 |

---

> 最后更新：2026-09-04 | 维护者：AI 助手 + hula0710
