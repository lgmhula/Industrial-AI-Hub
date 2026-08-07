# Day 51 — 工作队列模式：报警消息异步处理

> 日期：2026-08-07 | 阶段：Phase 3（第 8 周 RabbitMQ）

## 今日目标

- [x] 工作队列模式 Demo：多 Consumer 竞争消费
- [x] 创建 AlarmProducer / AlarmConsumer 生产级组件
- [x] 集成到 DeviceDataService.report()：报警触发时异步发送
- [x] 修复 3 处配置漂移（双 test.yml / env 变量缺失 / 自动配置排除）
- [x] 89/89 测试全绿

## 产出

### 1. 工作队列 Demo
`code/day51/WorkQueueDemo.java`：
- 1 个 Producer 发送 10 条模拟报警
- 2 个 Worker 竞争消费（prefetch=1 公平调度）
- Worker-1 慢 (400ms)、Worker-2 快 (200ms) — 验证能者多劳
- 手动 ACK 保证消息不丢失

### 2. 生产级 MQ 组件

| 组件 | 文件 | 职责 |
|------|------|------|
| `AlarmMessage` | `mq/AlarmMessage.java` | 报警消息 DTO (Serializable) |
| `AlarmProducer` | `mq/AlarmProducer.java` | 发送报警到 `alarm.exchange` |
| `AlarmConsumer` | `mq/AlarmConsumer.java` | `@RabbitListener` 异步消费、2-4 线程 |
| `MQConfig` | `config/MQConfig.java` | Exchange/Queue/Binding + JSON 转换器 |

### 3. 集成点
`DeviceDataService.report()` — 报警检测后自动发送 AlarmMessage 到 MQ：

```
report() → AlarmDetector.check() → 发现报警
  → for each AlarmVO → AlarmProducer.send(AlarmMessage)
    → alarm.exchange → alarm.queue → AlarmConsumer.handleAlarm()
```

使用 `@Autowired(required = false)` 确保 test profile 下 AlarmProducer 为 null，测试不受影响。

### 4. 配置漂移修复

| 漂移 | 修复前 | 修复后 |
|------|--------|--------|
| 双 test.yml | `src/main` 和 `src/test` 各有不同内容 | 合并到 `src/main`，删除 `src/test` 副本 |
| .env 缺变量 | 无 `RABBITMQ_HOST`/`RABBITMQ_PORT` | 补全两个变量 |
| test 缺排除 | 无 `RabbitAutoConfiguration` 排除 | 补充到合并后的 test.yml |

## 核心知识点

1. **工作队列 vs 发布/订阅**：工作队列每条消息只被一个 Consumer 处理（竞争）；发布/订阅每条消息所有 Consumer 都收到（广播）
2. **prefetch=1**：公平调度的关键 — 每个 Consumer 一次只取一条，快的多取
3. **手动 ACK vs 自动 ACK**：`autoAck=false` + `basicAck()` 保证 Consumer 崩溃时消息不丢
4. **JSON 消息转换**：`Jackson2JsonMessageConverter` 替代默认 `SimpleMessageConverter`，支持复杂对象

## 验证命令

```bash
cd backend && ./mvnw test
# Tests run: 89, Failures: 0, Errors: 0, Skipped: 0
```

## 明日

Day 52 — 发布/订阅模式：设备数据同步到多个消费者
