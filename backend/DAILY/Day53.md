# Day 53 — 消息可靠性：持久化、手动 ACK、死信队列、幂等消费

> 日期：2026-08-09 | 阶段：Phase 3（第 8 周 RabbitMQ）

## 今日目标

- [x] 死信队列 (DLX/DLQ) 配置 — 失败消息自动转入
- [x] ReliableMessagingDemo — 持久化 + 手动 ACK + 重试 + 幂等
- [x] 增强 AlarmConsumer — 手动 ACK 模式 + 重试 + DLQ 监听
- [x] 89/89 测试全绿

## 产出

### 1. ReliableMessagingDemo
`code/day53/ReliableMessagingDemo.java` — 四种可靠性机制演示：

```
day53.task.queue (x-dead-letter-exchange → day53.task.dlx)
    ↓ Consumer 处理 MSG-3 故意失败 → basicNack(requeue=false)
day53.task.dlx → day53.task.dlq
    ↓
DLQ Consumer: 收到死信 → 触发人工告警
```

### 2. MQConfig 死信配置

| 常量 | 值 | 用途 |
|------|------|------|
| `ALARM_DLX` | `alarm.dlx` | 死信 Exchange |
| `ALARM_DLQ` | `alarm.dlq` | 死信队列 |

`alarmQueue` 升级配置：
- `deadLetterExchange(ALARM_DLX)` — 失败 → DLX
- `deadLetterRoutingKey(ALARM_ROUTING_KEY)` — 路由键
- `ttl(30_000)` — 30 秒未消费自动过期进 DLQ

### 3. AlarmConsumer 增强

| 机制 | 实现 |
|------|------|
| 手动 ACK | `ackMode = "MANUAL"` + `Channel.basicAck/basicNack` |
| 重试 | 最多 3 次，退避 500/1000/1500ms |
| 死信队列 | Nack 后自动路由到 `alarm.dlq` |
| 幂等消费 | `ConcurrentHashMap<deliveryTag>` 去重 |
| DLQ 监听 | `handleDeadLetter()` — 收到死信触发人工告警 |

## 可靠性四层模型

```
Layer 1: 持久化        durable Exchange/Queue + deliveryMode=2
Layer 2: 手动 ACK      成功 basicAck, 失败 basicNack(requeue=false)
Layer 3: 死信队列      失败 → DLX → DLQ → 人工介入
Layer 4: 幂等消费      deliveryTag Set 去重，防止重复处理
```

## 验证命令

```bash
cd backend && ./mvnw test
# Tests run: 89, Failures: 0, Errors: 0, Skipped: 0
```

## 明日

Day 54 — 延迟队列：报警延迟通知（30 秒内未处理则升级）
