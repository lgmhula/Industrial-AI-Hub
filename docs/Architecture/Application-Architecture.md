# Application Architecture V2.2

> **Status:** Active
> **Version:** 2.4
> **Updated:** 2026-08-30
> **Based on:** Phase 3 收官 + 安全治理合并（站点授权 / 用户安全状态 / JWT 生命周期 / 登录审计 / 限流 / 注册治理 + V7-V13 迁移）+ Phase 4 Day 66-83（DeepSeek / Spring AI / Function Calling / RAG / Agent / MCP Server + Client + 巡检联调）
> **Governs:** All application-layer decisions for Industrial AI Hub Backend

---

## 1. 技术栈总览

### Runtime & Build

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 25 LTS (Temurin) | 唯一运行时 |
| Maven | 3.9.6 | Maven Wrapper 锁定 |
| Spring Boot | 3.5.0 | 应用框架父 POM |
| Spring Framework | 6.2.7 | Spring Boot 3.5 内置 |

### Persistence & Data

| 组件 | 版本 | 说明 |
|------|------|------|
| MyBatis | 3.5.19 | ORM（注解模式，分页统一 PageHelper） |
| MyBatis-Spring-Boot | 3.0.5 | 自动装配 |
| MySQL Connector/J | 9.2.0 | JDBC 驱动 |
| HikariCP | 6.3.0 | 连接池 |

### Web & API

| 组件 | 版本 | 说明 |
|------|------|------|
| Tomcat (Embedded) | 10.1.41 | 内嵌容器 |
| Jackson | (内置) | JSON 序列化 |
| Knife4j | 4.5.0 | Swagger 文档 |

### Security

| 组件 | 说明 |
|------|------|
| BCrypt | 密码加密 |
| JWT (jjwt 0.12.x) | Token 认证 |
| AuthInterceptor | RBAC 权限拦截 |
| JwtAuthFilter | Token 解析 + 注入 |
| RateLimitInterceptor | API 限流 |

### Infrastructure (compose.yml)

| 服务 | 版本 | 端口 | 配置状态 |
|------|------|------|:--:|
| MySQL | 8.4 | 3307 | Active |
| MySQL Master/Slave×2 | 8.4 | 13306-13308 | Configured |
| Redis Stack | 7.4.0 | 6379, 8001 | Configured |
| Redis Sentinel ×3 | 7.4.0 | 26379-81 | Configured |
| RabbitMQ | 4.0 | 5672, 15672 | Configured |
| Nacos | 2.4.3 | 8848, 9848 | Configured |
| MinIO | RELEASE.2025-09-07 | 9000, 9001 | Configured |
| Elasticsearch | 8.17 | 9200, 9300 | Configured |
| Backend (Spring Boot) | JDK 25 | 8080 | Active (compose build) |

### AI（Phase 4 新增）

| 组件 | 说明 |
|------|------|
| DeepSeek API | OpenAI 兼容 Chat Completions；默认 `deepseek-chat`；opt-in 启用（`deepseek.enabled`） |
| Spring AI ChatClient | `spring-ai-starter-model-openai:1.0.3`（ADR 0022），显式 Bean 指向 DeepSeek baseUrl，业务层统一 ChatClient/PromptTemplate |
| DeepSeekClient | 保留为通用 `chat()` 协议层（RestClient + token 用量 + 503 语义，ADR 0021） |
| JSON 输出 | `OpenAiChatModel` 默认 `response_format=json_object`：告警摘要 / 设备健康诊断 |
| Function Calling | `@Tool` 声明式工具注册（零手写 JSON Schema，ADR 0023）：get_device_basic / list_device_recent_alarms / list_active_alarms_by_site / list_device_recent_data；`DeviceStatusAgentService` + `ToolCallingAgent` 手动工具循环（3/4 轮硬限 + 未参考实时数据标注，ADR 0026） |
| MCP Server | `spring-ai-starter-mcp-server-webmvc:1.0.3`（ADR 0027 / ADR 0028）：SSE `/mcp/sse` + `/mcp/message`，仅 tools 能力，`McpDeviceTools` 7 个只读设备/数据查询工具显式注册 |
| MCP Client | `io.modelcontextprotocol.sdk:mcp:0.10.0`（ADR 0029）：`McpClientService` SSE 握手 + 工具清单 + 只读探针；`POST /api/mcp/smoke`（ADMIN）；`McpAccessFilter` 可选 `X-MCP-Token` 传输鉴权 |
| Agent + MCP | `McpInspectionAgentService` 复用 `ToolCallingAgent`（6 轮硬限，ADR 0026）+ `McpInspectionSession` / `McpToolCallbackAdapter`（ADR 0030）：一次巡检一个 SSE 会话，自动巡检设备并生成中文日报；`POST /api/ai/agents/inspection-report`（ADMIN，INSPECTION/MCP 审计） |

### Observability & 部署 (Baseline V2.1)

