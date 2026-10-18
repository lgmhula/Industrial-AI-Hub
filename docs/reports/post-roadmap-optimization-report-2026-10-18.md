# v3.0-final 后增强报告

> 日期：2026-10-18
> 范围：Day 112 路线收口后的独立增强分支
> 历史基线：[`v3.0-final-release-report.md`](v3.0-final-release-report.md)

---

## 一、结论与边界

`v3.0-final` 作为 Day 1-112 的历史检查点保持不变。本轮增强位于
`codex/post-roadmap-optimization` 分支，目标是从单机演示能力向多实例可演进架构前进一步，
同时保留默认离线启动路径。

本轮可以确认：

- 活动告警由数据库唯一索引与原子 upsert 合并，持续超限不再按每条遥测新增活动告警；
- SSE 推送增加了 Redis Pub/Sub fan-out、连接首帧与 15 秒心跳；
- 通用限流和 AI 限流支持 `local|redis` 两种 Store，Redis 模式使用 Lua 原子令牌桶；
- RAG 可选接入 Qdrant 与 OpenAI-compatible embedding，默认仍使用本地 hash + simple store；
- 前端具备 Vue 静态构建 + Nginx 容器，观测栈可选接入 Loki/Promtail/Grafana；
- 后端全量回归为 **403/403**，61 个测试类，`0 failures / 0 errors / 0 skipped`。

本轮**不宣称生产级高可用已经完成**。跨主机故障切换、滚动升级、网络分区演练、容量 SLA、
Redis 故障下的多副本一致性、长期日志存储与审计承诺仍未验收。

---

## 二、历史基线与本轮口径

| 维度 | `v3.0-final` 历史事实 | 本轮增强结果 |
|------|----------------------|--------------|
| 后端测试 | 375 tests / 57 suites | **403 tests / 61 suites** |
| 数据库 Schema | V15 | **V16** |
| 活动迁移 | 14 个 | **15 个（V1 + V3-V16）** |
| Compose | v1.7，core 6 / full 15 | **v1.8，core 7 / observability 10 / full 20** |
| ADR | 0001-0037 | **0001-0041** |
| SSE | 进程内 emitter registry | **Redis fan-out 基础能力 + 本机 emitter** |
| 限流 | 进程内 Guava 桶 | **local/redis 双模，identity + path 双层桶** |
| RAG | simple + local hash | **可选 Qdrant + OpenAI-compatible embedding** |
| 前端交付 | Vite dev server | **Vite 保留；新增 Nginx 静态容器** |
| 观测 | Prometheus + 文件日志 | **新增可选 Loki/Promtail/Grafana** |

历史报告中的 375/375、V15、14 个迁移和 core 6 / full 15 不改写；本轮新增数字单独记录，
避免把 tag 后能力倒灌进历史 Release Gate。

---

## 三、增强内容

### 3.1 Flyway V16 活动告警聚合（ADR 0038）

- `alarm` 新增 `repeat_count`、`last_triggered_at` 与生成列 `active_alarm_key`；
- 活动状态（`status IN (0,1)`）按 `device_id:alarm_type` 生成唯一键，解决后为 `NULL`；
- `uk_alarm_active_device_type` 由数据库兜底并发去重；
- `AlarmService.createAlarm()` 使用 `INSERT ... ON DUPLICATE KEY UPDATE` 原子合并；
- 迁移先归并存量重复活动告警，保留历史记录，再建立唯一索引；
- API 与前端告警列表暴露重复次数和最近触发时间。

### 3.2 Redis SSE Fan-out 与分布式限流（ADR 0039）

- RabbitMQ 消费实例取得幂等结果后，将巡检报告发布到 Redis channel；
- 每个后端实例订阅 channel，只向本机 `SseEmitterRegistry` 推送；
- 发布失败或无订阅者时回退本机 Push Gateway；
- SSE 建连前增加权限探测端点，建连后发送 `connected` comment 首帧；
- 15 秒 comment 心跳维持代理与浏览器连接；
- emitter 回调按实例条件删除，避免旧连接迟到回调误删新连接；
- `RateLimitStore` 支持 `local|redis`，Redis 模式使用 Lua + Redis `TIME` 原子令牌桶；
- 通用限流按 identity（用户 ID / IP）与 path 双层判定，两个桶都通过才放行；
- Redis 异常时本次请求回退本地桶并节流 WARN，避免限流组件成为业务单点。

### 3.3 Qdrant 与 OpenAI-compatible Embedding（ADR 0040）

- `rag.vector-store=simple|qdrant`；
- `rag.embedding.provider=hash|openai-compatible`；
- Qdrant REST 实现 add、similaritySearch、size 与 collection 维度检查；
- embedding 实现使用 JDK `HttpClient`，校验 API Key、响应条数、索引和维度；
- Qdrant 位于 `full` profile，默认 `core` 不依赖外部向量库；
- 默认启动仍为 `simple + hash`，离线演示行为不变。

### 3.4 前端容器、Loki 与 Grafana（ADR 0041）

- `frontend/Dockerfile` 使用 Node 构建、Nginx 运行静态产物；
- `/api/` 反向代理到 `backend:8080`，`/api/push/` 关闭代理缓冲以支持 SSE；
- `/healthz` 供容器健康检查；
- Vite `5173` 继续作为开发热更入口；
- Loki 接收 Promtail 从 `backend-logs` volume 采集的日志；
- Grafana provisioning 同时配置 Loki 与 Prometheus 数据源和 dashboard；
- 观测栈位于 `observability` 或 `full` profile，不增加默认 `core` 启动负担。

---

## 四、验证矩阵

### 4.1 自动化测试

