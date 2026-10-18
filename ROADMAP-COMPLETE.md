# Industrial AI Hub 路线图完成报告

- **状态：** 官方 112 天路线图完成
- **完成日期：** 2026-10-18
- **最终版本：** `v3.0-final`
- **范围：** Day 1-112
- **事实原则：** 只记录仓库、测试、报告或真机实测可追溯的结论

---

## 一、完成结论

Industrial AI Hub 已完成从 Java 基础到工业 AI 设备管理平台的完整学习与交付闭环：

- Day 1-21：Java、MySQL、MyBatis、Spring Boot 基础复苏；
- Day 22-65：Industrial AI Hub V1、中间件、基础设施和工程质量治理；
- Day 66-91：DeepSeek、Spring AI、RAG、Agent、MCP、SSE 与 AI 业务闭环；
- Day 92-112：PLC/MQTT、EMQX、真实 ESP32 硬件、观测、压测、编排和面试发布材料。

最终交付包含可运行的模块化单体、Vue 3 前端、真实硬件双向链路、AI 业务能力、测试与性能证据、Docker Compose 完整编排，以及可复用的架构决策和故障排查文档。

`v3.0-final` 是 **112 天学习路线终点**，不是生产级高可用、分布式扩容或线上容量承诺。

> Day 113-120 不属于当前官方路线图，不应把后续设想写成已完成任务。
>
> 本报告第二至七章冻结 Day 112 / `v3.0-final` 的历史口径；tag 之后完成的增强统一记录在第八章，不移动 tag，也不回写 375/V15 的历史验收事实。

---

## 二、阶段完成情况

| 阶段 | 路线范围 | 完成结果 | 主要产物 |
|------|----------|----------|----------|
| Phase 1 | Day 1-21 | 完成 | Java 基础、集合、异常、IO、MySQL、JDBC、MyBatis、Spring Boot 入门 |
| Phase 2 | Day 22-42 | 完成 | Industrial AI Hub V1：认证、RBAC、设备、遥测、报警、日志、前端页面 |
| Phase 3 | Day 43-65 | 完成 | Redis、RabbitMQ、Docker、Linux、质量审计、基础设施稳定化 |
| Phase 4 | Day 66-91 | 完成，tag `v2.0-ai` | DeepSeek、Spring AI、RAG、Agent、MCP、SSE、AI 自动报警 |
| Phase 5 | Day 92-112 | 完成，tag `v3.0-final` | PLC/MQTT、EMQX、真实硬件、观测、压测、编排、面试与发布 |

---

## 三、最终交付

### 3.1 业务后端

- Java 25 + Spring Boot 3.5 + MyBatis 分层架构；
- JWT、BCrypt、ADMIN/OPERATOR/VIEWER RBAC 和站点资源作用域；
- 设备、遥测、报警、操作日志、用户、角色、站点等 9 个业务模块；
- 8 条报警规则、报警确认/解决工作流和 AI 摘要；
- 统一响应、参数校验、全局异常处理和 AOP 操作审计；
- 通用限流与 AI 独立限流。

### 3.2 IoT 与消息链路

- EMQX 5.8.9 + Eclipse Paho MQTT 客户端；
- ESP32-S3 + DHT22 以 2 秒周期发布 QoS1 telemetry；
- Redis SETNX 字段级幂等，键为 `mqtt:{deviceId}:{ts}:{dataType}`，TTL 24 小时；
- 温度/湿度规则命中后通过 command Topic 下发继电器控制；
- Redis 5 秒下行节流 + 固件吸合 3 秒自动复位；
- RabbitMQ 工作队列、Fanout、DLQ、手动 ACK 和 Redis 消费幂等；
- Java `PlcSimulator` 与真实 ESP32 使用同一 Topic/Payload 契约。

### 3.3 AI 与 Agent

- DeepSeek opt-in 接入，未启用时 AI 返回 503，核心业务不受影响；
- 5 项业务价值：告警摘要、设备健康诊断、RAG 问答、Agent 巡检日报、AI 巡检自动报警；
- Spring AI `ChatClient` + `PromptTemplate` + Function Calling；
- 普通 Agent 3 轮、巡检 Agent 最多 6 轮硬限制；
- MCP Server/Client 和 7 个只读工具；
- Agent 日报经 RabbitMQ、Redis 幂等、SSE Gateway 推送浏览器。

### 3.4 数据、安全与观测

