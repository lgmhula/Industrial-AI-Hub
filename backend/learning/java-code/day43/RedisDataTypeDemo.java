package code.day43;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Day 43 — Redis 五种基本数据类型练习。
 *
 * <h3>覆盖类型</h3>
 * <ol>
 *   <li><b>String</b> — SET/GET/INCR/EXPIRE/TTL</li>
 *   <li><b>Hash</b> — HSET/HGET/HGETALL/HDEL</li>
 *   <li><b>List</b> — LPUSH/RPUSH/LPOP/RPOP/LRANGE</li>
 *   <li><b>Set</b> — SADD/SMEMBERS/SINTER/SUNION/SDIFF</li>
 *   <li><b>ZSet</b> — ZADD/ZRANGE/ZRANK/ZSCORE</li>
 * </ol>
 *
 * @author hula0710
 * @since 2026-08-04 (Day 43)
 */
public class RedisDataTypeDemo {

    private static final String HOST = "localhost";
    private static final int PORT = 6379;
    private static final String PASSWORD = System.getenv("REDIS_PASSWORD");

    public static void main(String[] args) {
        System.out.println("========== Redis 五种基本数据类型练习 ==========\n");

        try (Jedis jedis = new Jedis(HOST, PORT)) {
            if (PASSWORD != null && !PASSWORD.isBlank()) {
                jedis.auth(PASSWORD);
            }
            System.out.println("✅ 连接成功: " + jedis.ping() + "\n");

            demoString(jedis);
            demoHash(jedis);
            demoList(jedis);
            demoSet(jedis);
            demoZSet(jedis);

            // 清理
            jedis.flushDB();
            System.out.println("🧹 测试数据已清理 (flushDB)");
        } catch (Exception e) {
            System.err.println("❌ 连接失败: " + e.getMessage());
            System.err.println("请确认 Redis 容器已启动: docker compose up -d redis");
        }
    }

    /** 1. String — 最基础：缓存值、计数器、带过期时间的缓存。 */
    private static void demoString(Jedis jedis) {
        System.out.println("=== 1. String ===");

        // SET / GET
        jedis.set("device:name:1001", "温度传感器-A区");
        System.out.println("  GET device:name:1001  = " + jedis.get("device:name:1001"));

        // SETEX (set with expire) / TTL
        jedis.setex("alarm:temp:1001", 10, "高温告警");
        System.out.println("  TTL alarm:temp:1001   = " + jedis.ttl("alarm:temp:1001") + "s");

        // SET NX (only set if not exists) / SET XX (only update if exists)
        jedis.set("lock:device:1001", "locked", SetParams.setParams().nx().ex(30));
        System.out.println("  SET NX lock:device:1001 = " + jedis.get("lock:device:1001"));

        // INCR / DECR — 计数器
        jedis.set("counter:online", "0");
        jedis.incr("counter:online");
        jedis.incrBy("counter:online", 5);
        System.out.println("  INCR counter:online    = " + jedis.get("counter:online"));

        // MGET — 批量获取
        List<String> names = jedis.mget("device:name:1001", "device:name:1002");
        System.out.println("  MGET [1001, 1002]      = " + names);

        System.out.println();
    }

    /** 2. Hash — 对象存储：设备属性、用户信息。 */
    private static void demoHash(Jedis jedis) {
        System.out.println("=== 2. Hash ===");

        // HSET / HGET
        jedis.hset("device:info:1001", "name", "温度传感器-A区");
        jedis.hset("device:info:1001", "type", "temperature");
        jedis.hset("device:info:1001", "status", "online");

        System.out.println("  HGET device:info:1001 name = " + jedis.hget("device:info:1001", "name"));

        // HGETALL
        Map<String, String> info = jedis.hgetAll("device:info:1001");
        System.out.println("  HGETALL device:info:1001   = " + info);

        // HEXISTS / HDEL
        System.out.println("  HEXISTS type               = " + jedis.hexists("device:info:1001", "type"));
        jedis.hdel("device:info:1001", "status");
        System.out.println("  After HDEL status          = " + jedis.hgetAll("device:info:1001"));

        // HMSET (批量 set)
        jedis.hset("device:metrics:1001", Map.of("temp", "36.5", "humidity", "60", "rpm", "1500"));
        System.out.println("  HMSET device:metrics       = " + jedis.hgetAll("device:metrics:1001"));

        System.out.println();
    }

