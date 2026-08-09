# Day 52 — 发布/订阅模式：设备数据同步到多个消费者

> 日期：2026-08-09 | 阶段：Phase 3（第 8 周 RabbitMQ）

## 今日目标

- [x] Fanout Exchange 发布/订阅模式 Demo
- [x] 项目级 Fanout Exchange 配置（设备数据广播）
- [x] DeviceDataSyncConsumer：日志归档 + 实时分析双消费者

## 产出

### 1. PubSubDemo
`code/day52/PubSubDemo.java` — 1 个 Fanout Exchange 绑定 3 个 Queue：
```
Producer → [Fanout Exchange]
             ├→ log.queue      → LogConsumer
             ├→ analytics.queue → AnalyticsConsumer
             └→ notify.queue    → NotifyConsumer
```
每条消息被广播到全部 3 个消费者，验证发布/订阅的广播语义。

### 2. MQConfig 扩展
新增 Fanout Exchange + 2 个队列：

| 常量 | 值 | 用途 |
|------|------|------|
| `DEVICE_DATA_FANOUT` | `device-data.fanout` | 设备数据广播 Exchange |
| `DEVICE_DATA_LOG_QUEUE` | `device-data.log.queue` | 日志归档队列 |
| `DEVICE_DATA_ANALYTICS_QUEUE` | `device-data.analytics.queue` | 实时分析队列 |

### 3. DeviceDataSyncConsumer
- `handleLog()` — 监听 `device-data.log.queue`：日志归档
- `handleAnalytics()` — 监听 `device-data.analytics.queue`：实时分析

## 核心知识点

| 模式 | Exchange | 消息分发 | Day | 
|------|----------|----------|-----|
| 工作队列 | Direct | 一条 → 一个 Consumer | Day 51 |
| 发布/订阅 | Fanout | 一条 → 所有 Consumer | Day 52 |
| 主题路由 | Topic | 按模式匹配 | Day 54 |

## 明日

Day 53 — 消息可靠性：持久化、手动 ACK、死信队列、幂等消费
