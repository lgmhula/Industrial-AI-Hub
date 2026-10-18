package dev.reboot.security;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 Redis 下的多实例限流验证。
 *
 * <p>显式执行：
 * {@code RUN_RATE_LIMIT_IT=true ./mvnw test -Dtest=RateLimitStoreRedisIT}。</p>
 */
@EnabledIfEnvironmentVariable(named = "RUN_RATE_LIMIT_IT", matches = "true")
class RateLimitStoreRedisIT {

    private static LettuceConnectionFactory sharedFactory;
    private static StringRedisTemplate sharedRedis;

    @BeforeAll
    static void setUpRedis() {
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(
                System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1"),
                Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379")));
        String password = System.getenv("REDIS_PASSWORD");
        if (password != null && !password.isBlank()) {
            standalone.setPassword(password);
        }
        sharedFactory = new LettuceConnectionFactory(standalone);
        sharedFactory.afterPropertiesSet();
        sharedFactory.start();
        sharedRedis = new StringRedisTemplate(sharedFactory);
        sharedRedis.afterPropertiesSet();
        sharedFactory.getConnection().ping();
    }

    @AfterAll
    static void tearDownRedis() {
        if (sharedFactory != null) {
            sharedFactory.destroy();
        }
    }

    @Test
    void twoStores_shareOneBucketInsteadOfDoublingQuota() {
        String key = uniqueKey("shared");
        RateLimitStore instanceA = new RateLimitStore(sharedRedis, "redis");
        RateLimitStore instanceB = new RateLimitStore(sharedRedis, "redis");
        try {
            assertTrue(instanceA.tryAcquire(key, 1.0, 0L), "实例 A 应取得初始唯一令牌");
            assertFalse(instanceB.tryAcquire(key, 1.0, 0L), "实例 B 不应获得第二个初始令牌");

            sleep(1_150L);
            assertTrue(instanceB.tryAcquire(key, 1.0, 0L), "令牌桶补充后应允许下一次请求");
        } finally {
            deleteBucket(key);
        }
    }

    @Test
    void redisFailure_fallsBackToIndependentLocalBucket() {
        RedisStandaloneConfiguration unreachable = new RedisStandaloneConfiguration("127.0.0.1", 1);
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(200))
                .shutdownTimeout(Duration.ZERO)
                .build();
        LettuceConnectionFactory brokenFactory =
                new LettuceConnectionFactory(unreachable, clientConfig);
        brokenFactory.afterPropertiesSet();
        brokenFactory.start();
        try {
            StringRedisTemplate brokenRedis = new StringRedisTemplate(brokenFactory);
            brokenRedis.afterPropertiesSet();
            RateLimitStore store = new RateLimitStore(brokenRedis, "redis");
            String key = uniqueKey("fallback");

            long start = System.nanoTime();
            assertTrue(store.tryAcquire(key, 1.0, 0L), "Redis 失败时首次请求应回退本地桶放行");
            assertFalse(store.tryAcquire(key, 1.0, 0L), "后续请求应继续受本地桶限制");
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            assertTrue(elapsedMs < 2_000L, "故障回退应有界，实际耗时 " + elapsedMs + "ms");
        } finally {
            brokenFactory.destroy();
        }
    }

    @Test
    void rejectionLogElection_isSharedAcrossInstances() {
        String path = "/api/it/" + UUID.randomUUID();
        RateLimitStore instanceA = new RateLimitStore(sharedRedis, "redis");
        RateLimitStore instanceB = new RateLimitStore(sharedRedis, "redis");

        assertTrue(instanceA.shouldLogRejectionInRedis(path, 5_000L));
        assertFalse(instanceB.shouldLogRejectionInRedis(path, 5_000L));
    }

    private static String uniqueKey(String purpose) {
        return "it:" + purpose + ":" + UUID.randomUUID();
    }

    private static void deleteBucket(String key) {
        sharedRedis.delete(List.of("industrial-ai-hub:rate-limit:bucket:" + key));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
