# Day 44 — Redis 缓存实战

> 日期：2026-08-04 | 阶段：Phase 3（第 7 周 Redis）

## 今日目标

- 将 Redis 从“数据类型练习”升级为项目真实缓存层
- 完成 `StringRedisTemplate` / `RedisTemplate` 双模板配置
- 在用户、设备两个高频读取场景落地 Cache-Aside 缓存降级

## 背景问题

工业平台中用户档案、设备档案属于“读多写少”数据：每次请求都查 MySQL，
数据库连接和 SQL 开销高。目标是把查询结果缓存到 Redis，命中后直接返回。

## 产出

- [x] `spring-boot-starter-data-redis` 集成（Lettuce 客户端）
- [x] `RedisConfig.java` — StringRedisTemplate + ObjectRedisTemplate（Jackson JSON 序列化）
- [x] `CacheService.java` — 统一缓存层（getOrFetch/put/evict/互斥锁/随机TTL）
- [x] `UserService.getById()` — Redis 缓存降级（缓存命中→返回，未命中→DB→回写）
- [x] `DeviceService.getById()` — 同上
- [x] 写操作后缓存失效（update/delete → evict）
- [x] `CacheWarmupDemo.java` — 缓存预热演示

## 核心知识点

1. **Cache-Aside（旁路缓存）**：读时先查缓存，未命中查 DB 并回写；写时先更新 DB，再删除/更新缓存。
2. **缓存降级（Fallback）**：Redis 异常时捕获并直接查 DB，缓存故障不能拖垮主链路。
3. **缓存预热**：系统启动或大促前把热点 Key 预先写入，避免冷启动流量全部打到 DB。

## 工业场景对标

- 设备档案查询（`device:id:*`）—— 点位信息不常变，缓存 30 分钟
- 用户信息查询（`user:id:*`）—— 登录态高频读取
- 写操作（update/delete）后立即 `evict`，防止脏读

## 关键代码

- [RedisConfig.java](../src/main/java/dev/reboot/config/RedisConfig.java)
- [CacheService.java](../src/main/java/dev/reboot/service/CacheService.java)
- [CacheWarmupDemo.java](../src/main/java/code/day44/CacheWarmupDemo.java)

## 验证命令

```bash
docker compose up -d redis
cd backend && ./mvnw test
```

## 测试

- 当日：75/76（ApplicationContextLoadTest 基础设施回归，Day 47 前修复为 80/80）

## 明日

Day 45 — 缓存穿透/击穿/雪崩：布隆过滤器、互斥锁、随机过期
