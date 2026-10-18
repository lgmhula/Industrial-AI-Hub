# Application Architecture V2.6

> **Status:** Active
> **Version:** 2.6
> **Updated:** 2026-10-18
> **Based on:** Phase 3 中间件与安全治理 + Phase 4 AI 集成（Day 66-91，ADR 0021-0032）+ Phase 5 PLC/MQTT/观测与发布（Day 92-112，ADR 0033-0037）+ 路线后增强（活动告警聚合、Redis SSE fan-out、分布式限流、可选 Qdrant、前端容器与观测栈，ADR 0038-0041；Schema V16）
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
| RateLimitStore | `local`（Guava）/`redis`（Lua 原子令牌桶）双模；Redis 异常回退本机桶（ADR 0039） |

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
| EMQX | 5.8.9 | 1883, 8083, 18083 | Active |
| Prometheus | 2.55.1 | 9090 | Active |
| Backend (Spring Boot) | JDK 25 | 8080 | Active (compose build) |
| Frontend (Nginx) | Nginx 1.27 | 3000 | Active (compose build) |
| Qdrant | 1.12.4 | 6333, 6334 | Configured（full profile） |
| Loki / Promtail / Grafana | 3.2.1 / 3.2.1 / 11.3.0 | 3100 / — / 3001 | Configured（observability/full profile） |

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
| RAG 后端 | 默认 `SimpleVectorStore + LocalHashEmbeddingModel`；可切换 `QdrantVectorStore` 与 `OpenAiCompatibleEmbeddingModel`（ADR 0040），业务接口不变 |

### Observability & 部署 (Baseline V1.8)

| 项 | 说明 |
|------|------|
| Spring Profiles | `application.yml`（基础）+ `-dev.yml`（DEBUG/SQL stdout/Swagger）+ `-prod.yml`（WARN/Slf4j/Swagger 关）；`SPRING_PROFILES_ACTIVE` 切换，默认 dev |
| Actuator | 暴露 `health/info/prometheus`；health 供 Dockerfile/compose 探测，Prometheus 抓取三把业务指标尺与 HTTP 延迟直方图 |
| Dockerfile | `backend/Dockerfile`：multi-stage（25-jdk-alpine 构建 → 25-jre-alpine 运行），non-root 用户，layer caching，HEALTHCHECK /actuator/health |
| 前端交付 | `frontend/Dockerfile`：Node 22 构建 Vue，Nginx 1.27 提供静态资源、`/api/` 反代与 `/api/push/` SSE 专用配置 |
| 集中日志 | `observability`/`full` profile 可选 Loki + Promtail + Grafana；Promtail 读取 `backend-logs`，Grafana 预置 Prometheus/Loki 数据源 |
| JWT 密钥 | 生产环境由 compose 注入 `JWT_SECRET`；测试环境使用 `application-test.yml` 隔离配置 |

---

## 2. 分层架构

```
HTTP Request
  → TraceIdFilter        (MDC traceId + X-Trace-Id 透传/回传)
  → McpAccessFilter      (可选 X-MCP-Token 传输门)
  → JwtAuthFilter        (Bearer Token 解析，注入 userId/roles)
  → AiRateLimitInterceptor (AI 端点独立桶)
  → RateLimitInterceptor (identity + path 双层桶；local/redis 双模)
  → AuthInterceptor      (@RequireRole 角色校验)
  → Controller            (@RestController)
  → Service               (@Service + BusinessException)
  → Mapper                (@Mapper, PageHelper 分页)
  → MySQL (HikariCP)
```

---

## 2a. AI 巡检日报推送链路完整图（Phase 4 Day 85 + Day 86 闭环，ADR 0031 + ADR 0030 合并）

