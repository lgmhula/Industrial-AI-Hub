# Day 46 — Redis 分布式锁：设备数据上报防重

> 日期：2026-08-04

## 产出

- [x] Redisson 3.39.0 集成（`redisson-spring-boot-starter`）
- [x] `DeviceDedupDemo.java` — SET NX EX 防重演示
- [x] 以 "设备ID + 时间窗口" 为去重 Key，窗口内重复上报自动拦截

## 学习代码

[DeviceDedupDemo.java](../src/main/java/code/day46/DeviceDedupDemo.java)

## Redisson vs Jedis SETNX

- **本 Demo**: Jedis SET NX EX（原子操作，简易锁）
- **生产推荐**: Redisson RLock（看门狗自动续期、可重入、公平锁）

## 明日

Day 47 — Spring Cache 注解集成（@Cacheable/@CacheEvict）
