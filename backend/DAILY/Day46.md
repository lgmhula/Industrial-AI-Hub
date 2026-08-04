# Day 46 — Redis 分布式锁：设备数据上报防重

> 日期：2026-08-04 | 阶段：Phase 3（第 7 周 Redis）

## 今日目标

- 理解“分布式锁”与 JVM 锁的本质区别
- 用 Redis 原子命令实现设备上报去重
- 引入 Redisson，对比简易锁与生产级锁

## 背景问题

PLC/传感器可能因网络重试、双链路采集在同一个时间窗口内重复上报同一条数据。
多实例部署下 JVM 锁失效，必须用 Redis 保证“同一窗口只入库一次”。

## 产出

- [x] Redisson 3.39.0 集成（`redisson-spring-boot-starter`）
- [x] `DeviceDedupDemo.java` — SET NX EX 防重演示
- [x] 以 "设备ID + 时间窗口" 为去重 Key，窗口内重复上报自动拦截

## 核心知识点

1. **SET NX EX 原子性**：`NX` 保证不存在才写入，`EX` 保证自动过期，两者同一命令提交。
2. **去重 Key 设计**：`dedup:{deviceId}:{时间窗口}` —— 窗口内第二次 SET 返回空，判定为重复。
3. **Redisson RLock**：看门狗自动续期、可重入、公平锁，适合跨实例互斥场景。

## 工业场景对标

- 设备数据上报防重（本日 Demo）
- 定时巡检任务多实例互斥执行
- 告警处置并发修改防覆盖

## Redisson vs Jedis SETNX

| 维度 | Jedis SET NX EX | Redisson RLock |
------|------|------|
| 原子性 | ✅ 单命令原子 | ✅ 脚本原子 |
| 自动续期 | ❌ 需手动 | ✅ 看门狗 |
| 可重入 | ❌ | ✅ |
| 场景 | 简单去重/幂等 | 跨实例临界区 |

## 学习代码

[DeviceDedupDemo.java](../src/main/java/code/day46/DeviceDedupDemo.java)

## 验证命令

```bash
docker compose up -d redis
cd backend && ./mvnw compile exec:java -Dexec.mainClass="code.day46.DeviceDedupDemo"
```

## 明日

Day 47 — Spring Cache 注解集成（@Cacheable/@CacheEvict）