    /** 3. List — 队列/时间线：报警消息队列、操作日志。 */
    private static void demoList(Jedis jedis) {
        System.out.println("=== 3. List ===");

        // LPUSH / RPUSH
        jedis.lpush("alarm:queue", "ALARM-003 振动异常", "ALARM-002 温度过高");
        jedis.rpush("alarm:queue", "ALARM-004 压力过低");

        // LRANGE
        List<String> queue = jedis.lrange("alarm:queue", 0, -1);
        System.out.println("  LRANGE alarm:queue         = " + queue);

        // LLEN
        System.out.println("  LLEN alarm:queue           = " + jedis.llen("alarm:queue"));

        // LPOP / RPOP
        String firstAlarm = jedis.lpop("alarm:queue");
        System.out.println("  LPOP (最先入队)             = " + firstAlarm);

        // LTRIM — 保留最近 N 条（日志截断）
        jedis.ltrim("alarm:queue", 0, 1);  // 保留前 2 条
        System.out.println("  After LTRIM(0,1)           = " + jedis.lrange("alarm:queue", 0, -1));

        System.out.println();
    }

    /** 4. Set — 无序不重复：设备标签、在线设备集合。 */
    private static void demoSet(Jedis jedis) {
        System.out.println("=== 4. Set ===");

        // SADD / SMEMBERS
        jedis.sadd("device:tags:A区", "温度传感器", "湿度传感器", "压力传感器");
        jedis.sadd("device:tags:B区", "温度传感器", "振动传感器", "流量传感器");

        Set<String> tagsA = jedis.smembers("device:tags:A区");
        System.out.println("  SMEMBERS A区               = " + tagsA);

        // SINTER — 交集（两个区域共有传感器类型）
        Set<String> common = jedis.sinter("device:tags:A区", "device:tags:B区");
        System.out.println("  SINTER(A区 ∩ B区)           = " + common);

        // SUNION — 并集
        Set<String> all = jedis.sunion("device:tags:A区", "device:tags:B区");
        System.out.println("  SUNION(A区 ∪ B区)           = " + all);

        // SDIFF — 差集（A区有B区没有）
        Set<String> diff = jedis.sdiff("device:tags:A区", "device:tags:B区");
        System.out.println("  SDIFF(A区 - B区)            = " + diff);

        // SISMEMBER / SCARD
        System.out.println("  SISMEMBER 温度传感器        = " + jedis.sismember("device:tags:A区", "温度传感器"));
        System.out.println("  SCARD A区                   = " + jedis.scard("device:tags:A区"));

        System.out.println();
    }

    /** 5. ZSet — 有序集合：设备评分、报警优先级排序。 */
    private static void demoZSet(Jedis jedis) {
        System.out.println("=== 5. ZSet (Sorted Set) ===");

        // ZADD
        jedis.zadd("device:priority", 95, "device-1001");  // 高温告警 — 最高优先级
        jedis.zadd("device:priority", 60, "device-1002");  // 湿度偏高
        jedis.zadd("device:priority", 30, "device-1003");  // 一般日志
        jedis.zadd("device:priority", 85, "device-1004");  // 振动超标

        // ZRANGE — 按分数升序
        List<String> asc = jedis.zrange("device:priority", 0, -1);
        System.out.println("  ZRANGE (分数升序)           = " + asc);

        // ZREVRANGE — 按分数降序（优先处理高优先级）
        List<String> desc = jedis.zrevrange("device:priority", 0, -1);
        System.out.println("  ZREVRANGE (分数降序)        = " + desc);

        // ZRANK — 排名（升序，0 开始）
        System.out.println("  ZRANK device-1001 (升序)    = " + jedis.zrank("device:priority", "device-1001"));

        // ZSCORE — 获取分数
        System.out.println("  ZSCORE device-1001         = " + jedis.zscore("device:priority", "device-1001"));

        // ZRANGEBYSCORE — 按分数范围查询
        List<String> highPriority = jedis.zrangeByScore("device:priority", 80, 100);
        System.out.println("  ZRANGEBYSCORE [80,100]     = " + highPriority + " ← 高优先级告警");

        // ZCARD — 总数
        System.out.println("  ZCARD                      = " + jedis.zcard("device:priority"));

        System.out.println();
    }
}
