# Week 08 复盘 — RabbitMQ 消息队列（Phase 3 第 8 周）

> 日期：2026-08-07 ~ 2026-08-09 | 覆盖：Day 50 ~ Day 55

---

## 一、本周目标 vs 实际

| 目标 | 实际 | 状态 |
|------|------|:----:|
| Day 50: RabbitMQ 核心概念 | Exchange/Queue/Binding + 低层 API Demo | ✅ |
| Day 51: 工作队列 | AlarmProducer → AlarmConsumer 竞争消费 | ✅ |
| Day 52: 发布/订阅 | DeviceDataProducer → Fanout → 双消费者 | ✅ |
| Day 53: 消息可靠性 | DLQ + 手动 ACK + 重试 + 幂等消费 | ✅ |
| Day 54: 延迟队列 | TTL + DLX 实现 30s 超时升级 | ✅ |
| Day 55: 项目整合 | 三条 MQ 管线全线贯通 | ✅ |

## 二、关键收获

### 2.1 从同步到异步的范式转换

Day 51 是分水岭。在此之前，`DeviceDataService.report()` 是一条同步链路：
```
report() → insert DB → alarmDetector.check() → return
```

Day 51 之后，报警变成异步：
```
report() → insert DB → alarmProducer.send() → return
                                      ↓ (异步)
                                 AlarmConsumer
```

这意味着报警处理不再阻塞数据上报——即使报警处理耗时 5 秒，API 仍然 50ms 返回。

### 2.2 四种 Exchange 模式对比

| Exchange | 路由规则 | 类比 | 项目落地 |
|----------|----------|------|----------|
| Direct | routingKey 精确匹配 | 专线电话 | 报警处理、延迟检查 |
| Fanout | 忽略 routingKey，全广播 | 群发邮件 | 设备数据同步 |
| Topic | routingKey 模式匹配 (*.#) | 分部门通知 | Day 56+ 待探索 |
| Headers | 按消息头属性匹配 | 条件过滤 | 特殊场景 |

### 2.3 消息可靠性四层模型

这是本周最重要的架构认知：

```
Layer 1: 持久化      → durable Exchange/Queue + deliveryMode=2
Layer 2: 手动 ACK    → basicAck / basicNack(requeue=false)
Layer 3: 死信队列    → DLX/DLQ 兜底，失败消息不丢弃
Layer 4: 幂等消费    → deliveryTag Set 去重
```

四层环环相扣：持久化防重启丢消息，手动 ACK 防 Consumer 崩溃丢消息，死信队列防处理失败丢消息，幂等消费防重复处理。

### 2.4 TTL + DLX = 延迟队列

RabbitMQ 没有内置延迟队列，但 TTL + DLX 模式优雅地实现了：
- 消息在 `delay.queue`（无消费者）等待 TTL 过期
- 过期后自动进入 DLX → escalation queue
- 零 CPU 轮询开销

这比 `@Scheduled` 轮询数据库更优雅——消息级精度 + 零空转。

## 三、代码统计

- **新增文件**：AlarmMessage、AlarmProducer、AlarmConsumer、DeviceDataMessage、DeviceDataProducer、DeviceDataSyncConsumer、AlarmEscalationConsumer、MQConfig
- **学习 Demo**：Day50 RabbitMQDemo、Day51 WorkQueueDemo、Day52 PubSubDemo、Day53 ReliableMessagingDemo、Day54 DelayQueueDemo
- **修改文件**：DeviceDataService（集成三条 MQ 管线）、pom.xml（amqp 依赖）、application.yml（RabbitMQ 配置）
- **测试**：89/89 全绿

## 四、MQ 全架构

```
DeviceDataService.report()
 │
 ├─ [Fanout "device-data.fanout"]       ← Day 52
 │    ├→ device-data.log.queue          → DeviceDataSyncConsumer
 │    └→ device-data.analytics.queue    → DeviceDataSyncConsumer
 │
 ├─ [Direct "alarm.exchange"]           ← Day 51
 │    └→ alarm.queue (DLX→alarm.dlq)    → AlarmConsumer (手动ACK+重试)
 │
 └─ [Direct "alarm.delay.exchange"]     ← Day 54
      └→ alarm.delay.queue (TTL 30s)
           → alarm.delay.dlx
             └→ alarm.escalation.queue  → AlarmEscalationConsumer
```

## 五、不足与改进

1. **Topic Exchange 未实践**：Day 52 提到了 Topic 但未实现。场景：按 `alarm.critical.*` / `alarm.warning.*` 分级路由
2. **Spring RabbitMQ 高层 API 未对比**：Day 50 用了低层 amqp-client，Day 51+ 用了 Spring RabbitTemplate。两者的取舍值得一篇笔记
3. **测试覆盖**：MQ 组件用 `@Profile("!test")` 排除，暂无集成测试。Day 58+ 可考虑 Testcontainers

## 六、下周展望（第 9 周：Docker + Linux 部署）

| 天 | 任务 |
|----|------|
| Day 57 | Docker 基础：镜像、容器、Dockerfile |
| Day 58 | 编写项目 Dockerfile，构建 SpringBoot 镜像 |
| Day 59 | docker-compose 编排：MySQL + Redis + RabbitMQ + 应用 |
| Day 60 | Linux 基础命令 |
| Day 61 | 项目部署到 Linux |
| Day 62 | Nginx 配置 |
| Day 63 | 周复盘 |

> 第 7 周 Redis 是"加速"——缓存让读更快。
> 第 8 周 RabbitMQ 是"解耦"——消息让系统更弹性。
> 第 9 周 Docker 是"封装"——容器让部署可复制。
