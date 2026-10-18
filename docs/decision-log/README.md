# ADR 导览：0001-0041

> 最后更新：2026-10-18 | 维护：AI 助手 + hula0710

本页是 Industrial AI Hub 全部 ADR 的一页导览。详细背景、备选方案和验证证据仍以各 ADR
原文为准；本页只回答三个问题：**当时决定了什么、为什么、今天对系统有什么影响。**

## 阅读约定

| 状态 | 含义 |
|------|------|
| 已落地 | 决策已进入当前生产代码、配置或交付物 |
| 已采纳 | 决策仍有效；部分内容是阶段边界或后续演进目标 |
| 已收口 | 原问题已修复并有验证证据，但 ADR 保留历史背景 |
| 受 Profile 限制 | 能力只在特定 Compose profile 或显式开关下启用 |

> “已采纳”不等于“所有目标已实现”。当前 RAG 默认仍是 `SimpleVectorStore + hash`，
> Qdrant/OpenAI-compatible 是可选实现；`it` 集成测试为显式触发；Knife4j 只在开发环境开启。
> Redis SSE fan-out 与分布式限流降低单机绑定风险，但不等于生产级高可用已完成。

## 阶段全景

| 阶段 | ADR | 主题 |
|------|-----|------|
| 基础设施基线 | 0001-0008 | Compose、Redis、Nacos、MySQL、JDK、Spring Boot |
| 工程与数据契约 | 0009-0019 | 分层、响应、数据库、API 文档、密钥、分支、测试、Flyway |
| 安全与资源边界 | 0020 | 站点资源授权 |
| AI / RAG / Agent / MCP / SSE | 0021-0032 | DeepSeek、Spring AI、工具循环、MCP、推送与 IT 边界 |
| IoT / 观测 / 性能 | 0033-0037 | EMQX、Paho、MQTT 下行、Prometheus、限流日志 |
| 路线后增强 | 0038-0041 | 告警聚合、Redis SSE/限流、Qdrant/Embedding、前端与集中观测 |

## 基础设施基线（0001-0008）

| ADR | 决策 | 主要理由 | 当前影响 / 状态 |
|-----|------|----------|-----------------|
| [0001](0001-unified-compose.md) | 统一 Docker Compose | 降低多中间件启动与环境差异成本 | 当前由 `compose.yml` 管理 core/full 服务；已落地 |
| [0002](0002-redis-stack.md) | 采用 Redis Stack 全模块 | 一套镜像覆盖缓存、锁与数据结构实验 | 当前服务基线使用 Redis Stack；已落地 |
| [0003](0003-nacos-healthcheck.md) | 调整 Nacos healthcheck | 避免启动期误判和编排抖动 | Nacos 属可选 full-profile 能力；已采纳 |
| [0004](0004-mysql-port-policy.md) | MySQL 端口冲突治理 | 兼容本机 MySQL 与多实例编排 | Compose 主库使用 3307，主从实例使用 13306-13308；已落地 |
| [0005](0005-mysql-master-slave.md) | MySQL 主从拓扑 | 演示复制、读扩展与故障边界 | full-profile 能力，不作为默认开发依赖；已采纳 |
| [0006](0006-redis-sentinel-fixed.md) | 修复 Redis Sentinel 配置 | 解决容器网络、地址宣告与健康检查问题 | full-profile 中可作为高可用示例；已收口 |
| [0007](0007-jdk-25-lts.md) | JDK 25 LTS 作为运行时 | 与 Spring Boot 3.5 和长期支持路线对齐 | 后端构建/运行基线为 JDK 25；JMeter 单独优先 JDK 17；已落地 |
| [0008](0008-spring-boot-adoption.md) | Spring Boot 3.5 + MyBatis | 减少手工装配，同时保留 SQL 可控性 | 当前后端主框架；已落地 |

## 工程与数据契约（0009-0019）

