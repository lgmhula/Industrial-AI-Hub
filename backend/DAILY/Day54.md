# Day 54 — 延迟队列：报警延迟通知（30 秒内未处理则升级）

> 日期：2026-08-09 | 阶段：Phase 3（第 8 周 RabbitMQ）

## 今日目标

- [x] TTL + DLX 延迟队列原理 Demo
- [x] 项目延迟队列基础设施 (delay exchange/queue/escalation)
- [x] AlarmEscalationConsumer — 监听升级消息
- [x] AlarmProducer.sendDelayCheck() — 报警时同步发延迟检查
- [x] 89/89 测试全绿

## 产出

### 1. DelayQueueDemo
`code/day54/DelayQueueDemo.java` — TTL + DLX 实现延迟队列：

```
Producer → delay.queue (无消费者, TTL=5s)
            → 5s 后过期
            → delay.dlx → escalation.queue
            → EscalationConsumer: "报警升级！"
```

3 条消息全部在 5s 后自动升级，验证延迟队列的零轮询特性。

### 2. 项目延迟队列架构

```
AlarmProducer.sendDelayCheck()
  → alarm.delay.exchange → alarm.delay.queue (TTL 30s, 无消费者)
    → 30s 后过期
    → alarm.delay.dlx → alarm.escalation.queue
    → AlarmEscalationConsumer.handleEscalation()
```

| 组件 | 队列/Exchange | 作用 |
|------|--------------|------|
| `alarm.delay.exchange` | Direct Exchange | 接收延迟消息 |
| `alarm.delay.queue` | Queue (无消费者) | 消息停留 30s |
| `alarm.delay.dlx` | Direct Exchange | 死信路由 |
| `alarm.escalation.queue` | Queue | 升级消息投递 |

### 3. AlarmEscalationConsumer
- `handleEscalation(AlarmMessage)` — 收到升级消息，触发告警
- 当前记录日志，Day 55 补齐 DB 状态查询

### 4. 集成链路

```
DeviceDataService.report()
  → AlarmDetector.check() → 发现报警
    → alarmProducer.send(msg)          // 正常工作队列
    → alarmProducer.sendDelayCheck(msg) // 延迟 30s 升级检查
      → 30s 后 AlarmEscalationConsumer 收到
```

## 核心知识点

1. **延迟队列 ≠ RabbitMQ 内置功能** — 通过 TTL + DLX 模式实现
2. **per-message TTL vs queue TTL**：queue TTL 作用于所有消息，per-message TTL 可针对单条（本日使用）
3. **为什么不用 @Scheduled 轮询**：延迟队列零 CPU 开销，精度到毫秒级
4. **delay.queue 为什么没有消费者**：消息不需要被处理，只需在 TTL 过期后自动转入 DLX

## 验证命令

```bash
cd backend && ./mvnw test
# Tests run: 89, Failures: 0, Errors: 0, Skipped: 0
```

## 明日

Day 55 — RabbitMQ 整合到项目：重构报警和数据上报模块