| 项 | 说明 |
|------|------|
| Spring Profiles | `application.yml`（基础）+ `-dev.yml`（DEBUG/SQL stdout/Swagger）+ `-prod.yml`（WARN/Slf4j/Swagger 关）；`SPRING_PROFILES_ACTIVE` 切换，默认 dev |
| Actuator | 仅暴露 `health`（show-details: never），供 Dockerfile/compose HEALTHCHECK 探测 |
| Dockerfile | `backend/Dockerfile`：multi-stage（25-jdk-alpine 构建 → 25-jre-alpine 运行），non-root 用户，layer caching，HEALTHCHECK /actuator/health |
| JWT 密钥 | 生产环境由 compose 注入 `JWT_SECRET`；测试环境使用 `application-test.yml` 隔离配置 |

---

## 2. 分层架构

```
HTTP Request
  → JwtAuthFilter        (Bearer Token 解析，注入 userId/roles)
  → RateLimitInterceptor (QPS 限流)
  → AuthInterceptor      (@RequireRole 角色校验)
  → Controller            (@RestController)
  → Service               (@Service + BusinessException)
  → Mapper                (@Mapper, PageHelper 分页)
  → MySQL (HikariCP)
```

---

## 3. 模块清单

### Controllers (9)

| Controller | 端点 | 鉴权 |
|-----------|------|------|
| AuthController | POST /login, /register, /logout | 公开（含限流） |
| DeviceController | CRUD + searchDevices | VIEWER/OPERATOR/ADMIN |
| UserController | listPage/get/create/update/delete/toggleStatus/lock/unlock/resetPassword/assignRole/revokeRole | ADMIN |
| AlarmController | listPaged/listByDevice/listByStatus/acknowledge/resolve | VIEWER+ |
| DeviceDataController | report/list/listByTimeRange/stats/latest | VIEWER+ |
| OperationLogController | listPaged/listByUserId/listRecent | ADMIN |
| RoleController | CRUD + toggleStatus | ADMIN |
| SiteController | list | VIEWER+ |
| AiController | /api/ai/chat、/api/ai/alarms/{id}/summary、/api/ai/devices/{id}/diagnose、/api/ai/agents/device-status、/api/ai/agents/device-analysis、/api/ai/agents/inspection-report | VIEWER+（inspection-report 仅 ADMIN） |

### Services (21)

| 类别 | Service |
|------|---------|
| 业务核心 | DeviceService / UserService / AuthService / AlarmService / DeviceDataService / OperationLogService / AlarmDetector |
| 角色与站点 | RoleService / SiteService / SiteAccessService |
| 安全治理 | AuthRateLimitService / TokenBlacklistService / LoginAuditService |
| 基础设施 | CacheService |
| AI（Phase 4） | AiService（ChatClient 提示词编排 + DeepSeekClient 协议层，ADR 0021/0022）/ DeviceStatusAgentService + DeviceAnalysisAgentService + McpInspectionAgentService（ToolCallingAgent 手动循环，ADR 0026/0030）/ RagIngestionService + RagRetrievalService + PdfIngestionService（RAG 入库/检索/PDF 导入，ADR 0024/0025） |

### 中间件整合（Phase 3 新增）

| 包 | 内容 |
|----|------|
| `mq/` | RabbitMQ 管线：`AlarmProducer`/`AlarmConsumer`（工作队列）、`DeviceDataProducer`/`DeviceDataSyncConsumer`（发布/订阅）、`AlarmEscalationConsumer`（延迟队列 30s 升级）、`InspectionReportMessage`/`InspectionReportProducer`/`InspectionReportConsumer`（Day 85 AI 巡检日报投递+消费，ADR 0031 Phase 1-3；Redis SETNX 跨实例幂等 `inspection:{date}:{siteId}/all` TTL 24h；Push Gateway/SSE/Vue 在 Phase 4-7 待实现） |
| `rule/` | 报警规则引擎：`AlarmRule` / `AlarmRuleConfig` / `Operator` |
| 缓存 | Spring Cache（`@Cacheable`/`@CacheEvict`）+ Redisson 分布式锁（设备数据上报防重） |

### AI 集成（Phase 4 新增）