| ADR | 决策 | 主要理由 | 当前影响 / 状态 |
|-----|------|----------|-----------------|
| [0009](0009-layered-architecture.md) | Controller -> Service -> Mapper | 明确职责、便于测试和权限切面 | 生产代码主分层；已落地 |
| [0010](0010-api-response-standard.md) | `ApiResponse<T>` 统一响应 | 前端错误处理与接口契约稳定 | REST 主路径统一响应；已落地 |
| [0011](0011-database-design-audit.md) | 零 FK、单值 `device_data`、V1 不做保留策略 | 工业写入吞吐、迁移弹性与项目阶段取舍 | 零 FK/单值模型当前有效；数据保留策略仍属生产化待办；已采纳 |
| [0012](0012-database-changelog.md) | 维护数据库 Schema changelog | 为 Flyway 之外的变更提供人类可读索引 | 当前已同步至 V16；活跃 |
| [0013](0013-api-docs-knife4j.md) | Knife4j + springdoc | 浏览器内联调、OpenAPI 契约清晰 | dev 开启，prod 关闭；受 Profile 限制 |
| [0014](0014-phase3-middleware-integration.md) | RabbitMQ 拓扑 + 多阶段镜像 + Nginx 反代 | 统一异步消息、部署与入口边界 | 报警/巡检消息、Dockerfile 与 Nginx 均已落地 |
| [0015](0015-dev-env-secrets-ssot.md) | 根 `.env` 为开发密钥唯一事实源 | 消除 IDEA/命令行配置漂移与密钥误提交 | 当前开发启动标准；敏感变量缺失 fail-fast |
| [0016](0016-charset-safe-init.md) | 修复数据库初始化中文乱码 | 避免 mysql CLI 默认字符集造成双重编码 | 历史问题已收口；当前由 Flyway JDBC 迁移承接 |
| [0017](0017-branch-strategy.md) | `main` 发布线，分支开发，squash/合并收口 | 保持发布线清晰且不引入过重 Git Flow | 当前仍执行；最近日常使用 `--no-ff` 合并保留 Day 上下文 |
| [0018](0018-ci-and-test-isolation.md) | CI 门禁 + H2 测试隔离 | 保证默认测试无外部中间件依赖 | `./mvnw test` 默认使用 H2；真实链路另走显式 IT |
| [0019](0019-flyway-migration.md) | Flyway 管理 Schema，演示种子移出迁移链 | 防止生产库自动灌入测试用户/设备/告警 | V1 + V3-V16 为活动链；V2 退役；开发种子经 `seed-dev.sh` 显式执行 |

## 安全与资源边界（0020）

| ADR | 决策 | 主要理由 | 当前影响 / 状态 |
|-----|------|----------|-----------------|
| [0020](0020-site-authorization.md) | `site/user_site/device.site_id` 站点作用域 | 修复纯 RBAC 下的跨站资源读取风险 | 设备、数据、告警查询已接入站点授权；已落地 |

## AI / RAG / Agent / MCP / SSE（0021-0032）

| ADR | 决策 | 主要理由 | 当前影响 / 状态 |
|-----|------|----------|-----------------|
| [0021](0021-deepseek-llm-provider.md) | DeepSeek OpenAI 兼容协议，显式 opt-in | 成本可控、无 Key 环境仍可启动 | `DEEPSEEK_ENABLED=false` 为默认；未启用返回 503；已落地 |
| [0022](0022-spring-ai-chatclient.md) | ChatClient/PromptTemplate 抽象 | 隔离供应商协议与业务提示词 | 摘要、诊断等结构化 AI 调用复用该层；已落地 |
| [0023](0023-function-calling.md) | Spring AI `@Tool` + 手动工具循环 + 3 轮硬限 | 自动工具循环无上限，无法精确治理轮次和成本 | 设备状态问答已落地；关闭 `internalToolExecutionEnabled` |
| [0024](0024-rag-vector-store.md) | Qdrant 为可演进目标，首版 `SimpleVectorStore` + 哈希向量 | 先离线打通 RAG 链路，避免过早引入外部依赖 | 默认仍为 `simple + hash`；Qdrant 与 OpenAI-compatible embedding 已作为可选实现落地，见 ADR 0040 |
| [0025](0025-rag-pdf-ingestion.md) | PDFBox + 上传端点导入知识 | Java 原生、无需外部文档服务 | `/api/rag/documents` 与 chunk 入库已落地 |
| [0026](0026-agent-loop-governance.md) | ReAct 手动循环、默认 3 轮/最多 6 轮、只读工具 | 用硬限、trace 和审计约束成本与副作用 | 单设备 Agent 与巡检 Agent 按不同任务设限；已落地 |
| [0027](0027-mcp-tool-exposure.md) | MCP 工具显式注册，阻断自动收集 | 防止内部依赖用户身份的工具被无身份通道暴露 | `McpToolConfig` 只注册外部只读工具；已落地 |
| [0028](0028-mcp-data-tools.md) | 设备详情/数据范围/统计/搜索共 7 个只读工具 | 覆盖巡检需要的最小查询面 | MCP 工具契约稳定；已落地 |
| [0029](0029-mcp-client-auth-smoke.md) | MCP Java SDK + SSE 客户端 + `X-MCP-Token` | 打通客户端链路，并提供传输层最低边界 | Token 为空时仅限内网开发的放行模式；已落地 |
| [0030](0030-mcp-agent-inspection.md) | 一次巡检单 MCP 会话，工具清单适配为 ToolCallback | 复用 Agent 循环，避免每工具重复建连 | 巡检日报入口与 INSPECTION 审计已落地 |
| [0031](0031-day85-ai-report-push-architecture.md) | Agent -> RabbitMQ -> Consumer -> Push Gateway -> SSE | RabbitMQ 不能直接推浏览器，必须显式分层 | 六段链路已实现；Redis Pub/Sub fan-out 已补，emitter 仍是各实例本地状态；不等于生产级高可用 |
| [0032](0032-day91-exit-audit-e2e-it-strategy.md) | 新增 opt-in `it` profile，真实 RabbitMQ/Redis 跑链路 | mock 全绿不能替代端到端证据 | `RUN_INSPECTION_IT=true` 才执行；SSE 浏览器 E2E 仍待补 |

