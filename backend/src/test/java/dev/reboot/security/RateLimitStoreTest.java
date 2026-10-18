package dev.reboot.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitStoreTest {

    @Mock private StringRedisTemplate redis;

    @Test
    void localMode_shouldNotCallRedis() {
        RateLimitStore store = new RateLimitStore(redis, "local");

        assertTrue(store.tryAcquire("local-key", 10.0, 0L));
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
        assertNull(store.shouldLogRejectionInRedis("/api/devices", 5_000L));
    }

    @Test
    void redisMode_shouldUseSharedScriptResult() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);
        RateLimitStore store = new RateLimitStore(redis, "redis");

        assertTrue(store.tryAcquire("shared-key", 10.0, 0L));
        verify(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    void redisMode_waitBeyondTimeout_shouldRejectImmediately() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(-1000L);
        RateLimitStore store = new RateLimitStore(redis, "redis");

        long start = System.nanoTime();
        boolean acquired = store.tryAcquire("shared-key", 1.0, 50L);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertFalse(acquired);
        assertTrue(elapsedMs < 300, "预计等待超过 timeout 时应快速拒绝，实际 " + elapsedMs + "ms");
    }

    @Test
    void redisFailure_shouldFallbackToLocalBucket() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new IllegalStateException("redis down"));
        RateLimitStore store = new RateLimitStore(redis, "redis");

        assertTrue(store.tryAcquire("fallback-key", 1.0, 0L), "首次 Redis 失败后应由本地桶放行");
        assertFalse(store.tryAcquire("fallback-key", 1.0, 0L), "后续请求继续在本地桶限流");
    }

    @Test
    void rejectionLogWindow_shouldElectOnlyOneInstance() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L, 0L);
        RateLimitStore store = new RateLimitStore(redis, "redis");

        assertTrue(store.shouldLogRejectionInRedis("/api/devices", 5_000L));
        assertFalse(store.shouldLogRejectionInRedis("/api/devices", 5_000L));
    }
}