> 目标：ADMIN 手动 `POST /api/ai/agents/inspection-report` 触发一次巡检 → 毫秒级落 alarm（AI 异常转业务报警，Day 86）→ 秒级经 RabbitMQ → 消费幂等 → Redis Pub/Sub fan-out → 各实例本地 Push Gateway 按 siteIds 路由 → SSE 具名事件 → Nginx 反代 → 浏览器 InspectionReport.vue 卡片实时追加。
> 所有组件均为**独立可降级**（单点失败不阻塞其他组件）。基础边界见 ADR 0031，多实例 fan-out 与首帧/心跳见 ADR 0039。

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│  ① 触发入口（JWT ADMIN 仅允许）                                                                                         │
│  POST /api/ai/agents/inspection-report                                                 @OperationLog(INSPECTION/MCP)   │
│        │ AiController                                                                                                   │
│        ▼                                                                                                                │
│  McpInspectionAgentService.generate(userId, reportDate)  ──────────────────── 【Day 86 AI→业务闭环接入点】               │
│        │                                                                                             ↓                 │
│        │  1) McpInspectionSession (AutoCloseable)                                        AiAlarmAutoCreator             │
│        │     └─ MCP Java SDK 0.10.0 SSE 单会话握手                                                   │                   │
│        │  2) ToolCallingAgent(systemPrompt=巡检, maxRounds=6)                                         │                   │
│        │     └─ 工具：McpDeviceTools 7 只读  (mcp_list_devices / mcp_get_device_data_range / ...)      │                   │
│        │  3) toResult() → AiInspectionReportResult  ←detectedIssues(List) + report(String)+统计        │                   │
│        │                                  │                                                          │                   │
│        │                                  ▼ Step A (Day 86, 先落盘=保证)                             │                   │
│        │                    createAlarms(userId, result)  @OperationLog(AUTO_ALARM/ALARM)             │                   │
│        │                       │                                                                      │                   │
│        │                       │ ❲6级降级❳                                                           │                   │
│        │                       ├─ severity clamp 1-3                                                 │                   │
│        │                       ├─ deviceId resolve (id 优先 / code fallback findByCode)              │                   │
│        │                       ├─ Redis SETNX 24h: ai-alarm:{deviceId}:{alarmType}:{yyyy-MM-dd}      │                   │
│        │                       │    (Redis↓ → 降级：不幂等，宁可重复也不丢)                            │                   │
│        │                       └─ AlarmService.createAlarm(...) → alarm 表                           │                   │
│        │                                                                                              │                   │
│        │                                  │                                                          │                   │
│        │                                  ▼ Step B (后投递=不阻塞)                                    │                   │
│        ▼        @Nullable InspectionReportProducer.send(message)  ──AmqpException→ WARN 降级         │                   │
│    (Agent return 已发生，失败不返回 5xx 给调用方)                                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘
                                           │ InspectionReportMessage (Jackson 序列化 JSON)
                                           ▼
┌──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│  ② RabbitMQ (inspection.*)                                                                                            │
│  Topic exchange「inspection.exchange」 routingKey=inspection.new ──┐                                                    │
│      │                                                               │ DLX (死信交换，任何失败→DLQ 人工查看)                 │
│      ▼                                                               │                                                    │
│  inspection.queue (durable, no TTL, 与 alarm 队列同模式无 TTL)        │ binding: x-dead-letter-exchange=inspection.dlx       │
│      │                                                               ▼                                                    │
│      │ (失败 nack(requeue=false) → inspection.dlq.queue )           inspection.dlq.queue (durable)                      │
│      ▼                                                                                                                  │
│  @RabbitListener InspectionReportConsumer  ackMode=MANUAL (@Profile("!test"), 测试 profile 不启)                         │
│     [入站链路]                                                                                                           │
│     1) 消息反序列化 → InspectionReportMessage                                                                            │
│     2) 跨实例幂等：Redis SETNX「inspection:{reportDate}:{siteId}/all」TTL 24h                                            │
│        ├─ 多站点语义：message.siteIds 中**未被任何实例处理过的 siteId 子集**继续推送；全命中 → ack+skip                    │
│        └─ Redis↓ → 降级：不幂等，直接推送（宁可重复不丢，前端 reportDate 去重二次防线）                                     │
│     3) 处理成功 basicAck；处理失败 basicNack(requeue=false) → DLQ → 消峰                                                  │
│          │                                                                                                              │
│          ▼ InspectionPushPubSub.publish(message, accessibleSiteIdsFromMessage)                                         │
│     Redis channel「industrial-ai-hub:inspection-report」                                                               │
│       ├─ Redis 不可用或无订阅者 → 回退本实例 Push Gateway（单机仍可用）                                                │
│       └─ 每个后端实例订阅 → 仅向本机 SseEmitterRegistry 推送                                                           │
│                                                                                                                         │
│     本实例 InspectionPushGateway.sendToSites(...)                                                                       │
│       ├─ 空 siteIds = ADMIN 全站点；非空按 SseEmitterSession.canReceive(siteIds) 精确匹配                              │
│       ├─ sendSafely() 每条 emitter 独立 try/catch，失败只触发本地条件删除                                              │
│       └─ 成功 → event='inspection-report' + id=reportDate + data=JSON(message)                                         │
│                                                                                                                         │
│     SseEmitterRegistry（每实例 ConcurrentHashMap<userId, SseEmitterSession>）                                          │
│       ├─ 建连发送 connected comment 首帧；15 秒 comment heartbeat；30 min timeout                                      │
│       ├─ onCompletion / onTimeout / onError 按 emitter 实例条件删除，旧回调不误删新连接                               │
│       └─ @PreDestroy ApplicationContext shutdown 三重泄漏防护                                                          │
└──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘
                                           │
                                           ▼  HTTP text/event-stream over SSE
