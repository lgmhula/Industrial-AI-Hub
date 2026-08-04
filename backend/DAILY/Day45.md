# Day 45 — 缓存三大问题解决方案

> 日期：2026-08-04 | 阶段：Phase 3（第 7 周 Redis）

## 今日目标

- 识别缓存穿透、击穿、雪崩三类故障的产生条件
- 用 Redis 原生命令逐一给出可落地方案
- 把随机 TTL 沉淀进 `CacheService` 公共层

## 背景问题

单纯 Cache-Aside 在工业监控高并发下会暴露三个缺口：

1. **穿透**：查询不存在的 ID，每次都打到 DB
2. **击穿**：热点 Key 过期瞬间，大量请求同时重建缓存
3. **雪崩**：大量 Key 同时过期，DB 瞬时过载

## 产出

- [x] **缓存穿透** → Bloom Filter（RedisBloom BF.RESERVE/BF.ADD/BF.EXISTS）
- [x] **缓存击穿** → 互斥锁（SETNX，单线程查DB重建缓存）
- [x] **缓存雪崩** → 随机 TTL（±20% 偏移，分散过期时间）
- [x] `CachePatternDemo.java` — 三大模式完整演示
- [x] `CacheService.jitter()` — 生产级随机 TTL 实现

## 核心知识点

| 问题 | 方案 | 要点 |
------|------|------|
| 穿透 | 布隆过滤器 | 不存在的数据先在 Bloom 层拦截，省去 DB 查询 |
| 击穿 | SETNX 互斥锁 | 只允许一个线程查 DB 重建，其余等待后读缓存 |
| 雪崩 | 随机 TTL | 基础 TTL ±20% 抖动，避免同批次过期 |

## 工业场景对标

- 设备点位监控：单设备热点数据 Key 失效瞬间，多客户端同时订阅 → 互斥锁
- 大促/夜班批量上报：海量 Key 同时建立 → 随机 TTL 错峰
- 传感器异常查询不存在设备：Bloom 快速拒绝无效查询

## 学习代码

[CachePatternDemo.java](../src/main/java/code/day45/CachePatternDemo.java)

## 验证命令

```bash
docker compose up -d redis
cd backend && ./mvnw compile exec:java -Dexec.mainClass="code.day45.CachePatternDemo"
```

## 明日

Day 46 — Redis 分布式锁（Redisson）：设备数据上报防重
