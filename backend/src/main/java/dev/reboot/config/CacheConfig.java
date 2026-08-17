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

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
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
 * ObjectMapper 见 {@link #createCacheObjectMapper()}：注册 JavaTimeModule +
 * 受限类型信息（BasicPolymorphicTypeValidator allowlist），不沿用 RedisConfig 中
 * 弃用的 LaissezFaireSubTypeValidator（无限制反序列化风险，见安全备忘）。</p>
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
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith("cache:")
                .entryTtl(DEFAULT_TTL)
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(createCacheObjectMapper())))
                .disableCachingNullValues();
        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaults)
                .build();
    }

    /**
     * 缓存值序列化 ObjectMapper —— 写/读两侧共用同一配置。
     *
     * <p>三个要点（缺一不可，否则缓存链路 500）：</p>
     * <ol>
     *   <li>{@link JavaTimeModule}：UserVO/DeviceVO/DeviceData 含 {@code LocalDateTime}，
     *       缺失 → 写缓存序列化失败；</li>
     *   <li>{@code activateDefaultTyping(NON_FINAL)}：写入 {@code @class} 类型信息；
     *       缺失 → 缓存读命中反序列化为 {@code LinkedHashMap} →
     *       ClassCastException（读 500）；</li>
     *   <li>类型校验器使用受限 {@link BasicPolymorphicTypeValidator}（仅放行项目
     *       dto/entity 与 JDK 集合/时间/数值），不沿用 RedisConfig 弃用的
     *       LaissezFaireSubTypeValidator（无限制反序列化风险，见安全备忘）。</li>
     * </ol>
     */
    public static ObjectMapper createCacheObjectMapper() {
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("dev.reboot.dto.")
                .allowIfSubType("dev.reboot.entity.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.math.")
                .allowIfSubType("java.lang.")
                .build();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        return objectMapper;
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
