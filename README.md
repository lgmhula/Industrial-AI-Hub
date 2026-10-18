# Industrial AI Hub

> 工业 AI 设备管理平台 — 从 Java 基础到 AI 集成与真实硬件接入的完整技术栈闭环（112 天路线图）

[![v3.0-final](https://img.shields.io/badge/version-v3.0--final-blue)](https://github.com/lgmhula/Industrial-AI-Hub)
[![post-roadmap](https://img.shields.io/badge/post--roadmap-2026--10--18-orange)](#路线后增强)
[![JDK](https://img.shields.io/badge/JDK-25_LTS-orange)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.0-green)](https://spring.io/projects/spring-boot)
[![tests](https://img.shields.io/badge/tests-403%2F403-brightgreen)](#质量与性能)
[![License](https://img.shields.io/badge/license-MIT-lightgrey)](LICENSE)

---

## 项目简介

Industrial AI Hub 是一个面向工业场景的设备管理与 AI 分析平台：**真实 ESP32 硬件 + MQTT 上报传感器数据 → 规则引擎报警 → 继电器下行联动 → AI 诊断与巡检日报 → SSE 实时推送**，全链路可观测、可压测、可一键编排。

- 后端 Spring Boot 3.5 + MyBatis，前端 Vue 3 + Element Plus + ECharts（深色工业控制中心风格）
- 基础设施 Docker Compose v1.8 统一编排：**默认 7 服务一键启动，`--profile full` 20 服务**
- 按 16 周路线图渐进构建（Day 1-112），Phase 5 已完成真实硬件 6 段物理闭环
- 路线后增强已补活动告警聚合、Redis SSE fan-out、共享限流、Qdrant 可选后端、前端容器与集中日志

## 核心亮点

| 亮点 | 说明 | 证据 |
|------|------|------|
| 🏭 **真实硬件闭环** | ESP32-S3 + DHT22 → EMQX → 后端 → 规则报警 → **继电器下行联动**（哈气触发湿度报警、热风触发温度报警，双分支实测） | ADR 0033-0035，Day 99 真机 6 段闭环 |
| 🤖 **AI 不再是 demo** | DeepSeek 驱动 5 项业务价值：告警摘要 / 设备健康诊断 / RAG 知识库问答（PDF 入库 + 向量检索）/ Agent 巡检日报（ReAct 循环 + Function Calling）/ **AI 巡检自动生成报警** | ADR 0021-0030，9 个 AI 端点 + 7 个 MCP 只读工具 |
| 📊 **可观测三件套** | Prometheus 三把业务指标尺 + 全链路 traceId（`X-Trace-Id` 透传回传）+ 滚动日志归档（ERROR 独立文件） | ADR 0036，Day 100-101 |
| ⚡ **性能有证据** | JMeter 压测：读 40.3 req/s（p99 18ms）、写 20.1 req/s；突发 4639 req/s 下限流拒绝率 98.87%、142,771 样本 traceId 零回归；优化后限流日志量 **−92.8%** | Day 102-103 报告，ADR 0037 |
| 🔄 **多实例基础能力** | 活动告警数据库原子合并；RabbitMQ 消费经 Redis Pub/Sub 跨实例广播 SSE；Lua 令牌桶支持共享限流并保留本地回退 | ADR 0038-0039，Flyway V16 |
| 🧪 **403/403 全绿** | 压测守卫样本级验证幂等/限流/traceId 语义；新增 Redis/Qdrant/聚合回归 | 全量 `./mvnw clean test` |
| 🐳 **一键编排** | compose v1.8：默认 7 服务；`--profile full` 20 服务；前端 Nginx、Prometheus、可选 Loki/Promtail/Grafana 与 Qdrant | Baseline V1.8 |

## 架构总览

```text
                 浏览器（Vue 3 + Element Plus + ECharts）
                      │ REST /api/*（JWT + RBAC + 站点作用域）
                      ▼
   ┌──────────────────────── Spring Boot 3.5 后端 ────────────────────────┐
   │  TraceId Filter → MCP/JWT Filter → AI 限流 → 通用限流 → Auth 拦截器    │
   │                                                                    │
   │  业务：设备 CRUD / 数据查询统计 / 报警规则引擎(8 规则) / 操作日志 AOP  │
   │  AI  ：ChatClient + PromptTemplate + RAG(simple/Qdrant 可选)         │
   │        + Agent(ReAct) + 巡检日报 → RabbitMQ → Redis 幂等/fan-out     │
   │        → 各实例本地 SSE Emitter                                     │
   │  IoT ：MQTT Listener(Paho) → Redis SETNX 字段级幂等 → device_data   │
   │        → AlarmDetector → MqttCommandGateway(下行, 5s 节流)          │
   └────┬──────────────┬──────────────┬───────────────┬─────────────────┘
        ▼              ▼              ▼               ▼
   MySQL 8.4      Redis Stack     RabbitMQ        EMQX 5.8 MQTT Broker
   (10 表/Flyway) (缓存/幂等/      (队列/DLQ/延迟)    ▲              │
                   fan-out/限流)
                                                     │ telemetry    ▼ command
   Prometheus 2.55 ◀── /actuator/prometheus（双部署模式并存观测）
   Loki/Promtail/Grafana（observability/full profile，可选集中日志）
                                                     ESP32-S3 + DHT22 + 继电器（真机）
                                                     PlcSimulator（Java 模拟 PLC，并行接入）
```

> 完整分层架构、全链路 ASCII 图与三道附表见 [`docs/Architecture/Application-Architecture.md`](docs/Architecture/Application-Architecture.md)；
> 基础设施基线见 [`docs/Architecture/Infrastructure-Baseline.md`](docs/Architecture/Infrastructure-Baseline.md)（V1.8）。

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| **运行时** | JDK (Eclipse Temurin) | 25 LTS |
| **后端** | Spring Boot + MyBatis + PageHelper + Knife4j | 3.5.0 / 3.0.5 / 2.1.0 / 4.5.0 |
| **安全** | JJWT + BCrypt + Guava RateLimiter + Redis Lua 共享令牌桶 | 0.12.6 / — / 33.4.0-jre |
| **AI** | DeepSeek API（OpenAI 兼容）+ Spring AI 1.0.3 + MCP SDK 0.10.0 + PDFBox；Embedding/Qdrant 可选 | opt-in（ADR 0021/0040） |
| **IoT** | Eclipse Paho mqttv3 + EMQX | 1.2.5 / 5.8.9 |
| **数据** | MySQL 8.4（Flyway 管理 15 个活动迁移 / 10 表）；Qdrant 1.12.4 可选 | — |
| **缓存** | Redis Stack（Jedis + Redisson） | 7.4.0-v1 |
| **消息** | RabbitMQ（工作队列/发布订阅/DLQ/延迟队列） | 4.0-management |
| **观测** | Actuator + Micrometer + Prometheus；Loki/Promtail/Grafana 可选 | Boot parent / 2.55.1 / 3.2.1 / 11.3.0 |
| **前端** | Vue 3 + Vite + Element Plus + ECharts + Axios；Nginx 容器交付 | Node 22 / Nginx 1.27 |
| **预留** | Nacos 2.4.3 / Elasticsearch 8.17 / MinIO（`full` profile，不纳入主线） | — |

## 功能模块

| 模块 | 说明 |
|------|------|
| **认证与权限** | JWT 登录/注册、BCrypt、RBAC 三级角色（ADMIN/OPERATOR/VIEWER）+ 站点资源作用域（ADR 0020） |
| **设备管理** | CRUD + 逻辑删除 + 分页搜索 + 设备详情（含 AI 诊断/问答面板） |
| **设备数据** | MQTT 真机/模拟器双来源上报、时间范围查询、聚合统计 |
| **报警管理** | 8 条规则引擎、活动告警原子聚合（重复次数/最近触发）、确认/解决工作流、AI 摘要、AI 巡检自动生成报警 |
| **AI 助手** | RAG 知识库问答（PDF 上传入库 + 引文溯源）、告警摘要、设备健康诊断 |
| **巡检日报** | Agent 单会话巡检（MCP 7 只读工具）→ MQ → Redis 幂等/Pub/Sub → 多实例 SSE 推送 |
| **MCP** | Server（SSE + 传输鉴权）+ 客户端联调冒烟，显式工具注册边界 |
| **操作日志** | AOP 自动记录（含 AI 操作审计：CHAT/SUMMARY/DIAGNOSE/INSPECTION/AUTO_ALARM） |
| **观测与治理** | Prometheus 业务指标、traceId、滚动日志、本地/Redis 限流、可选 Loki+Grafana 集中观测 |

## 快速开始

> 完整「从零复刻」指南（环境变量、双启动路径、故障排查）见 [`docs/SETUP.md`](docs/SETUP.md)。

### 环境要求

- JDK 25 (Eclipse Temurin) · Docker（含 Compose v2）· Node.js 20+
- Maven 无需手动安装（内置 Maven Wrapper）

### 1. 配置环境变量

```bash
cp .env.example .env
# 至少设置：MYSQL_ROOT_PASSWORD / REDIS_PASSWORD / RABBITMQ_DEFAULT_PASS /
#          JWT_SECRET(≥32字符) / EMQX_DASHBOARD_PASSWORD（缺失将 fail-fast，ADR 0015）
```

### 2. 启动（路径 A：全容器一键编排）

```bash
docker compose up -d                     # 默认 7 服务：mysql/redis/rabbitmq/emqx/prometheus/backend/frontend
docker compose --profile full up -d      # 20 服务（另含 Qdrant、Loki/Grafana、Nacos/MinIO/ES、MySQL 主从、Sentinel）
```

### 3. 或：后端本地开发（路径 B：热更）

```bash
docker compose up -d mysql redis rabbitmq emqx
cd backend && ./mvnw spring-boot:run     # http://localhost:8080
```

> 容器化后端与宿主机后端并存时：`BACKEND_HOST_PORT=8082 docker compose up -d backend`
> （端口避让 + MQTT clientId 自动隔离，Day 104）

### 4. 演示数据（可选）

```bash
./scripts/seed-dev.sh                    # 幂等注入设备/传感器演示数据（ADR 0019 §5）
```

### 5. 访问前端

路径 A 启动后直接访问 **http://localhost:3000**。Nginx 会反代 `/api/` 到容器后端，SSE 路径关闭缓冲。

本地热更路径：

```bash
cd frontend && npm install && npm run dev   # http://localhost:5173
```

### 6. 验证

```bash
curl http://localhost:8080/actuator/health          # {"status":"UP"}
curl http://localhost:8080/actuator/prometheus      # 指标（mqtt_* 业务尺 + JVM）
curl http://localhost:8082/actuator/health          # 容器后端并存时（BACKEND_HOST_PORT=8082）
curl http://localhost:3000/healthz                  # 容器前端
```

登录：`admin / admin123`（生产请立即修改）

## API 文档

启动后端后访问 `http://localhost:8080/doc.html`（Knife4j，dev 环境）。

## 项目结构

```text
Industrial-AI-Hub/
├── compose.yml                  # Docker 编排 v1.8（core 7 / full 20）
├── .env / .env.example          # 密钥 SSOT（ADR 0015）
├── backend/
│   ├── pom.xml                  # Spring Boot 3.5 parent
│   ├── DAILY_ROADMAP.md         # 112 天路线图
│   ├── DAILY/ REVIEW/           # 每日日志 + 周复盘
│   ├── performance/             # JMeter 压测资产（jmx/脚本/分析器）
│   ├── learning/                # 学习代码（Day 94-97 MQTT 实验等，不参与构建）
│   └── src/main/java/dev/reboot/
│       ├── controller/ service/ mapper/ entity/ dto/     # 分层架构
│       ├── client/ mcp/ rule/ mq/                        # DeepSeek / MCP 工具 / 规则引擎 / 消息
│       ├── config/ security/ aop/ annotation/ enums/ exception/ util/
├── frontend/                    # Vue 3 业务视图 + Dockerfile + Nginx SSE 反代
├── observability/               # Loki / Promtail / Grafana provisioning + dashboard
├── prometheus/                  # 抓取配置（双部署模式并存观测）
├── docs/
│   ├── Architecture/            # 应用架构 V2.6 + 基础设施基线 V1.8
│   ├── decision-log/            # ADR 0001-0041
│   ├── ai/                      # AI 集成 Runbook + 学习笔记
│   └── reports/                 # 压测/性能/审计报告
├── mysql/ redis/ rabbitmq/      # 中间件配置与数据
└── scripts/                     # seed-dev.sh 等运维脚本
```

## 数据库

`reboot` 库，**10 张表**，schema 由 **Flyway** 管理（V1 + V3-V16，共 15 个活动迁移，`db/migration/`）：
`user` / `role` / `user_role` / `device` / `device_data` / `alarm` / `operation_log` / `site` / `user_site` / `login_audit`。
8 个 CHECK 约束覆盖全部枚举字段；`alarm` 另有活动周期生成列唯一索引；演示种子数据与生产迁移链隔离。

## 质量与性能

| 指标 | 数值 | 来源 |
|------|------|------|
| 单元测试 | **403/403** | `./mvnw clean test` |
| 前端构建 | 0 errors | 本轮 `npm run build` |
| 读性能 | 40.3 req/s，avg 8.5ms，p99 18ms（100% 200） | JMeter 5.6.3 压测（Day 102） |
| 写性能 | 20.1 req/s，avg 22ms，100% 落库 | 同上 |
| 突发抗压 | 4639 req/s 施压 → 拒绝率 98.87%，限流行为稳定 | 同上 |
| 链路追踪 | 142,771 样本 traceId 回传断言零失败 | 压测守卫 |
| 日志优化 | 限流拒绝日志 −99.996% 行数 / −92.8% 体积（行为零变化） | Day 103（ADR 0037） |
| 压力吞吐 | 20 台模拟 PLC × 10 msg/s = 201 msg/s，p99 5ms，0 失败 | Day 97 EMQX 实测 |

## 路线后增强

`v3.0-final` 保留为 Day 1-112 的历史检查点；以下能力在 tag 之后完成，未移动 tag 或 `main`：

- ADR 0038：活动告警按设备/类型原子 upsert，Flyway V16 唯一索引兜底并发，前端展示重复次数；
- ADR 0039：Redis SSE fan-out 与 `local|redis` 双模限流，通用限流采用 identity + path 双层桶；
- ADR 0040：`SimpleVectorStore|Qdrant` 与 `hash|OpenAI-compatible` embedding 可选切换，默认仍离线；
- ADR 0041：前端 Nginx 容器、Loki/Promtail/Grafana 可选观测链。

准确边界：多实例 SSE/限流基础能力已落地；跨主机故障切换、滚动升级、网络分区和容量 SLA 仍未完成。

## 学习路线

按 16 周路线图（`backend/DAILY_ROADMAP.md`）渐进构建：

| 阶段 | 周期 | 内容 |
|------|------|------|
| Phase 1 | Week 1-3 | Java 基础复苏（Day 1-21） |
| Phase 2 | Week 4-6 | Industrial AI Hub V1（Day 22-42） |
| Phase 3 | Week 7-9 | 中间件武装（Day 43-63） |
| Phase 4 | Week 10-13 | AI 集成（Day 64-91，tag `v2.0-ai`） |
| Phase 5 | Week 15-17 | PLC + MQTT + 真实硬件 + 完整系统上线（Day 92-112） |

> 当前进度：**Day 112 / 112 已完成**。官方路线以 `v3.0-final` 收口，Day 113-120 不属于当前路线。

## 文档索引

| 文档 | 说明 |
|------|------|
| [`ROADMAP-COMPLETE.md`](ROADMAP-COMPLETE.md) | 112 天路线完成报告（阶段成果 + 最终证据 + 路线后待办） |
| [`docs/SETUP.md`](docs/SETUP.md) | 从零复刻指南（克隆→配置→运行→验证） |
| [`docs/Architecture/Application-Architecture.md`](docs/Architecture/Application-Architecture.md) | 应用架构 V2.6（分层 + SSE/限流/RAG 全链路 + 附表） |
| [`docs/Architecture/Infrastructure-Baseline.md`](docs/Architecture/Infrastructure-Baseline.md) | 基础设施基线 V1.8（core 7 / full 20 + Profile 分级 + 持久卷） |
| [`docs/ai/phase4-integration-guide.md`](docs/ai/phase4-integration-guide.md) | AI 模块 6 步集成 Runbook + 故障速查 |
| [`docs/decision-log/`](docs/decision-log/) | ADR 0001-0041 + [一页决策导览](docs/decision-log/README.md) |
| [`docs/reports/`](docs/reports/) | 压测 / 性能 / 审计报告 + [工程踩坑手册](docs/reports/engineering-pitfalls.md) |
| [`docs/reports/post-roadmap-optimization-report-2026-10-18.md`](docs/reports/post-roadmap-optimization-report-2026-10-18.md) | 路线后增强验证矩阵、证据与剩余 HA 边界 |
| [`docs/reports/v3.0-final-release-report.md`](docs/reports/v3.0-final-release-report.md) | v3.0-final Release Gate、验证矩阵、已知边界与后续优先级 |
| [`docs/interview/project-introduction.md`](docs/interview/project-introduction.md) | 面试项目介绍（30 秒 / 3 分钟 / 10 分钟 + 高频追问与数字证据） |
| [`docs/interview/java-interview-guide.md`](docs/interview/java-interview-guide.md) | Java 后端八股（集合 / 并发 / JVM / Spring）+ 项目证据映射 + 30 题自测 |
| [`docs/interview/resume-materials.md`](docs/interview/resume-materials.md) | 简历材料（一句话 / 紧凑 / 扩展版 + STAR 故事 + 可写边界 + 投递检查） |
| [`docs/TECH-DEBT.md`](docs/TECH-DEBT.md) | 技术债务 SSOT 清单 |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | 分支流程 + 自测清单 |

## 开发约定

- **构造器注入**（禁 `@Autowired` 字段注入）· **统一响应** `ApiResponse<T>` · **4 空格缩进**（`.editorconfig`）
- **Git**：`main` 唯一发布线禁止直推（ADR 0017）；日常走分支 + PR；commit 格式 `Day XXX: 简要说明`
- **密钥**：根目录 `.env` 唯一事实源，禁入 Git（ADR 0015）；敏感变量缺失 fail-fast
- **架构变更**：新增依赖/重大决策必须记录 ADR；schema 变更走 Flyway 新迁移

## License

MIT

---

> 最后更新：2026-10-18（历史 `v3.0-final` 保持不动；路线后增强补告警聚合、Redis SSE/限流、Qdrant、前端容器与观测栈）| 维护者：[hula0710](https://github.com/lgmhula)