| 验证项 | 结果 | 说明 |
|--------|------|------|
| 后端全量测试 | **403 / 403** | `./mvnw clean test` |
| 测试类 | **61** | Surefire XML 汇总 |
| 失败 / 错误 / 跳过 | **0 / 0 / 0** | 全绿 |
| Flyway V16 | 通过 | 真实 MySQL 迁移与结构校验 |
| Redis SSE / 限流 | 通过 | 单测 + 真实 Redis IT |
| Qdrant 可选实现 | 通过 | REST 契约单测 + 容器 IT |
| 活动告警迁移 | 通过 | 真实 MySQL V16 IT |

关键测试覆盖：

- `MySqlMigrationV16IT`：存量归并、唯一索引、upsert 累加、解决后新周期；
- `InspectionPushChainIT`：RabbitMQ/Redis 真实链路、幂等、fan-out、权限探测与 SSE；
- `InspectionPushPubSubTest`：发布与回退；
- `SseEmitterRegistryTest`：首帧、心跳与条件删除边界；
- `RateLimitStoreRedisIT`：真实 Redis 跨实例共享额度、过期与回退；
- `RateLimitInterceptorTest`：identity/path 双层顺序与 429；
- `QdrantVectorStoreTest` / `QdrantVectorStoreIT`：向量库契约与真实容器链路；
- `OpenAiCompatibleEmbeddingModelTest`：请求、排序、错误状态与维度校验。

### 4.2 数据库

真实 MySQL 核对结果：

| 项目 | 实测值 |
|------|--------|
| Flyway Schema Version | **V16** |
| 成功迁移 | **15**（V1 + V3-V16） |
| 业务表 | **10** |
| 列 | **85** |
| CHECK 约束 | **8** |
| 索引 | **42** |
| `alarm` 聚合字段 | `repeat_count` / `last_triggered_at` / `active_alarm_key` 均存在 |

`user_role` 为 `id / user_id / role_id` 三列；此前文档中的 4 列和总列数 86 已纠正为 3 列和 85。

### 4.3 Compose 与构建

| 验证项 | 实测值 |
|--------|--------|
| 默认 `core` | **7 服务** |
| `observability` profile | **10 服务** |
| `full` profile | **20 服务** |
| Compose 配置解析 | `docker compose config --quiet` 通过 |
| 后端镜像 | `docker compose build backend` 通过 |
| 前端镜像 | `docker compose build frontend` 通过 |
| 前端生产构建 | `npm run build` 通过 |

MinIO 本地固定镜像 digest：

```text
sha256:14cea493d9a34af32f524e538b8346cf79f3321eff8e708c1e2960462bd8936e
```

### 4.4 运行态健康检查

| 入口 | 期望 |
|------|------|
| `http://127.0.0.1:8080/actuator/health` | `UP` |
| `http://localhost:3000`（前端容器） | HTTP 200 |
| `http://localhost:3001`（Grafana） | HTTP 200 / 登录页 |
| `http://localhost:9090`（Prometheus） | HTTP 200 |
| `http://127.0.0.1:6333/readyz`（Qdrant） | HTTP 200 |

### 4.5 浏览器 SSE

2026-10-18 17:28-17:31 使用 Playwright 对真实浏览器与容器后端完成验证：

| 验证项 | 实测结果 |
|--------|----------|
| 登录并进入 `/inspection` | SSE 建立，收到 `connected` 首帧，页面显示“已连接” |
| 停留超过一个心跳周期 | 连接保持 |
| 离开页面 | 后端在两个测试周期分别记录正常会话移除，当前在线数回到 0 |
| 重新进入页面 | 浏览器重新建立 SSE，当前在线数回到 1 |
| 异常日志 | 未出现 `AsyncRequestNotUsableException`、Broken pipe、ERROR 或异常处理器二次失败 |

日志只包含正常 SSE 注册/移除 INFO；启动阶段仅保留既有的 Logback
watchable URL WARN 与 Flyway MySQL 8.4 版本提示，与 SSE 断开路径无关。

---

## 五、剩余边界与后续优先级

以下项目没有在本轮宣告完成：

1. **跨主机 HA**：未验证多主机 Redis、RabbitMQ、MySQL 和生产负载均衡；
2. **故障切换**：未做实例宕机、Redis 主从切换、网络分区和消息积压恢复演练；
3. **滚动升级**：未形成兼容性窗口、双版本并行和数据库回滚策略；
4. **容量 SLA**：未进行多副本 SSE 连接数、Redis 限流吞吐和长稳测试；
5. **日志长期存储**：Loki 仅作为可选观测，不等同于审计级归档与保留承诺；
6. **向量库运维**：Qdrant 为可选验证能力，未做重建、备份、迁移和容量规划；
7. **前端生产部署**：Nginx 容器已验证基础代理与 SSE，但未覆盖 TLS、WAF、CDN 和多区域发布。

准确表述应为：**多实例 SSE / Redis 限流基础能力已落地**，不能写成
**生产级高可用已完成**。

---

## 六、复现入口

```bash
cd backend
./mvnw clean test

cd ../frontend
npm run build

cd ..
docker compose config --quiet
docker compose build backend frontend
docker compose up -d backend frontend

curl -fsS http://127.0.0.1:8080/actuator/health
curl -sS -o /dev/null -w '%{http_code}\n' http://localhost:3000
curl -sS -o /dev/null -w '%{http_code}\n' http://localhost:9090
curl --noproxy '*' -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:6333/readyz
```

完整部署与 profile 说明见
[`Infrastructure-Baseline.md`](../Architecture/Infrastructure-Baseline.md)、
[`SETUP.md`](../SETUP.md) 与 [`deploy-runbook-new-device.md`](deploy-runbook-new-device.md)。
