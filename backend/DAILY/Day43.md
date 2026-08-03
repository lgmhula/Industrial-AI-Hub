# Day 43 — Redis 五种基本数据类型练习

> 日期：2026-08-04 | Phase 3-A 完成后返回路线图

## 今日任务

- [x] Redis 环境确认（compose.yml redis 服务已运行）
- [x] Jedis 客户端依赖添加（5.2.0）
- [x] String 类型：SET/GET/SETEX/INCR/MGET
- [x] Hash 类型：HSET/HGET/HGETALL/HDEL
- [x] List 类型：LPUSH/RPUSH/LPOP/LRANGE/LTRIM
- [x] Set 类型：SADD/SMEMBERS/SINTER/SUNION/SDIFF
- [x] ZSet 类型：ZADD/ZRANGE/ZREVRANGE/ZRANK/ZSCORE

## 产出

- [RedisDataTypeDemo.java](../src/main/java/code/day43/RedisDataTypeDemo.java)

## 运行方式

```bash
# 确保 Redis 已启动
docker compose up -d redis

# 编译运行
cd backend
./mvnw compile exec:java -Dexec.mainClass="code.day43.RedisDataTypeDemo"
```

## 明日计划

Day 44 — Redis 缓存实战：用户信息缓存、设备数据缓存、缓存预热

---

> 累计：76 单元测试通过 | Phase 3-A (T1-T6) 完成