- MySQL 8.4，Flyway Schema V15；
- V1 + V3-V15 共 14 个活动迁移，10 张表；
- Redis Stack 缓存、锁、幂等和节流；
- Actuator + Micrometer + Prometheus JVM/HTTP/三条 MQTT 业务指标；
- `X-Trace-Id` 请求头透传与回传、MDC 日志关联、滚动归档和 ERROR 独立文件；
- Docker Compose v1.7：默认 6 服务，full profile 15 服务；
- backend 日志卷 `backend-logs` 和向量库数据卷 `backend-data` 持久化。

### 3.5 前端与交付

- Vue 3 + Vite + Element Plus + ECharts；
- 登录、仪表盘、设备、报警、日志、用户、角色、AI 助手和巡检日报页面；
- SSE 浏览器订阅与断线重连；
- MIT License、README、从零复刻指南、部署 Runbook 和工程踩坑手册。

---

## 四、质量与实测证据

| 指标 | 最终口径 | 证据 |
|------|----------|------|
| 官方路线 | 112 / 112 天 | `backend/DAILY_ROADMAP.md` |
| 后端测试 | 375 / 375，0 failures / 0 errors / 0 skipped | Day 112 全量回归 |
| 测试类 | 57 | Surefire 汇总 |
| 前端构建 | 1.12s，0 errors | Day 112 `npm run build` |
| 架构决策 | 37 份 ADR，编号 0001-0037 | `docs/decision-log/` |
| 数据库 | Schema V15，14 个活动迁移，10 张表 | Flyway / ADR 0012 |
| 编排 | 默认 6 服务；full profile 15 / 15 healthy | Day 104、Day 111 |
| MQTT 并发 | 20 台设备 × 10 msg/s，201.3 msg/s，3020 条 100% ACK | Day 97 |
| HTTP 读性能 | 40.3 req/s，avg 8.5 ms，p99 18 ms，100% 200 | Day 102 |
| HTTP 写性能 | 20.1 req/s，1208 条全部 200 且全部落库 | Day 102 |
| 突发限流 | 约 4639 req/s 输入，放行约 52.5 req/s，拒绝率 98.87% | Day 102 |
| traceId 大样本 | 142,771 个样本回传断言 0 失败 | Day 102 |
| 日志治理 | 拒绝日志行数 -99.996%，日志体积 -92.8% | Day 103 |
| 真实硬件 | 1 台 ESP32-S3 + DHT22 + 继电器完成温度/湿度双分支物理闭环 | Day 99 |

数字边界：

1. `4639 req/s` 是施压输入值，不是系统容量。
2. `52.5 req/s` 是当时限流后的放行速率，不是生产 SLO。
3. `375` 个测试是当前回归范围，不等同于生产质量证明。
4. `15 服务` 是 Compose 服务数量，不代表业务已经拆成微服务。

---

## 五、关键工程决策

| 主题 | 最终决策 | ADR / 报告 |
|------|----------|------------|
| 发布治理 | `main` 为唯一发布线，功能分支经 `--no-ff` 合并后打 tag | ADR 0017 |
| 密钥治理 | 根 `.env` 为本地 SSOT，敏感变量缺失 fail-fast | ADR 0015 |
| 消息边界 | MQTT 负责设备接入，RabbitMQ 负责后端任务、重试和 DLQ | ADR 0033 |
| QoS1 幂等 | Redis SETNX 字段级幂等，Redis 异常降级继续落库 | ADR 0034 |
| 硬件下行 | MQTT command + 后端 5 秒节流 + 固件 3 秒复位 | ADR 0035 |
| 观测 | Prometheus 拉取 + traceId + 滚动日志，不启用 ELK | ADR 0036 |
| 性能治理 | 只修有证据的日志放大器，不无依据修改 SQL/缓存/连接池 | ADR 0037 |
| 发布预演 | 同时验证宿主机与容器、健康端点、非 root 写权限和持久卷 | Day 111 |

---

## 六、`v3.0-final` 时边界（历史口径）

以下能力在 Day 112 发布时明确未完成；其路线后状态见第八章：

- 浏览器级 SSE E2E 尚未补齐，已有 RabbitMQ/Redis 集成链路测试；
- RAG 使用 `SimpleVectorStore + LocalHashEmbeddingModel`，尚未接入 Qdrant；
- `SseEmitterRegistry`、Guava 限流桶、日志节流状态仍是 per-JVM；
- 报警风暴的合并去重未实现；
- 前端尚未容器化；
- Grafana 仪表盘未落地，Prometheus 指标端点已可用；
- MinIO 镜像源治理和多实例部署仍属于后续工作；
- 真机规模为 1 台 ESP32-S3，不是量产设备接入。