## IoT / 观测 / 性能（0033-0037）

| ADR | 决策 | 主要理由 | 当前影响 / 状态 |
|-----|------|----------|-----------------|
| [0033](0033-mqtt-broker-emqx.md) | EMQX 5.8.9 作为 MQTT Broker | 轻量、可观测、QoS/retained/LWT 能力完整 | 端口 1883/8083/18083，healthcheck 与密码注入已落地 |
| [0034](0034-mqtt-listener-paho-redis-idempotency.md) | Paho SmartLifecycle + Redis 字段级 SETNX 幂等 | 处理 QoS 1 重复投递，保持业务写入可重试 | MQTT 遥测消费、报警分发与指标已落地 |
| [0035](0035-mqtt-relay-command-downlink.md) | `plc/{site}/{device}/command` 下行命令 | 补齐“数据 -> 决策 -> 执行器”物理闭环 | RELAY_ON、5 秒节流、ESP32 自动复位已落地 |
| [0036](0036-prometheus-metrics-observability.md) | Actuator + Micrometer + Prometheus 拉取 | 用业务指标而非只靠日志观察 MQTT 主链路 | 三条业务指标、HTTP/JVM 指标和双部署抓取目标已落地 |
| [0037](0037-rate-limit-log-throttling.md) | 限流拒绝日志按窗口节流，超时显式可配，开启 HTTP 直方图 | 修复 30 秒突发产生 23.2 MB 日志的放大器 | 日志体积下降 92.8%，行为不变；ADR 0039 补充 Redis 共享桶与跨实例日志窗口 |

## 路线后增强（0038-0041）

| ADR | 决策 | 主要理由 | 当前影响 / 状态 |
|-----|------|----------|-----------------|
| [0038](0038-active-alarm-aggregation.md) | 活动告警按设备/类型原子合并，resolved 后开启新周期 | 解决持续超限造成的报警风暴，并由数据库唯一键兜底并发 | Flyway V16 + upsert + `repeatCount/lastTriggeredAt`；已落地 |
| [0039](0039-redis-sse-fanout-distributed-rate-limit.md) | Redis Pub/Sub 广播 SSE；Lua 令牌桶支持多实例共享限流 | 消除“连接只能落在消费实例”和每 JVM 配额放大问题 | 服务端与集成链路已落地；Redis 异常回退本地，仍非生产级 HA |
| [0040](0040-qdrant-openai-embedding.md) | Qdrant 与 OpenAI-compatible embedding 做成显式可选实现 | 保持离线默认，同时验证真实语义向量与外部向量库边界 | 默认 `simple + hash`；`qdrant + openai-compatible` 需显式开启 |
| [0041](0041-frontend-container-loki-grafana.md) | 前端 Nginx 容器化；Loki/Promtail/Grafana 进入可选 profile | 补齐静态交付与集中日志观察，同时控制 core 启动成本 | Compose v1.8：core 7 / full 20；观测栈不默认启动 |

## 当前仍需区分的目标与边界

| 事项 | 当前事实 | 不应误写为 |
|------|----------|------------|
| 向量库 | 默认 `SimpleVectorStore` + `LocalHashEmbeddingModel`；Qdrant/OpenAI-compatible 需显式切换 | “默认已经使用 Qdrant/真实 embedding” |
| Knife4j / OpenAPI | dev 开启，prod 关闭 | “所有环境公开 API 文档” |
| 真实链路 IT | `RUN_INSPECTION_IT=true` 显式执行 | “默认 `mvn test` 已覆盖 E2E” |
| SSE 回调 | `sendSafely` + timeout + shutdown 兜底；Redis fan-out 已接入 | “单靠集成测试已证明所有浏览器生命周期” |
| MySQL 主从 / Sentinel / Nacos 等 | full-profile 编排能力 | “默认 core 启动即包含全部服务” |
| 多副本 | Redis 幂等、SSE fan-out、可选共享限流已支持；emitter 仍是实例本地状态 | “后端已无状态、生产级高可用已完成” |
| 数据保留 | ADR 0011 明确 V1 暂不做 | “生产保留策略已完成” |

## 新增 ADR 检查清单

1. 编号连续，文件名使用 `NNNN-kebab-case.md`。
2. 至少包含背景、决策、备选方案、后果/风险、验证证据。
3. 在根 `AGENTS.md`、本导览和相关架构文档中补入口。
4. 若涉及 Schema，同步 ADR 0012 与 `backend/src/main/resources/sql/README.md`。
5. 明确“已落地”和“后续目标”的边界，禁止把阶段计划写成既成事实。
