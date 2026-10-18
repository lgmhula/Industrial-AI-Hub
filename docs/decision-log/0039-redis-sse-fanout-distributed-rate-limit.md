# Decision 0039: Redis SSE Fan-out 与分布式限流

| 属性 | 值 |
|------|-----|
| **状态** | 已落地 |
| **决策日期** | 2026-10-18 |
| **决策者** | hula0710 + AI 助手 |
| **关联** | ADR 0031 / ADR 0037 |

## 1. 背景

原实现中，`SseEmitterRegistry` 和 Guava 令牌桶都保存在单个 JVM：

- RabbitMQ 竞争消费后只有拿到幂等键的实例会推送，但浏览器 SSE 连接可能挂在另一个实例；
- 通用限流和 AI 限流按实例计数，多实例部署时总额度会随副本数放大；
- 拒绝日志在 Redis 模式下也需要跨实例收敛，避免每个实例重复刷日志。

本项目不承诺生产级高可用，但需要消除“所有能力只能绑定一台后端”的架构阻塞，并保留默认可离线运行。

## 2. 决策

### 2.1 SSE Fan-out

1. `InspectionReportConsumer` 取得 RabbitMQ 消息和 Redis 幂等结果后，通过
   `InspectionPushPubSub.publish()` 发布到 Redis channel
   `industrial-ai-hub:inspection-report`。
2. 每个后端实例订阅该 channel，只向本机 `SseEmitterRegistry` 中的 emitter 推送。
3. 发布无订阅者或 Redis 异常时回退本机 Push Gateway，优先保留单实例可用性。
4. SSE 建连前增加普通 GET 权限探测端点 `GET /api/push/inspection/access`，只校验 JWT、角色和站点范围，
   不创建 emitter。连接建立后发送 SSE `connected` comment 首帧，前端据此切换“已连接”状态。
5. Emitter 回调按 emitter 实例条件删除，避免旧连接迟到回调误删新连接。

该改造降低单机连接绑定风险并增加跨实例验证能力，不代表已经完成生产级高可用、跨主机压测或故障切换 SLA。

### 2.2 限流

新增 `RateLimitStore`，提供两种模式：

- `local`：默认模式，沿用 Guava 令牌桶；
- `redis`：用 Redis Lua 脚本实现基于 Redis `TIME` 的原子令牌桶，多实例共享额度。

Redis 不可用时本次请求立即回退本地桶，并做 5 秒告警节流。通用限流与 AI 限流统一复用该 Store。
通用限流顺序固定为：

1. 先尝试 identity token（登录用户 ID；未登录时为客户端 IP）；
2. identity 通过后，再尝试 path token；
3. 两层都通过才放行。

path 拒绝时 identity token 已经被消费，这是有意的双层设计：它避免恶意客户端持续冲击全站 path 配额，
同时保留 identity 层对单客户端的独立约束。代价是 path 拒绝会额外消耗一次 identity 额度。

### 2.3 拒绝日志

- `local` 模式继续使用进程内窗口节流；
- `redis` 模式使用 Redis `INCR + PEXPIRE` 收敛为每 path 每窗口最多一行；
- 日志模式与令牌桶模式解耦，Redis 日志键失败不影响限流判定。

## 3. 备选方案

| 方案 | 未采纳原因 |
|------|------------|
| RabbitMQ fanout 让所有后端消费 | 会破坏现有工作队列语义，且重复消费与幂等边界更复杂 |
| 每个实例各自调用 AI/推送 | 无法保证只生成一次日报 |
| Spring Session + Sticky Session | 只解决连接粘滞，不解决消费实例与连接实例不一致 |
| 限流改用网关单点 | 当前 Compose 没有统一生产网关，且本地开发仍需独立可运行 |
| Redis 异常直接拒绝请求 | 限流组件不应成为业务单点，故选择回退本地 |

## 4. 后果与风险

- Redis 成为多实例协作的运行时依赖；失效时退化为单实例语义，需要监控 WARN 与 Redis 可用性。
- SSE fan-out 只覆盖本机 emitter，用户连接仍绑定某个后端实例；实例故障后依赖浏览器重连到其他实例。
- Redis 限流与本地限流的时间/容量实现不同，切换模式时需要观察额度表现。
- 日志节流与令牌桶都使用 Redis，网络抖动会增加请求延迟；当前失败路径保持快速回退。

## 5. 验证证据

- `InspectionPushChainIT`：RabbitMQ/Redis 真实链路、幂等、fan-out、权限探测与 SSE 连接。
- `InspectionPushPubSubTest`、`SseEmitterRegistryTest`：发布失败回退、首帧、旧回调条件删除。
- `RateLimitStoreRedisIT`：真实 Redis 下多实例共享额度、过期与回退。
- `RateLimitInterceptorTest`：identity/path 双层顺序、429、日志节流。
- 浏览器 SSE E2E 在本轮收口验证并记录截图。

## 6. 明确边界

本 ADR 不宣称“多实例生产级完成”。仍需独立验证：跨主机 Redis/网络分区、实例滚动升级、限流容量压测、
监控告警和多副本部署编排。
