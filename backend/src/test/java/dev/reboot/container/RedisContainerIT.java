package dev.reboot.container;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.Jedis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Redis Testcontainers 集成测试 —— 验证 Jedis 对真实 Redis 的基础操作。
 *
 * <p>使用 Testcontainers 自动拉起 Redis 7 容器，无需手动启动。
 * Docker 不可用时自动跳过（{@code disabledWithoutDocker = true}）。</p>
 *
 * <p>覆盖项目缓存模式所依赖的 Redis 原语：</p>
 * <ul>
 *   <li>SET / GET / DEL — CacheService 读写删</li>
 *   <li>SETNX — 分布式互斥锁（getOrFetchWithMutex）</li>
 *   <li>EXPIRE / TTL — 随机 TTL 防雪崩</li>
 * </ul>
 */
@Testcontainers(disabledWithoutDocker = true)
class RedisContainerIT {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private Jedis connect() {
        return new Jedis(REDIS.getHost(), REDIS.getMappedPort(6379));
    }

    @Test
    void setGetDel_basicCachePattern() {
        try (Jedis jedis = connect()) {
            jedis.flushDB();

            assertEquals("OK", jedis.set("device:1001", "sensor-data"));
            assertEquals("sensor-data", jedis.get("device:1001"));

            assertEquals(1L, jedis.del("device:1001"));
            assertNull(jedis.get("device:1001"));
        }
    }

    @Test
    void setnx_distributedLockPattern() {
        try (Jedis jedis = connect()) {
            jedis.flushDB();

            assertTrue(jedis.setnx("lock:hot-key", "owner-1") > 0, "首次 SETNX 应成功");
            assertEquals(0L, jedis.setnx("lock:hot-key", "owner-2"), "二次 SETNX 应失败（锁被持有）");
            assertEquals("owner-1", jedis.get("lock:hot-key"));

            jedis.del("lock:hot-key");
            assertTrue(jedis.setnx("lock:hot-key", "owner-2") > 0, "释放后 SETNX 应成功");
        }
    }

    @Test
    void expireTtl_antiAvalanchePattern() {
        try (Jedis jedis = connect()) {
            jedis.flushDB();

            jedis.set("cache:user:1", "payload");
            jedis.expire("cache:user:1", 600);
            long ttl = jedis.ttl("cache:user:1");
            assertTrue(ttl > 0 && ttl <= 600, "TTL 应在 (0, 600] 范围内，实际: " + ttl);

            jedis.persist("cache:user:1");
            assertEquals(-1L, jedis.ttl("cache:user:1"), "PERSIST 后 TTL 应为 -1（永不过期）");
        }
    }
}