┌──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│  ③ Push Controller + JWT（Spring Boot 8080） @Profile("!test")     @OperationLog(PUSH/SSE)                           │
│  GET /api/push/inspection/access（普通权限探测） + /api/push/inspection（SSE）                                       │
│     │ JwtAuthFilter：❲双 token 源 ❳                                                                                    │
│     │   ① Header Authorization: Bearer <token>（优先，防 query 覆盖攻击）                                              │
│     │   ② Query  ?token=<token>（fallback，仅 /api/push/** 路径启用；REST 路径不读，防 URL 泄漏 token 到 access_log） │
│     │                                                                                                                  │
│     ▼ SiteAccessService.accessibleSiteIds(userId)                                                                     │
│        - ADMIN → empty（= 全站点语义，PushGateway 会按 ADMIN emitter 广播所有消息）                                      │
│        - VIEWER/OPERATOR → user_site 表 siteId 集合；**空集合直接 403 FORBIDDEN**（P0：避免空集合被误解释成全站点）    │
│                                                                                                                        │
│     ▼ new SseEmitter(30min) + 构造 SseEmitterSession(userId, siteIds, emitter)                                         │
│        └─ 注册到 SseEmitterRegistry，onCompletion/onTimeout/onError 自动 de-reg                                        │
└──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘
                                           │  HTTP 1.1 反代
                                           ▼
┌──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│  ④ Nginx（compose 服务名 frontend，Docker 唯一入站口）                                                                │
│  location /api/push/ {                                                                                                 │
│      proxy_pass http://backend:8080;                                                                                   │
│      proxy_buffering off;            # SSE 关键：立即 flush 给浏览器，不缓冲                                            │
│      proxy_cache off;                # 不缓存任何 SSE 片段                                                              │
│      proxy_read_timeout 2100s;       # 覆盖默认 60s：高于 30min emitter 超时                                          │
│      proxy_http_version 1.1;         # 支持持久连接                                                                     │
│      proxy_set_header Connection ""; # 关闭 close hop，Spring Boot 端保持长连接                                        │
│      access_log off;                 # P0 安全：token 在 ?token=，关日志防 token 泄漏到磁盘                           │
│  }                                                                                                                     │
│  ⚠️ 其余 /api/* REST 端点 location 仍然使用默认 proxy_buffering on + access_log on，保持常规性能/审计语义                │
└──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘
                                           │  EventSource /api/push/inspection?token=xxx
                                           ▼
┌──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│  ⑤ 浏览器 InspectionReport.vue（Vue 3 + EventSource）                                                                 │
│  先请求 /access 探测权限 → new EventSource(`/api/push/inspection?token=${jwt}`)  ❲JWT 仅 URL 携带❳                  │
│     │                                                                                                                  │
│     ├─ 收到 connected comment / onopen → 连接态脉冲圆点 connected                                                     │
│     ├─ 15 秒 heartbeat comment 保持连接；断线按 readyState 进入重连态                                                  │
│     ├─ onerror readyState===2(CLOSED) → 手动 3s 重试 + reconnecting 圆点（EventSource 默认自动重连指数退避一起生效）   │
│     ├─ addEventListener('inspection-report', e → JSON.parse(e.data))                                                  │
│     │      │                                                                                                          │
│     │      ├─ 去重：reportDate Set 已存在 → skip（前端第三道幂等防线）                                                 │
│     │      ├─ 限制：最多 50 条（内存）                                                                                 │
│     │      ├─ 映射：autoAlarmCount 徽章 + detectedIssues 异常折叠卡 + severity 徽章 + description XSS 双转义          │
│     │      └─ 滚动：新卡片追加到顶部，scrollToBottom                                                                    │
│     └─ onUnmounted(() => es.close())     组件卸载关闭连接防泄漏                                                          │
│                                                                                                                        │
│  【独立页】                                                                                                             │
│  - Route: /inspection（App.vue Sidebar Notification 菜单项）                                                         │
│  - 三态 UI：EmptyState / 连接中 pulse dot / 连接成功 / reconnecting                                                    │
│  - 空态 3 种：无权限 / 从未产生日报 / 产生过日报但暂无数据                                                              │
└──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

### 2a.1 三道幂等防线 + 降级语义总表

| 防线 | 组件 | 键 | TTL | 失败降级 |
|------|------|----|-----|---------|
| 第一道 | InspectionReportConsumer（MQ 侧） | `inspection:{reportDate}:{siteId}/all` | 24h | Redis 异常 → 跳过幂等，直接推送（宁可重复不丢） |
| 第二道 | AiAlarmAutoCreator（AI → ALARM） | `ai-alarm:{deviceId}:{alarmType}:{yyyy-MM-dd}` | 24h | Redis 异常 → 跳过幂等，直接 createAlarm（宁可重复也不丢告警） |
| 第三道 | 浏览器 InspectionReport.vue（UI 侧） | `reportDate`（内存 `new Set()`） | 会话内 | 无失败（纯内存） |

> 降级语义对齐 ADR 0031 §6：所有组件单点失败，都不会阻塞"正常通道"的继续执行；降级行为 = 放弃幂等（宁可重复），而不是失败关闭。

### 2a.2 三道安全点

1. **PushController 空 siteIds = 403（非 ADMIN）**：P0 防护，避免非 ADMIN VIEWER `user_site` 没数据时 "空集合被 ADMIN 语义当作全站点"越权；
2. **JwtAuthFilter 仅 `/api/push/**` 启用 query token fallback**：REST 端点不读 query，防止 ?token= 被"复制 URL 分享到飞书/钉钉"泄漏；同时 header 优先级高于 query，防 query 参数覆盖 header 攻击；
3. **nginx `/api/push/` access_log off**：?token= 会写进默认 access_log $request_uri，必须显式关；其余 `/api/*` 保持审计开启。

### 2a.3 能力状态（原扩展点更新）

| 能力 | 状态 | 实现/边界 |
|------|:--:|------|
| 多实例 SSE 广播（跨 JVM fan-out） | ✅ 已落地基础能力 | `InspectionPushPubSub` 使用 Redis channel 广播，各实例只推本机 emitter；实例故障仍依赖浏览器重连，未做跨主机 HA SLA |
| SSE 首帧与心跳 | ✅ 已落地 | 建连首帧 `connected` comment + 15 秒 heartbeat；失败仅移除本地会话，不调用全局 `complete()` |
| 浏览器级 SSE E2E | ✅ 已完成 | `/inspection` 连接、停留、离开与重连实测；后端无 `AsyncRequestNotUsableException` |
| cron 每日定时巡检 | 未实现 | 当前仍由 ADMIN 手动 POST 触发，不能写成已自动化 |
| PLC 异常即时推送 | 未实现 | PLC telemetry 已接入报警；独立 `plc-alert.exchange` 推送链路不在当前范围 |

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
| `mq/` | RabbitMQ 管线：`AlarmProducer`/`AlarmConsumer`（工作队列）、`DeviceDataProducer`/`DeviceDataSyncConsumer`（发布/订阅）、`AlarmEscalationConsumer`（延迟队列 30s 升级）、`InspectionReportMessage`/`InspectionReportProducer`/`InspectionReportConsumer`（AI 巡检日报，ADR 0031）；`InspectionPushPubSub` 在 Redis 幂等后 fan-out 到各实例，本机 `InspectionPushGateway` + `SseEmitterRegistry` 推送，见 §2a |
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
| `service/Rag*` | 文档切片 + 入库/检索 + PDFBox 导入（ADR 0024/0025）；`RagConfig` 通过 `rag.vector-store` 选择 Simple/Qdrant，通过 `rag.embedding.provider` 选择 hash/OpenAI-compatible（ADR 0040） |
| `rag/QdrantVectorStore` | Qdrant REST VectorStore：Cosine collection、稳定 UUID、payload 原文/metadata、维度冲突显式失败（ADR 0040） |
| `rag/OpenAiCompatibleEmbeddingModel` | JDK HttpClient 调 `/embeddings`，支持 dimensions 开关、响应排序/维度校验；Key 缺失 fail-fast（ADR 0040） |
| `controller/AiController` | `/api/ai/*`（VIEWER+），设备状态问答带 `@OperationLog(FUNCTION_CALL, {ret})`，巡检日报带 `@OperationLog(INSPECTION/MCP, {ret})` 且仅 ADMIN |
| `mcp/` | `McpDeviceTools` 7 个只读 @Tool + `McpToolConfig` 显式 ToolCallbackProvider（ADR 0027 / ADR 0028）+ `McpClientService` / `McpController`（`/api/mcp/smoke`）+ `McpAccessFilter`（X-MCP-Token 传输鉴权，ADR 0029）+ `McpInspectionSession` / `McpToolCallbackAdapter`（巡检会话与工具适配，ADR 0030） |

### MQTT 设备接入（Phase 5 Day 96 新增，ADR 0034）

| 包/类 | 内容 |
|--------|------|
| `config/MqttProperties` | `mqtt.*` 配置：默认关闭，host/port/clientId/topicFilter/QoS/keepAlive/connectionTimeout |
| `config/MqttConfig` | `@Profile(!test)` + `@ConditionalOnProperty`；SmartLifecycle 管理 Paho `MqttClient`，EMQX 连接与订阅，文件持久化 + 自动重连 |
| `service/MqttDeviceDataIngestService` | MQTT Payload → `deviceCode` 匹配 → Redis 字段级幂等 → `device_data` 落库 → Fanout 广播 → AlarmDetector 报警；复用 REST 上报同一业务链路 |
| `mqtt` 设备边界 | EMQX = 设备接入；RabbitMQ = 应用内部消息总线；两者保持上下游职责 |

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
| InspectionReport | /inspection | ✅ SSE 日报订阅 + 权限探测 + 重连/心跳状态 |
| RagAssistant | /assistant | ✅ RAG 知识库问答 + 引文展示 |
| NotFound | /:pathMatch(.*)* | ✅ 404 兜底 |

---

## 4. API 端点清单

约 41 个端点（覆盖 Auth / Device / DeviceData / Alarm / OperationLog / User / Role / Site / AI / MCP 模块）+ Knife4j 文档 (/doc.html)。

---

## 5. 数据库

`reboot` 数据库，10 张表（user / role / user_role / site / user_site / device / device_data / alarm / operation_log / login_audit），由 Flyway 管理（V1 基线 + V3 CHECK 扩展 + V4 站点授权 + V5 用户安全状态 + V6 登录审计 + V7 alarm/role 审计字段 + V8 admin 密码更新 + V9 AI 操作日志类型 + V10 FUNCTION_CALL + V11 RAG 知识 + V12 MCP_SMOKE/MCP + V13 INSPECTION 操作日志类型 + V14 PUSH/SSE + V15 AUTO_ALARM + V16 活动告警聚合；V1 + V3-V16 共 15 个活动迁移文件），零 FK，软删除策略。V16 为 `alarm` 增加 `repeat_count`、`last_triggered_at`、生成列 `active_alarm_key` 与 `uk_alarm_active_device_type`（ADR 0038）。

---

## 6. 演进路线

> 唯一路线权威源：`backend/DAILY_ROADMAP.md`（本表与其严格对齐）。

| 阶段 | 周期 | Day | 内容 | 状态 |
|------|------|-----|------|:--:|
| Phase 1 | 第 1-3 周 | Day 1-21 | Java 复苏 | ✅ |
| Phase 2 | 第 4-6 周 | Day 22-42 | 项目 V1：CRUD / JWT / RBAC / 告警 / 前端 | ✅ v1.0 + Baseline V2.1 |
| Phase 3 | 第 7-9 周 | Day 43-63 | 中间件武装：Redis + RabbitMQ + Docker + Linux | ✅ 2026-08-16 |
| Phase 4 | 第 10-13 周 | Day 64-91 | AI 集成：DeepSeek → RAG → Agent/MCP → AI→业务闭环 → 前端 AI 工业化 | ✅ Day 66-91 完成，tag `v2.0-ai`；ADR 0021-0032 |
| Phase 5 | 第 14-16 周 | Day 92-112 | PLC + MQTT + 完整系统 | ✅ Day 92-112 完成，tag `v3.0-final`；路线后增强见 ADR 0038-0041 |