---

## 七、当时路线后待办（执行状态）

后续工作按风险优先，不以增加技术名词为目标：

1. 补浏览器级 SSE E2E，覆盖连接、推送、断线重连和清理；
2. 实现报警风暴合并/去重，降低持续超限产生的重复业务事件；
3. 将 SSE 会话、限流和日志节流改造为跨实例协作；
4. 评估 Qdrant 或等价向量库替换本地哈希 Embedding；
5. 前端容器化并接入统一反向代理；
6. 增加 Grafana、告警规则和集中日志方案；
7. 完成 MinIO 镜像源、离线导入和多实例部署验证。

---

## 八、路线后增强（tag 之后）

`v3.0-final` 保持不动；以下增强位于独立分支，按 2026-10-18 收口结果记录：

- 活动告警聚合：Flyway V16 为 `alarm` 增加 `repeat_count`、`last_triggered_at` 和活动周期生成列唯一索引；`AlarmService.createAlarm()` 使用原子 upsert，已解决告警再次触发会开启新周期（ADR 0038）；
- Redis SSE fan-out：RabbitMQ 消费实例取得幂等权后发布 Redis Pub/Sub，各实例向本机 emitter 推送；首帧确认与 15 秒 comment 心跳已落地，浏览器 `/inspection` 连接/离开/重连实测不再出现 `AsyncRequestNotUsableException`（ADR 0039）；
- 分布式限流：`RateLimitStore` 支持 `local|redis` 双模，Redis Lua 令牌桶使用 Redis `TIME` 保持跨实例原子性；通用限流为 identity + path 双层桶，Redis 异常回退本地并节流告警日志（ADR 0039）；
- RAG 可选后端：新增 `QdrantVectorStore` 与 `OpenAiCompatibleEmbeddingModel`，默认仍为 `simple + hash`；Qdrant 1.12.4 位于 full profile（ADR 0040）；
- 交付与观测：新增 Vue 静态构建 + Nginx 前端容器；新增可选 Loki/Promtail/Grafana 10 服务 `observability` profile，full profile 同步包含观测栈（ADR 0041）；
- Compose v1.8：core 7 / full 20 / observability 10；MinIO 本地固定镜像 digest 为 `sha256:14cea493d9a34af32f524e538b8346cf79f3321eff8e708c1e2960462bd8936e`。

路线后质量口径：

| 指标 | 路线后结果 | 证据 |
|------|------------|------|
| 后端测试 | 403 / 403，61 测试类，0 failures / 0 errors / 0 skipped | `./mvnw clean test` |
| 数据库 | Schema V16，V1 + V3-V16 共 15 个活动迁移，10 张表 | Flyway |
| 架构决策 | ADR 0001-0041 | `docs/decision-log/` |
| 编排 | core 7 / full 20 / observability 10 | `docker compose config --services` |
| 前端交付 | Vue build + Nginx 容器，`/api/` 反代与 SSE 独立缓冲策略 | ADR 0041 |
| 观测 | Prometheus + 可选 Loki/Promtail/Grafana | ADR 0036 / 0041 |

仍未完成：跨主机故障切换、滚动升级、网络分区演练、容量 SLA、生产级日志长期存储与审计承诺。多实例 SSE/限流应表述为“基础能力已落地”，不能表述为“生产级高可用已完成”。

详细验证矩阵与边界见 [`docs/reports/post-roadmap-optimization-report-2026-10-18.md`](docs/reports/post-roadmap-optimization-report-2026-10-18.md)。

## 九、复现入口

```bash
# 后端测试
cd backend
./mvnw test

# 前端生产构建
cd ../frontend
npm run build

# Compose 校验
cd ..
docker compose config --quiet

# 健康检查
curl http://localhost:8080/actuator/health
```

从零部署以 `docs/SETUP.md` 和 `docs/reports/deploy-runbook-new-device.md` 为准；历史每日交付以 `backend/DAILY/` 和 `backend/REVIEW/` 为准；架构取舍以 `docs/decision-log/` 为准。

---

## 十、最终结论

这条路线已经完成“学习技术 -> 形成业务闭环 -> 用测试和实测证明 -> 记录取舍与边界 -> 打包发布”的完整过程。`v3.0-final` 可以作为求职展示和继续演进的稳定检查点，但不应被描述为生产级平台交付。

> 完成不是“所有问题都消失”，而是每个已完成项有证据、每个未完成项有边界、每个下一步有优先级。
