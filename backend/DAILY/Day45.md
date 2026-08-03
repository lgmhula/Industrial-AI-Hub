# Day 45 — 缓存三大问题解决方案

> 日期：2026-08-04

## 产出

- [x] **缓存穿透** → Bloom Filter（RedisBloom BF.RESERVE/BF.ADD/BF.EXISTS）
- [x] **缓存击穿** → 互斥锁（SETNX，单线程查DB重建缓存）
- [x] **缓存雪崩** → 随机 TTL（±20% 偏移，分散过期时间）
- [x] `CachePatternDemo.java` — 三大模式完整演示
- [x] `CacheService.jitter()` — 生产级随机 TTL 实现

## 学习代码

[CachePatternDemo.java](../src/main/java/code/day45/CachePatternDemo.java)

## 明日

Day 46 — Redis 分布式锁（Redisson）：设备数据上报防重
