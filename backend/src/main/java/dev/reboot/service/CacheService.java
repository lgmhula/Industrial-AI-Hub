package dev.reboot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 统一缓存服务 —— 提供带防穿透/击穿/雪崩的缓存读写。
 *
 * <h3>核心方法</h3>
 * <table>
 *   <tr><th>方法</th><th>场景</th><th>缓存策略</th></tr>
 *   <tr><td>getOrFetch</td><td>读：缓存命中→返回，未命中→查DB→写缓存</td><td>防止击穿</td></tr>
 *   <tr><td>getOrFetchWithMutex</td><td>读：热点Key重建时加互斥锁</td><td>防止击穿</td></tr>
 *   <tr><td>put</td><td>写：DB写成功后更新缓存</td><td>Cache-Aside</td></tr>
 *   <tr><td>evict</td><td>删：数据删除后清除缓存</td><td>避免脏数据</td></tr>
 * </table>
 *
 * <h3>随机 TTL 策略（防雪崩）</h3>
 * <p>所有 set 操作在基础 TTL 上叠加 ±20% 随机偏移，避免大量 Key 同时过期。</p>
 *
 * @author hula0710
 * @since 2026-08-04 (Day 44-45)
 */
@Service
@Profile("!test")
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redis;

    public CacheService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Cache-Aside 读模式：先从缓存取，未命中则查 DB 并回写缓存。
     *
     * @param key      缓存 Key
     * @param ttl      过期时间
     * @param supplier DB 查询函数
     * @return 缓存值或 DB 查询结果
     */
    public String getOrFetch(String key, Duration ttl, Supplier<String> supplier) {
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            log.debug("缓存命中 key={}", key);
            return cached;
        }
        log.debug("缓存未命中 key={}，从 DB 加载", key);
        String value = supplier.get();
        if (value != null) {
            long jitteredSeconds = jitter(ttl.getSeconds());
            redis.opsForValue().set(key, value, Duration.ofSeconds(jitteredSeconds));
            log.debug("缓存回写 key={} ttl={}s", key, jitteredSeconds);
        }
        return value;
    }

    /**
     * 带互斥锁的缓存读取 —— 防止热点 Key 失效时大量请求打到 DB。
     *
     * <p>使用 SETNX 实现简易分布式互斥锁。</p>
     */
    public String getOrFetchWithMutex(String key, Duration ttl, Supplier<String> supplier) {
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            return cached;
        }

        String lockKey = "lock:" + key;
        Boolean locked = redis.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofSeconds(10));

        if (Boolean.TRUE.equals(locked)) {
            try {
                // Double-check：获取锁后再次检查缓存
                cached = redis.opsForValue().get(key);
                if (cached != null) {
                    return cached;
                }
                String value = supplier.get();
                if (value != null) {
                    long jitteredSeconds = jitter(ttl.getSeconds());
                    redis.opsForValue().set(key, value, Duration.ofSeconds(jitteredSeconds));
                    log.info("互斥锁重建缓存 key={} ttl={}s", key, jitteredSeconds);
                }
                return value;
            } finally {
                redis.delete(lockKey);
            }
        } else {
            // 未获得锁，等待后重试读缓存
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return redis.opsForValue().get(key);
        }
    }

    /** Cache-Aside 写模式：更新缓存。 */
    public void put(String key, String value, Duration ttl) {
        long jitteredSeconds = jitter(ttl != null ? ttl.getSeconds() : DEFAULT_TTL.getSeconds());
        redis.opsForValue().set(key, value, Duration.ofSeconds(jitteredSeconds));
        log.debug("缓存写入 key={} ttl={}s", key, jitteredSeconds);
    }

    /** 删除缓存 Key。 */
    public void evict(String... keys) {
        redis.delete(java.util.List.of(keys));
        log.debug("缓存删除 keys={}", java.util.Arrays.toString(keys));
    }

    /** 是否存在 Key。 */
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redis.hasKey(key));
    }

    /** 设置过期时间。 */
    public boolean expire(String key, Duration ttl) {
        return Boolean.TRUE.equals(redis.expire(key, ttl));
    }

    /**
     * 随机 TTL 偏移 —— ±20%，防止缓存雪崩。
     * <p>例如基础 TTL 600s → 实际 480~720s。</p>
     */
    static long jitter(long baseSeconds) {
        double factor = 0.8 + ThreadLocalRandom.current().nextDouble() * 0.4;
        return Math.max(1, (long) (baseSeconds * factor));
    }
}
