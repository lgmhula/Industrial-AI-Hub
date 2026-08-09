# Day 56 — 周复盘 + RabbitMQ 笔记整理

> 日期：2026-08-09 | 阶段：Phase 3（第 8 周 RabbitMQ 收尾）

## 今日目标

- [x] 编写 Week08 周复盘
- [x] RabbitMQ 知识体系沉淀

## 产出

- [x] `REVIEW/Week08.md` — 第 8 周完整复盘
  - 四种 Exchange 模式对比表
  - 消息可靠性四层模型
  - 全 MQ 架构图
  - 不足与改进方向

## RabbitMQ 知识体系

```
RabbitMQ 学习路径 (Day 50 → 55)

Day 50  核心概念        Exchange / Queue / Binding / Channel
           ↓
Day 51  工作队列        Direct Exchange → 竞争消费 (round-robin)
           ↓
Day 52  发布/订阅       Fanout Exchange → 广播到所有队列
           ↓
Day 53  消息可靠性      DLQ / 手动ACK / 重试 / 幂等
           ↓
Day 54  延迟队列        TTL + DLX → 定时消息 (30s 超时升级)
           ↓
Day 55  项目整合        三条 MQ 管线全线贯通
```

## 明日

Day 57 — Docker 基础：镜像、容器、Dockerfile
