# Decision 0031: AI 巡检日报推送架构设计（Day 85 前置）

| 属性       | 值                                                            |
| -------- | ------------------------------------------------------------ |
| **状态**   | ✅ 已采纳（架构边界冻结，Day 85 实现依据）                                    |
| **决策日期** | 2026-08-31                                                   |
| **决策者**  | hula0710 + AI 助手                                             |
| **关联**   | Day 85 / Week12 Exit Review / ADR 0026 / ADR 0029 / ADR 0030 |

> **本 ADR 仅冻结架构边界与选型，不含任何功能实现。**
> Week12 Exit Review 发现 ROADMAP Day 85「RabbitMQ → 前端」表述存在架构缺陷，
> 必须在 Day 85 编码前先明确分层，避免把后端消息中间件误当作浏览器推送通道。

## 1. 背景

Day 85 目标：AI 巡检日报（`McpInspectionAgentService.generate()` 产出
`AiInspectionReportResult`）生成后，自动通知前端展示。

当前 Week12 已具备的前半段链路：

```text
AI Agent（McpInspectionAgentService）
  ↓ 同步生成日报
（缺：日报投递到消息总线）
RabbitMQ（已就绪：exchange/queue/DLQ/delay）
（缺：日报 Consumer）
```

当前明显缺失的后半段：

```text
（缺：Report Consumer）
  ↓
（缺：Push Gateway —— 浏览器可达的常驻连接层）
（缺：SSE / WebSocket 传输）
（缺：Vue Client 接收与渲染）
```

即：**前半段（Agent→MQ→Consumer）有基础设施，后半段（Consumer→浏览器）完全空白**。
grep `SseEmitter|WebSocket|StompEndpoint|@MessageMapping` 在 backend 主代码零命中，
grep `EventSource|WebSocket|useWebSocket|stompClient` 在 frontend 零命中，证实后半段无任何存量。

## 2. 明确禁止架构

| 禁止方案                            | 禁止原因                                                                                                                |
| ------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| **RabbitMQ → Browser 直连**       | RabbitMQ 是服务端 AMQP 消息中间件；浏览器不是 RabbitMQ consumer，无原生 AMQP over WebSocket 客户端；即便启用 rabbitmq-web-stomp 插件也是额外组件且非推荐架构 |
| **浏览器直连 RabbitMQ TCP**          | AMQP 协议浏览器不可达；端口/协议不匹配；暴露 broker 凭证                                                                                 |
| **Push Gateway 直连 Agent**       | Agent 巡检是耗时操作（实测 6 轮 66 调用 \~39s），不能阻塞 HTTP 请求；必须经 MQ 异步化解耦                                                         |
| **MQ Consumer 直接持有 SseEmitter** | Consumer 水平扩展时 emitter 所在实例与订阅用户不在同一进程；必须经独立 Push Gateway 层（Redis pub/sub 或同类）做路由                                   |

> ROADMAP Day 85 原文「AI 生成的日报自动通过 RabbitMQ 推送到前端」表述不严谨：
> RabbitMQ 只能承担「Agent → Consumer」段，不能承担「Consumer → 浏览器」段。
> 本 ADR 修正该表述，明确六段分层。

## 3. 推荐架构（Day 85 实施依据）

```text
AI Agent（McpInspectionAgentService.generate）
  ↓ 投递 InspectionReportMessage
RabbitMQ（inspection.exchange / inspection.queue，durable + DLQ）
  ↓
Report Consumer（新建 InspectionReportConsumer）
  ↓ 解析 reportDate / siteIds / userId 范围，路由
Push Gateway（新建 InspectionPushGateway，常驻 Spring 进程）
  ↓ 维护 userId → SseEmitter 会话注册表
SSE / WebSocket（/api/push/inspection，JWT 鉴权）
  ↓
Vue Client（EventSource 订阅，断线自动重连）
```

### 3.1 各段职责边界

| 段               | 职责                                                           | 不做                          |
| --------------- | ------------------------------------------------------------ | --------------------------- |
| Agent           | 生成日报 `AiInspectionReportResult`，投递 `InspectionReportMessage` | 不直接 push 浏览器；不感知 SseEmitter |
| RabbitMQ        | 异步化解耦、持久化、DLQ、重试                                             | 不感知浏览器；不持有 emitter          |
| Report Consumer | 消费消息、解析路由范围、调用 Push Gateway                                  | 不直接持有 SseEmitter 全表（路由后即弃）  |
| Push Gateway    | 维护 `userId → SseEmitter` 注册表、鉴权建立、断线清理                       | 不生成日报内容；不做 MQ 重试            |
| SSE/WebSocket   | 单向推送日报 JSON 给已订阅浏览器                                          | 不做双向控制面（Day 85 范围内）         |
| Vue Client      | EventSource 订阅、断线重连、渲染                                       | 不直接调 MQ                     |