| 包 | 内容 |
|----|------|
| `client/` | DeepSeek Chat Completions HTTP 客户端（通用 `chat()` 协议层，503 统一错误映射） |
| `config/` | `OpenAiApi` / `OpenAiChatModel` / `ChatClient` 显式 Bean（DeepSeekProperties SSOT，ADR 0022） |
| `dto/ai/` | Chat 请求/响应、token usage、告警摘要 / 设备诊断 / 设备状态问答 / 巡检日报结构化 DTO |
| `tool/` | `DeviceAiTools`：4 个 `@Tool` 声明式工具（零手写 JSON Schema，ADR 0023），经 ToolContext 携带 userId 做站点作用域校验 |
| `service/AiService` | ChatClient/PromptTemplate 提示词编排 + 结构化 JSON 解析降级 + 站点作用域校验 |
| `service/DeviceStatusAgentService` | Function Calling 手动工具循环：最大 3 轮硬限、强制收尾、未参考实时数据标注、FUNCTION_CALL 审计元数据 |
| `service/DeviceAnalysisAgentService` | 多步推理 Agent：先查设备 → 再查数据 → 再分析，最大 4 轮硬限（ADR 0026） |
| `service/McpInspectionAgentService` | 巡检日报 Agent：单 MCP SSE 会话 + `ToolCallingAgent`（6 轮硬限），生成中文日报并返回设备/告警统计（ADR 0030）；Day 85 Phase 2 接入 `@Nullable InspectionReportProducer`，`generate()` 末尾投递 `InspectionReportMessage` 到 `inspection.exchange`，AmqpException 降级不阻塞主流程（ADR 0031 §3.1/§6） |
| `service/Rag*` | 文档切片 + 哈希向量 + 内存向量库入库/检索 + PDFBox 导入（ADR 0024/0025） |
| `controller/AiController` | `/api/ai/*`（VIEWER+），设备状态问答带 `@OperationLog(FUNCTION_CALL, {ret})`，巡检日报带 `@OperationLog(INSPECTION/MCP, {ret})` 且仅 ADMIN |
| `mcp/` | `McpDeviceTools` 7 个只读 @Tool + `McpToolConfig` 显式 ToolCallbackProvider（ADR 0027 / ADR 0028）+ `McpClientService` / `McpController`（`/api/mcp/smoke`）+ `McpAccessFilter`（X-MCP-Token 传输鉴权，ADR 0029）+ `McpInspectionSession` / `McpToolCallbackAdapter`（巡检会话与工具适配，ADR 0030） |

### 横切关注点

- GlobalExceptionHandler: BusinessException + @Valid + Exception 三层兜底
- OperationLogAspect: @OperationLog AOP 自动记录（finally 块确保异常也记录）
- ErrorCode 枚举: 统一错误码
- Operator 枚举: 告警规则比较运算符 (GT/LT/GTE/LTE/EQ/NEQ)
- BusinessException: Service 层统一异常

### 前端 (Vue 3 + Element Plus)

| 页面 | 路由 | 状态 |
|------|------|:--:|
| Login | /login | ✅ 含路由守卫 + 401 跳转 |
| Register | /register | ✅ 受注册开关 + 邀请码控制 |
| Dashboard | /dashboard | ✅ KPI + 告警流 + ECharts（默认首页） |
| DeviceList | /devices | ✅ |
| DeviceDetail | /devices/:id | ✅ |
| AlarmList | /alarms | ✅ 多选批量确认/解决 |
| UserList | /users | ✅ 含角色分配/锁定/解锁/重置密码 |
| RoleList | /roles | ✅ 角色 CRUD |
| OperationLogList | /logs | ✅ 服务端筛选 |
| NotFound | /:pathMatch(.*)* | ✅ 404 兜底 |

---

## 4. API 端点清单

约 41 个端点（覆盖 Auth / Device / DeviceData / Alarm / OperationLog / User / Role / Site / AI / MCP 模块）+ Knife4j 文档 (/doc.html)。

---

## 5. 数据库

`reboot` 数据库，9 张表（user / role / user_role / site / user_site / device / device_data / alarm / operation_log + login_audit），由 Flyway 管理（V1 基线 + V3 CHECK 扩展 + V4 站点授权 + V5 用户安全状态 + V6 登录审计 + V7 alarm/role 审计字段 + V8 admin 密码更新 + V9 AI 操作日志类型 + V10 FUNCTION_CALL + V11 RAG 知识 + V12 MCP_SMOKE/MCP + V13 INSPECTION 操作日志类型），零 FK，软删除策略。

---

## 6. 演进路线

> 唯一路线权威源：`backend/DAILY_ROADMAP.md`（本表与其严格对齐）。

| 阶段 | 周期 | Day | 内容 | 状态 |
|------|------|-----|------|:--:|
| Phase 1 | 第 1-3 周 | Day 1-21 | Java 复苏 | ✅ |
| Phase 2 | 第 4-6 周 | Day 22-42 | 项目 V1：CRUD / JWT / RBAC / 告警 / 前端 | ✅ v1.0 + Baseline V2.1 |
| Phase 3 | 第 7-9 周 | Day 43-63 | 中间件武装：Redis + RabbitMQ + Docker + Linux | ✅ 2026-08-16 |
| Phase 4 | 第 10-13 周 | Day 64-91 | AI 集成：DeepSeek → RAG → Agent/MCP | 🔨 Day 66-83 已完成 DeepSeek + ChatClient + Function Calling + RAG + Agent + MCP Server（含数据工具）+ MCP 客户端冒烟/传输鉴权 + Agent+MCP 巡检日报 |
| Phase 5 | 第 14-16 周 | Day 92-112 | PLC + MQTT + 完整系统 | 📅 计划 |
