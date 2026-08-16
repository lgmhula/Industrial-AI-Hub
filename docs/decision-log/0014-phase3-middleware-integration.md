# ADR 0014 — Phase 3 中间件集成决策（RabbitMQ 拓扑 + Docker 容器化 + Nginx 反代）

| 属性 | 值 |
|------|-----|
| **状态** | ✅ 已采纳 |
| **决策日期** | 2026-08-16（Day 63，Phase 3 收官） |
| **决策者** | hula0710 |
| **关联** | Day 43-63（Redis / RabbitMQ / Docker / Linux） |

---

## 1. 背景

Phase 3 为项目接入 Redis 缓存、RabbitMQ 消息队列、Docker 容器化、Linux 部署。
其中 RabbitMQ 消息拓扑、Docker 多阶段镜像、Nginx 反向代理三项关键设计决策在决策日志中缺失，
本文档补齐，避免"为什么这么设计"的知识断层。

---

## 2. RabbitMQ 消息拓扑

| 模式 | 应用 | 说明 |
|------|------|------|
| 工作队列 | 报警消息异步处理 | 报警产生后异步投递，削峰解耦 |
| 发布/订阅 | 设备数据同步到多个消费者 | fanout 广播 |
| 可靠性 | DLQ + 手动 ACK + 幂等消费 | 消息不丢、不重复 |
| 延迟队列 | 报警 30s 未处理自动升级 | TTL + DLX 实现 |

- 三条管线位于 `dev.reboot.mq` 包：
  - `AlarmProducer` → `AlarmConsumer`（工作队列）
  - `DeviceDataProducer` → `DeviceDataSyncConsumer`（发布/订阅）
  - `AlarmEscalationConsumer`（延迟队列：报警 30 秒未处理自动升级）
- 消息载体：`AlarmMessage` / `DeviceDataMessage`。

---

## 3. Docker 容器化

- `backend/Dockerfile`：multi-stage（`25-jdk-alpine` 构建 → `25-jre-alpine` 运行），non-root 用户，layer caching，HEALTHCHECK `/actuator/health`。
- `compose.yml` 新增 `backend` 服务：`SPRING_PROFILES_ACTIVE=prod`，`depends_on` mysql/redis/rabbitmq 的 `service_healthy`。
- 构建产物约 302MB 多阶段镜像。

---

## 4. Nginx 反向代理

- 生产配置：gzip 压缩、加权轮询负载均衡（`weight`）、HTTP→HTTPS `301`、TLSv1.2/1.3、静态资源 `immutable` 缓存、SPA `try_files` 回退。
- JWT 无状态 → 无需 `ip_hash` 会话保持（任何实例都能验证 token）。

---

## 5. 影响

- 依赖：`spring-boot-starter-amqp`、`spring-boot-starter-data-redis`、`jedis`、`redisson`（版本见 `AGENTS.md` §5 锁定表）。
- 包结构：新增 `mq/`（消息管线）、`rule/`（报警规则引擎）。
- 文档：`docs/docker/docker-basics.md`、`docs/linux/linux-basics.md`、`docs/linux/nginx-advanced.md`。

---

## 6. 关联 ADR

- Redis 缓存选型：ADR 0002（redis-stack）
- Redis Sentinel 高可用：ADR 0006
- 统一 compose 编排：ADR 0001