### 3.2 为什么必须有独立 Push Gateway 层

Report Consumer 在多副本部署时，emitter 所在实例与消费实例可能不同进程。
Push Gateway 通过 Redis pub/sub（或同类）让 Consumer 发布到 channel，
所有 Gateway 副本订阅并各自匹配本地 emitter，解决路由不可达问题。
Day 85 单副本可先用进程内直连，但接口契约必须按「可换 Redis pub/sub」设计。

## 4. SSE vs WebSocket 初步选择

| 维度        | SSE                       | WebSocket                                    |
| --------- | ------------------------- | -------------------------------------------- |
| 方向        | 单向（服务器→浏览器）               | 双向                                           |
| 浏览器原生     | ✅ EventSource 原生支持，无需 SDK | 需 WebSocket 客户端                              |
| 断线重连      | ✅ 浏览器自动重试                 | 需自行实现                                        |
| 协议        | HTTP/HTTPS（穿透反代友好）        | 升级握手，反代需额外配置                                 |
| 状态管理      | 简单（无客户端→服务端消息）            | 复杂（双向状态机）                                    |
| 适配场景      | 日报通知、告警推送、状态变更            | 实时协作、聊天、双向控制                                 |
| Spring 支持 | SseEmitter（Spring MVC 原生） | @MessageMapping + STOMP 或原生 WebSocketHandler |

### 4.1 Day 85 推荐方案：**SSE（SseEmitter）**

理由：

1. Day 85 场景是「日报单向推送」，无客户端→服务端控制面需求；
2. EventSource 浏览器原生断线重连，降低前端实现成本；
3. HTTP 协议穿透 Nginx 反代（项目已有 Nginx）零额外配置；
4. Spring MVC SseEmitter 原生支持，不引新依赖；
5. 后续若需双向（如用户触发重新巡检），可单开 WebSocket 通道，不污染 SSE 通道。

### 4.2 何时升级 WebSocket

* 需要客户端主动发起「立即重巡检」「按设备过滤」等控制指令；

* 需要双向心跳保活与低延迟交互。

* 升级时新增独立 `/ws` 通道，不替换 SSE 日报通道，保持职责单一。

## 5. 权限设计

### 5.1 目标

用户 A（仅 siteId=1 授权）**只能收到** siteId=1 的巡检日报；
不能收到 siteId=2 的日报，即使日报已推送到 Push Gateway。

### 5.2 鉴权与会话绑定

```text
浏览器
  ↓ GET /api/push/inspection（带 Authorization: Bearer <JWT>）
JWT Filter
  ↓ 解析 userId、roles，注入 request attribute
Push Gateway
  ↓ userId → SiteAccessService.getAccessibleSiteIds(userId)
  ↓ 建立 SseEmitter，注册到 userId → emitter 表
  ↓ 同时记录该 emitter 可订阅的 siteIds 集合
SSE 连接建立
```

### 5.3 路由匹配

Report Consumer 解析 `InspectionReportMessage.siteIds`，
调用 Push Gateway 路由：

| 路由策略               | 适用        | 实现                                                                                        |
| ------------------ | --------- | ----------------------------------------------------------------------------------------- |
| 进程内直连（Day 85 单副本）  | 开发 / 小规模  | Consumer 遍历本地 `userId→emitter` 表，按 emitter 的 siteIds 集合交集匹配                               |
| Redis pub/sub（多副本） | 生产 / 水平扩展 | Consumer publish 到 `inspection:site:{siteId}` channel，所有 Gateway 副本订阅自身用户有权的 channel，本地派发 |

### 5.4 全局 ADMIN 语义

沿用 ADR 0020：ADMIN 隐式全站点授权，可订阅所有 siteId 的日报；
非 ADMIN 仅订阅 `user_site` 授权范围内的 siteId。

### 5.5 安全边界

* SSE 端点必须走 JWT Filter（与 REST 同链路），禁止匿名建立 emitter；

* Push Gateway 不信任 Consumer 传入的 userId，只认 emitter 建立时绑定的 userId；

* 跨站点隔离失败 = P0 缺陷（与 ADR 0020 站点作用域同等级别）。

## 6. 失败策略

