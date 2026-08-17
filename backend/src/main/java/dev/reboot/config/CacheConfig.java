package dev.reboot.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Duration;

/**
 * Spring Cache 注解配置 —— {@code @Cacheable / @CacheEvict} 统一接入点。
 *
 * <h3>策略</h3>
 * <ul>
 *   <li><b>dev/prod</b>: RedisCacheManager，Key 前缀 {@code cache:}，默认 TTL 30 分钟</li>
 *   <li><b>test</b>: ConcurrentMapCacheManager，避免测试依赖 Redis</li>
 * </ul>
 *
 * <p>Value 序列化使用 {@link GenericJackson2JsonRedisSerializer}，
 * 不沿用 RedisConfig 中的 LaissezFaireSubTypeValidator（见安全备忘）。</p>
 *
 * @author hula0710
 * @since 2026-08-04 (Day 47)
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** 设备数据聚合统计缓存。 */
    public static final String CACHE_DEVICE_STATS = "device-data:stats";

    /** 设备数据时间范围查询缓存。 */
    public static final String CACHE_DEVICE_RANGE = "device-data:range";

    /** 设备详情查询缓存 (getById)。 */
    public static final String CACHE_DEVICE_DETAIL = "device:detail";

    /** 用户详情查询缓存 (getById)。 */
    public static final String CACHE_USER_DETAIL = "user:detail";

    /** 默认 TTL：30 分钟。 */
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    /**
     * 生产/开发缓存管理器 —— Redis 实现。
     *
     * @param factory Redis 连接工厂（由 spring-data-redis 自动配置提供）
     * @return RedisCacheManager
     */
    @Bean
    @Profile("!test")
    public RedisCacheManager redisCacheManager(RedisConnectionFactory factory) {
        // ObjectMapper 需注册 JavaTimeModule：UserVO/DeviceVO 含 LocalDateTime，
        // 缺失会导致 @Cacheable 写缓存时序列化失败（500）。
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith("cache:")
                .entryTtl(DEFAULT_TTL)
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper)))
                .disableCachingNullValues();
        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaults)
                .build();
    }

    /**
     * 测试缓存管理器 —— 进程内 Map，保证上下文加载与注解测试不依赖 Redis。
     *
     * @return ConcurrentMapCacheManager
     */
    @Bean
    @Profile("test")
    public CacheManager simpleCacheManager() {
        return new ConcurrentMapCacheManager(
                CACHE_DEVICE_STATS, CACHE_DEVICE_RANGE,
                CACHE_DEVICE_DETAIL, CACHE_USER_DETAIL);
    }
}
