# Week 07 复盘 — Redis 缓存体系（Phase 3 第 7 周）

> 日期：2026-08-04 ~ 2026-08-07 | 覆盖：Day 43 ~ Day 48

---

## 一、本周目标 vs 实际

| 目标 | 实际 | 状态 |
|------|------|:----:|
| Day 43: Redis 五种数据类型 | string/hash/list/set/zset 练习完成 | ✅ |
| Day 44: 缓存实战 (CacheService) | Cache-Aside 模式 + 随机 TTL 防雪崩 | ✅ |
| Day 45: 穿透/击穿/雪崩 | 布隆过滤器、互斥锁、随机过期方案 | ✅ |
| Day 46: Redisson 分布式锁 | SET NX EX 防重 + Redisson RLock | ✅ |
| Day 47: Spring Cache 注解集成 | @Cacheable/@CacheEvict + CacheConfig | ✅ |
| Day 48: 全项目实战整合 | 迁移 Device/User Service + 安全清理 | ✅ |

## 二、关键收获

### 2.1 从手写到声明式

本周经历了两次范式转换：
1. **Day 44-46**：从无缓存到手写 `CacheService`（getOrFetch/put/evict）— 理解了 Cache-Aside 模式的细节
2. **Day 47-48**：从手写到 Spring Cache 注解 — 理解了声明式缓存的优雅

每层抽象都有代价，但也都让下一层更清晰。

### 2.2 三个经典问题

| 问题 | 原因 | 本项目的解法 |
|------|------|-------------|
| 缓存穿透 | 查询不存在的数据 | CacheConfig 已 `disableCachingNullValues()` |
| 缓存击穿 | 热点 Key 过期 + 并发重建 | CacheService 互斥锁 (学习阶段)；生产可降级 |
| 缓存雪崩 | 大量 Key 同时过期 | `jitter()` ±20% 随机偏移（学习阶段） |

### 2.3 分布式锁的直觉

`SET NX EX` 的原子性保证了"同一窗口只入库一次"——这比 JVM `synchronized` 强大得多。Redisson 的看门狗自动续期解决了锁超时问题。

### 2.4 安全意识的觉醒

`LaissezFaireSubTypeValidator` 是真实的反序列化漏洞。Day 48 标记 `@Deprecated` + 全项目切换到 `GenericJackson2JsonRedisSerializer`。不是每段代码都要写出来就完美——但要能在合适的时机识别并修正。

## 三、代码统计

- **新增文件**：CacheConfig、SpringCacheDemo、CacheService（及 3 个 dayXX 学习代码）
- **修改文件**：DeviceDataService、DeviceService、UserService、RedisConfig、CacheConfig
- **测试**：80 → 89 条（+DeviceDataServiceCacheTest 4 条 + DeviceServiceCacheTest 4 条 + UserServiceCacheTest 5 条）

## 四、不足与改进

1. **CacheService 的互斥锁**：用的是 SETNX + sleep 轮询，生产环境应改用 Redisson RLock 的 `tryLock(waitTime, leaseTime)`
2. **布隆过滤器**：Day 45 仅讨论了概念，未实际集成 Redisson 的 `RBloomFilter`
3. **缓存预热**：项目启动时未预热热点数据，后续 Day 可加入 `@PostConstruct` 预热逻辑
4. **监控缺失**：没有缓存命中率监控。但 Phase 4 有 Prometheus + Actuator，届时补上

## 五、下周展望（第 8 周：RabbitMQ）

| 天 | 任务 |
|----|------|
| Day 50 | RabbitMQ 安装 + 核心概念 + 简单收发 |
| Day 51 | 工作队列模式：报警消息异步处理 |
| Day 52 | 发布/订阅模式：设备数据同步 |
| Day 53 | 消息可靠性：持久化、ACK、死信队列 |
| Day 54 | 延迟队列：报警延迟通知 |
| Day 55 | RabbitMQ 整合到项目 |
| Day 56 | 周复盘 + RabbitMQ 笔记整理 |

> 本周的 Redis 是"加速"——缓存让读更快。下周的 RabbitMQ 是"解耦"——消息让系统更弹性。