| 故障点                         | 现象                            | 策略                                                                                 |
| --------------------------- | ----------------------------- | ---------------------------------------------------------------------------------- |
| **Vue 断线**                  | EventSource onerror / onclose | 浏览器原生自动重连（指数退避）；Gateway 检测到 emitter complete/timeout/error 后从注册表移除，避免泄漏            |
| **Consumer 处理失败**           | 消费抛异常                         | Spring AMQP retry + DLQ（复用 alarm.dlx 模式）；消息 durable，broker 重启不丢                    |
| **Redis 异常**（多副本模式）         | pub/sub publish/subscribe 失败  | 降级到本地 emitter 直发（仅影响跨副本用户）；Redis 恢复后自动恢复；日志 WARN 不中断 Consumer                      |
| **RabbitMQ 异常**             | broker 不可达                    | Producer（Agent 投递）侧 catch + 降级：日志记录待发日报，可手动重投；不阻塞 Agent 主流程                        |
| **Push Gateway emitter 泄漏** | 长连接未清理                        | SseEmitter 设置 timeout（如 30min）；定时任务扫描清理超时 emitter；JVM shutdown hook 关闭全部 emitter   |
| **重复推送**                    | Consumer 重试导致同一日报推多次          | 幂等键：`inspection:{reportDate}:{siteId}`，Redis SETNX 去重（TTL 24h）；前端按 reportDate 去重渲染 |

### 6.1 消息持久化与可靠性

* `InspectionReportMessage` 必须可序列化（Jackson JSON）；

* `inspection.queue` durable=true；

* Consumer 手动 ACK（与 AlarmConsumer 同模式），处理成功才 ack；

* DLQ 消息可人工排查重投。

## 7. 影响与验证

* **代码**（Day 85 实施，本 ADR 不实现）：

  * 新增 `InspectionReportMessage` DTO；

  * 新增 `InspectionReportProducer`（Agent 侧投递）；

  * 新增 `InspectionReportConsumer`；

  * 新增 `InspectionPushGateway` + `SseEmitterRegistry`；

  * 新增 `InspectionPushController`（`/api/push/inspection`，JWT）；

  * MQConfig 增 `inspection.exchange` / `inspection.queue` / DLQ 绑定。

* **测试**：emitter 注册/清理、site 隔离、断线重连、幂等去重、DLQ 路径；

* **文档**：Application-Architecture 增推送链路图；AGENTS §3 状态同步。

## 8. 风险

| 风险                   | 缓解                                                                |
| -------------------- | ----------------------------------------------------------------- |
| 单副本 SSE emitter 内存泄漏 | timeout + 定时清理 + shutdown hook；Day 85 先单副本，多副本前补 Redis pub/sub    |
| 跨副本路由不可达             | Day 85 单副本可接受；生产前必须切 Redis pub/sub 策略                             |
| 日报生成慢阻塞投递            | Agent 投递 MQ 是异步非阻塞；Consumer 独立线程池                                 |
| 公网暴露 SSE 端点          | 必须走 JWT Filter；不与 MCP 通道（内网可信）混用鉴权模型                              |
| 站点隔离失败               | Push Gateway 只认 emitter 绑定 userId 的 siteIds；Consumer 不越权指定 userId |

## 9. 与既有架构对齐

* 复用 `ApiResponse<T>`（dev.reboot.dto）：SSE 推送 payload 用同一封装；

* 复用 `SiteAccessService`（ADR 0020）：站点作用域判定；

* 复用 `MQConfig` 模式（alarm.exchange/DLQ/delay）：inspection 队列同模式；

* 复用 `@OperationLog`（ADR 0023/0030）：Consumer 消费 + Push 建连需审计（CONSUME/PUSH 类型待 Day 85 Flyway 迁移加 CHECK）；

* 复用 JWT Filter + AuthInterceptor：SSE 端点鉴权不另建。

## 10. 不在 Day 85 范围

* 不实现 MQTT/PLC 侧推送（Phase 5）；

* 不实现 WebSocket 双向控制面（待需求驱动）；

* 不实现 OAuth/多租户 MCP 推送（与 ADR 0027 MCP 1.1 演进同周期）；

* 不替换 RabbitMQ 为 Kafka/Pulsar（技术栈锁定，AGENTS §5）。

***

> 最后更新：2026-08-31 | 维护者：AI 助手 + hula0710
> 路径说明：本文件遵循项目约定置于 `docs/decision-log/`（与 ADR 0026-0030 同目录），
> 而非任务原文 `docs/adr/`，以保持 AGENTS §2 文档索引一致性。

