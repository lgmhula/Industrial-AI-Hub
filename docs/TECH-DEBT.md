# 技术债务清单（Technical Debt）

> **SSOT**：本文档是技术债务的唯一权威清单（迁移自 `backend/REVIEW/Phase2-Summary.md` §四，并持续更新）。
> **最后更新**：2026-10-18（路线后增强：TD-035 与六项演进候选结清，生产 HA 边界继续单列）

---

## 一、已解决（可追溯）

| # | 问题 | 解决于 |
|:--:|------|------|
| 1 | JwtUtils static 工具类无法配置 | Phase 3-A T4（static→instance + profile-aware） |
| 2 | `listAll()` 返回实体含密码字段 | Phase 2 后（改返回 VO；现仅 `DeviceService.listAll()` 返回 `DeviceVO`） |
| 3 | 注册无频率限制 | RateLimitInterceptor（URI 级限流，覆盖 `/api/**`） |
| 5 | 缺少全局日志 traceId（TD-005） | 2026-10-13（Day 101：`TraceIdFilter` MDC + `X-Trace-Id` 透传/回传 + `logback-spring.xml` pattern；Day 102 高压复验 142,771 样本零回归） |
| 11 | compose.yml 无 profiles 分组 | Phase 3-A T6（`full` profile 分级启动） |
| 12 | 无应用层 Dockerfile | Phase 3（multi-stage + non-root + HEALTHCHECK） |
| 16 | 种子数据两份重复（02_test_data.sql vs seed_test_data.sql） | 2026-08-16 合并为唯一 SSOT `seed_test_data.sql` |
| 17 | `mysql/init/01_init.sql` 符号链接（Windows 不兼容） | 2026-08-16 改为 compose 直接挂载 `sql/*.sql` |
| 18 | Nacos/MinIO/ES 孤儿能力（无路线落点） | 2026-08-16 决策：Nacos=预留不启动、MinIO=Phase 5、ES=Day 101 ELK |
| 21 | README/AGENTS 状态滞后（~3 周） | 2026-08-16 漂移纠正 |
| 22 | Phase 3 决策无 ADR | 2026-08-16 新增 ADR 0014 |
| 23 | DAILY_ROADMAP Phase 2 复选框未勾 | 2026-08-16 勾选 |
| 28 | AI 端点无操作日志（TD-028） | 2026-08-28（Day 67：Flyway V9 扩展 `CHAT/SUMMARY/DIAGNOSE` + `target_type=AI`，AiController 三端点补 `@OperationLog`） |
| 32 | OperationLogAspect 失败场景 `{ret}` 输出 "null"（TD-032） | 2026-08-29（`formatResult` 接收 `Throwable error`，失败时替换为异常消息而非 "null"；`around` 捕获异常传入 `recordLog`） |
| 33 | DeviceStatusAgentService 校验顺序：AI 可用性检查先于资源校验（TD-033） | 2026-08-29（调整为 requireDevice → assertSiteAccess → ensureAvailable，避免 503 掩盖 404/403） |
| 34 | 限流拒绝日志放大（TD-034） | 2026-10-13（Day 103：拒绝日志按窗口节流 + 汇总抑制条数；同强度突发 **137,568 行 → 6 行**、日志增量 **23.2MB → 1.67MB（−92.8%）**，且放行数/慢样本数逐项对齐证明限流策略未变；ADR 0037） |
| 36 | 缺 HTTP 延迟直方图（TD-036） | 2026-10-13（Day 103：开启 `management.metrics.distribution.percentiles-histogram.http.server.requests`；运行时 `http_server_requests_seconds_bucket` 系列 **0 → 69**，Prometheus 可算 p95/p99） |
| 37 | 写路径尾部延迟不稳定（TD-037） | 2026-10-13（Day 103：定位为**环境噪声而非 SQL/连接池**——2.5× 速率（50.2 req/s）写入 1,506 次 100% 成功且 0 条 >100ms 慢查询（带对照组）、Hikari acquire max 8ms / pending 0、GC pause max 27ms，读组同期 max 136ms；进一步压缩需移出压测机 + 分段埋点，非当前债务） |
| 38 | `HttpMessageNotReadableException`（畸形 JSON）走兜底 `@ExceptionHandler(Exception)`（TD-038） | 2026-10-14（Day 104：新增显式处理器 → **400** + WARN 日志，不再污染 error.log；standalone MockMvc 3 用例锁定语义；容器化实测畸形 JSON 返回 `{"code":400,"message":"请求体格式错误…"}`） |
| 35 | 限流桶全局单桶（TD-035）：key 只有 path，非 per-user/IP | 2026-10-18（ADR 0039：`RateLimitStore` 支持 local/redis，Redis Lua 原子令牌桶；通用限流改为 identity + path 双层桶，AI 限流复用同一 Store；默认配置仍可无 Redis 运行） |
| 39 | 持续超限时每条遥测创建一条活动告警（报警风暴） | 2026-10-18（ADR 0038：Flyway V16 增加 `repeat_count` / `last_triggered_at` / 活动周期生成列唯一索引；`AlarmService` 原子 upsert，resolved 后重新开启周期；存量重复项迁移时归并保留审计） |
| 40 | SSE 浏览器生命周期只有服务端 IT，缺真实页面证据 | 2026-10-18（ADR 0039：Playwright 验证 `/inspection` 建连、停留、离开和重连；后端正常输出 connected/disconnected，无 `AsyncRequestNotUsableException`） |
| 41 | RabbitMQ 消费实例与浏览器 SSE 连接实例可能不同；限流/日志节流 per-JVM | 2026-10-18（ADR 0039：Redis Pub/Sub fan-out 到本机 emitter；`RATE_LIMIT_MODE=redis` 共享令牌桶与跨实例日志节流；Redis 异常回退 local，边界见 §四） |
| 42 | 前端只有 Vite dev server 运行路径，未进入 Compose | 2026-10-18（ADR 0041：Vue 静态构建 + Nginx 运行镜像；`frontend` 加入 core，`/api/` 反代后端，`/api/push/` 关闭缓冲并设置长连接超时） |
| 43 | 缺 Grafana、集中日志与可查询运行视图 | 2026-10-18（ADR 0041：Loki + Promtail + Grafana 进入 observability/full profile；Promtail 采集 `backend-logs`，预置 Prometheus/Loki 数据源与 runtime dashboard） |
| 44 | RAG 只能使用内存 SimpleVectorStore + hash embedding | 2026-10-18（ADR 0040：新增可选 `QdrantVectorStore` 与 OpenAI-compatible `/embeddings`；默认仍为 `simple + hash`，外部模式需显式配置） |

