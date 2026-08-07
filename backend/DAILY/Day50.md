# Day 50 — RabbitMQ 安装 + 核心概念 + 简单收发

> 日期：2026-08-07 | 阶段：Phase 3（第 8 周 RabbitMQ）

## 今日目标

- [x] 添加 `spring-boot-starter-amqp` 依赖
- [x] 配置 RabbitMQ 连接（application.yml）
- [x] 编写 RabbitMQ 核心概念 Demo：Exchange / Queue / Binding + 收发
- [x] 创建 test profile 隔离外部依赖（Redisson + RabbitMQ）
- [x] 89/89 测试全绿

## 产出

### 1. 依赖与配置
- `pom.xml`: 新增 `spring-boot-starter-amqp`
- `application.yml`: 新增 `spring.rabbitmq` 配置节（host/port/user/pass/virtual-host）
- `application-test.yml`: 排除 `RabbitAutoConfiguration` + `RedissonAutoConfigurationV2`

### 2. RabbitMQDemo.java
`code/day50/RabbitMQDemo.java` — 低层 API (amqp-client) 演示：

```
Producer → [Direct Exchange "day50.exchange"]
               │  routingKey = "day50.routing"
               ↓
          [Queue "day50.queue"]
               │
               ↓
          Consumer (异步回调)
```

核心概念：

| 概念 | 作用 | 本日 Demo |
|------|------|-----------|
| Connection | 到 Broker 的 TCP 长连接 | `factory.newConnection()` |
| Channel | 连接内的虚拟通道（轻量） | `connection.createChannel()` |
| Exchange | 接收消息并路由到队列 | `exchangeDeclare(DIRECT)` |
| Queue | 消息存储缓冲区 | `queueDeclare(durable=true)` |
| Binding | Exchange → Queue 的绑定规则 | `queueBind(routingKey)` |
| Producer | 发送消息 | `basicPublish()` |
| Consumer | 接收消息（push 模式） | `basicConsume(callback)` |

### 3. Exchange 类型速查

| 类型 | 路由规则 | 适用场景 |
|------|----------|----------|
| Direct | routingKey 精确匹配 | 本日 Demo；点对点任务 |
| Fanout | 广播到所有绑定队列 | Day 52：设备数据同步 |
| Topic | routingKey 模式匹配 (*.#) | Day 54：报警分级路由 |

## 运行方式

```bash
docker compose up -d rabbitmq
cd backend && ./mvnw compile exec:java -Dexec.mainClass="code.day50.RabbitMQDemo"
```

## 关键知识点

1. **Channel 不是线程安全的** — 每个线程应使用独立 Channel
2. **durable=true** 让 Queue/Exchange 在 Broker 重启后不丢失
3. **PERSISTENT_TEXT_PLAIN** 让消息持久化到磁盘
4. **acknowledge-mode: auto** — Spring 默认配置，适合学习阶段；生产场景 Day 53 细讲

## 验证命令

```bash
cd backend && ./mvnw test
# Tests run: 89, Failures: 0, Errors: 0, Skipped: 0
```

## 明日

Day 51 — 工作队列模式：报警消息异步处理
