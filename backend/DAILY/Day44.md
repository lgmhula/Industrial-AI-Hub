# Day 44 — Redis 缓存实战

> 日期：2026-08-04

## 产出

- [x] `spring-boot-starter-data-redis` 集成（Lettuce 客户端）
- [x] `RedisConfig.java` — StringRedisTemplate + ObjectRedisTemplate（Jackson JSON 序列化）
- [x] `CacheService.java` — 统一缓存层（getOrFetch/put/evict/互斥锁/随机TTL）
- [x] `UserService.getById()` — Redis 缓存降级（缓存命中→返回，未命中→DB→回写）
- [x] `DeviceService.getById()` — 同上
- [x] 写操作后缓存失效（update/delete → evict）
- [x] `CacheWarmupDemo.java` — 缓存预热演示

## 测试

- 75/76 单元测试通过（ApplicationContextLoadTest 需基础设施，已知限制）

## 明日

Day 45 — 缓存穿透/击穿/雪崩：布隆过滤器、互斥锁、随机过期