---

## 二、未解决（按优先级）

### P1（当前优先级）

| # | 问题 | 影响 | 建议 |
|:--:|------|------|------|
| 4 | 无 Token 刷新/登出机制 | 24h 过期后必须重新登录 | Phase 4 前补 refresh token 或缩短说明 |
| 19 | Redis 三客户端并存（jedis + spring-data-redis + redisson） | 依赖冗余、连接池复杂 | 收敛为 1-2 个客户端（jedis 仅 Day 43 练习用） |
| 24 | `DeepSeekProperties.apiKey` 空默认 + 启动期无 warn（ADR 0021 明示 opt-in 例外） | 误开启时缺 Key 只能在请求期暴露 | 补 `enabled=true && apiKey.isBlank()` 启动期 WARN 日志 |

### P2（渐进）

| # | 问题 | 影响 | 建议 |
|:--:|------|------|------|
| 6 | Entity 手写 getter/setter | 样板代码多 | 是否引入 Lombok（需推翻 ADR 0003 决策，先讨论） |
| 7 | 状态字段魔法数字（0/1/2） | 可读性差 | 引入 Status 枚举 |
| 8 | `user` 表名是 MySQL 保留字 | 潜在解析风险 | 重命名表或统一加反引号（现已有反引号） |
| 9 | 零外键约束 | 可能孤儿数据 | 应用层保证 + 定期审计脚本 |
| 10 | Swagger 示例值注解不丰富 | API 文档可读性 | 补 `@Schema`/`@ExampleObject` |
| 25 | AI 业务 DTO 枚举字段为纯 String（priority/healthLevel） | 前端映射/校验依赖约定 | 引入枚举或 `@Schema(allowableValues)` |
无新增 P2 债务。

### P3（后续迭代）

| # | 问题 | 影响 | 建议 |
|:--:|------|------|------|
| 13 | ECharts 打包 687KB 无分包 | 首屏加载慢 | 按需引入 + 路由懒加载 |
| 14 | 报警规则硬编码 AlarmRuleConfig | 不可动态调整 | 规则入库 + 动态加载 |
| 15 | DeviceData 时间范围查询无分页 | 大数据量内存风险 | 加时间分页/聚合下推 |
| 26 | AI DTO 缺 `@Schema` 注解 | Knife4j 文档可读性一般 | 补注解（与其余模块 DTO 风格一致） |

---

## 三、遗留决策待确认

| 项 | 状态 |
|----|------|
| Elasticsearch 是否真正集成 | 当前集中日志采用可选 Loki/Promtail/Grafana（ADR 0041）；ES 8.17 保留为 full-profile 预留，不宣称 ELK 已集成 |
| Nacos 是否彻底移除 | 已决策为「预留基础设施：不纳入路线/不启动/无业务依赖」，仍留在 compose `full` profile |

---

## 四、生产化边界（未完成，不等于已解决）

以下能力不属于“多实例基础能力已落地”的完成范围：

| 边界 | 当前事实 | 后续验证 |
|------|----------|----------|
| 跨主机高可用与故障切换 | Redis Pub/Sub、共享限流和数据库唯一键已降低单机绑定风险，但未做多主机故障演练 | 多后端 + Redis/MySQL/MQTT 故障注入、RTO/RPO 目标 |
| 滚动升级 | Compose 可重建容器，但未验证多副本滚动发布、会话排空和版本兼容 | 反向代理健康摘除、双版本共存、数据库前后向兼容 |
| 网络分区 | 未演练 Redis/RabbitMQ/MySQL/EMQX 分区下的降级、恢复与补偿 | 分区矩阵、超时/重试策略、脑裂和恢复脚本 |
| 容量与 SLA/SLO | 仅有单机压测与限流行为证据，不构成线上容量承诺 | 独立压测机、长稳压测、SLO、容量水位和告警阈值 |
| 日志长期存储与审计 | Loki/文件滚动满足当前排障，未承诺合规留存、不可篡改与长期审计 | 保留周期、对象存储、访问控制、归档与成本评估 |
| 多设备规模 | 真机验证为 1 台 ESP32-S3，模拟器压力测试不等同量产接入 | 多节点弱网、重连风暴、固件升级和设备生命周期验证 |

---

> 维护约定：任何 Agent 引入新债务 → 追加本表；解决债务 → 从「未解决」移入「已解决」并标注日期。
