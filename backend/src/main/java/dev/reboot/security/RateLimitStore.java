package dev.reboot.security;

import com.google.common.util.concurrent.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 限流令牌桶存储：本地 Guava 与 Redis 原子令牌桶双模式。
 *
 * <p>{@code rate.limit.mode=redis} 时使用 Redis Lua 脚本在多个后端实例间共享额度；
 * Redis 不可用时单次请求立即回退到本地 Guava 桶，避免限流组件拖垮业务。</p>
 */
@Component
public class RateLimitStore {

    private static final Logger log = LoggerFactory.getLogger(RateLimitStore.class);
    private static final String KEY_PREFIX = "industrial-ai-hub:rate-limit:";
    private static final long REDIS_WARN_INTERVAL_MS = 5_000L;
    private static final long REDIS_KEY_TTL_SECONDS = 120L;

    /**
     * 返回 1 表示已取得令牌；返回负数表示距离下一个令牌的预计等待毫秒数。
     * 时间基准使用 Redis TIME，避免多实例本地时钟漂移。
     */
    private static final DefaultRedisScript<Long> TOKEN_BUCKET_SCRIPT = new DefaultRedisScript<>(
            """
            local key = KEYS[1]
            local rate = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local ttl = tonumber(ARGV[3])
            local state = redis.call('HMGET', key, 'tokens', 'ts')
            local tokens = tonumber(state[1])
            local last = tonumber(state[2])
            local clock = redis.call('TIME')
            local now = tonumber(clock[1]) * 1000000 + tonumber(clock[2])
            if tokens == nil or last == nil then
                tokens = capacity
                last = now
            end
            local elapsed = math.max(0, now - last) / 1000000
            tokens = math.min(capacity, tokens + elapsed * rate)
            if tokens >= 1 then
                tokens = tokens - 1
                redis.call('HSET', key, 'tokens', tokens, 'ts', now)
                redis.call('EXPIRE', key, ttl)
                return 1
            end
            redis.call('HSET', key, 'tokens', tokens, 'ts', now)
            redis.call('EXPIRE', key, ttl)
            return -math.ceil((1 - tokens) / rate * 1000)
            """,
            Long.class
    );

    /** 每个 path 每个窗口只允许一个实例输出拒绝日志。 */
    private static final DefaultRedisScript<Long> REJECTION_LOG_SCRIPT = new DefaultRedisScript<>(
            """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
                return 1
            end
            return 0
            """,
            Long.class
    );

    private final Map<String, RateLimiter> localLimiters = new ConcurrentHashMap<>();
    private final AtomicLong lastRedisWarnAt = new AtomicLong(0L);
    private final StringRedisTemplate redis;
    private final boolean redisMode;

    public RateLimitStore(@Nullable StringRedisTemplate redis,
                          @Value("${rate.limit.mode:local}") String mode) {
        this.redis = redis;
        this.redisMode = "redis".equalsIgnoreCase(mode == null ? "" : mode.trim());
        if (redisMode && redis == null) {
            log.warn("rate.limit.mode=redis 但 StringRedisTemplate 不可用，将使用本地限流");
        } else {
            log.info("限流模式: {}", redisMode ? "redis" : "local");
        }
    }

    /**
     * 尝试在指定超时内取得令牌。
     *
     * <p>本地模式与 Guava {@code tryAcquire(timeout)} 语义一致；Redis 模式按脚本返回的
     * 下次令牌等待时间做有界重试，预计等待超过剩余超时时立即拒绝。</p>
     */
    public boolean tryAcquire(String key, double permitsPerSecond, long timeoutMs) {
        double permits = permitsPerSecond > 0 ? permitsPerSecond : 1.0;
        long timeout = Math.max(0L, timeoutMs);
        if (useRedis()) {
            try {
                return tryAcquireRedis(key, permits, timeout);
            } catch (RuntimeException e) {
                warnRedisFallback(e);
            }
        }
        return tryAcquireLocal(key, permits, timeout);
    }

    /**
     * Redis 模式下用 Redis 窗口键决定当前 path 窗口是否由本实例输出日志。
     * 本地模式返回 {@code null}，调用方继续使用原有内存节流逻辑。
     */
    @Nullable
    public Boolean shouldLogRejectionInRedis(String path, long intervalMs) {
        if (!useRedis()) {
            return null;
        }
        if (intervalMs <= 0L) {
            return Boolean.TRUE;
        }
        long window = System.currentTimeMillis() / intervalMs;
        String key = KEY_PREFIX + "log:" + path + ":" + window;
        try {
            Long result = redis.execute(REJECTION_LOG_SCRIPT, List.of(key),
                    String.valueOf(intervalMs));
            return result != null && result == 1L;
        } catch (RuntimeException e) {
            warnRedisFallback(e);
            return null;
        }
    }

    public boolean isRedisMode() {
        return redisMode;
    }

    private boolean useRedis() {
        return redisMode && redis != null;
    }

    private boolean tryAcquireRedis(String key, double permits, long timeoutMs) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        String redisKey = KEY_PREFIX + "bucket:" + key;
        double capacity = Math.max(1.0, Math.ceil(permits));
        while (true) {
            Long result = redis.execute(TOKEN_BUCKET_SCRIPT, List.of(redisKey),
                    format(permits), format(capacity), String.valueOf(REDIS_KEY_TTL_SECONDS));
            if (result != null && result == 1L) {
                return true;
            }
            long waitMs = result == null ? 1L : Math.max(1L, -result);
            long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            if (timeoutMs <= 0L || remainingMs <= 0L || waitMs > remainingMs) {
                return false;
            }
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    private boolean tryAcquireLocal(String key, double permits, long timeoutMs) {
        RateLimiter limiter = localLimiters.computeIfAbsent(key, ignored -> RateLimiter.create(permits));
        if (Math.abs(limiter.getRate() - permits) > 0.001) {
            limiter.setRate(permits);
        }
        if (timeoutMs <= 0L) {
            return limiter.tryAcquire();
        }
        return limiter.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS);
    }

    private void warnRedisFallback(RuntimeException e) {
        long now = System.currentTimeMillis();
        long last = lastRedisWarnAt.get();
        if (now - last >= REDIS_WARN_INTERVAL_MS && lastRedisWarnAt.compareAndSet(last, now)) {
            log.warn("Redis 限流不可用，本次请求回退本地限流: {}", e.getMessage());
        }
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }
}
