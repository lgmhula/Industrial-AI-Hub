package dev.reboot.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置 —— RedisTemplate / StringRedisTemplate Bean。
 *
 * <h3>序列化策略</h3>
 * <ul>
 *   <li><b>StringRedisTemplate</b>: key/value 均为 String，用于计数器/锁</li>
 *   <li><b>RedisTemplate&lt;String, Object&gt;</b>: <b>已弃用</b>。使用
 *   {@link com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator}
 *   存在反序列化安全风险（OSVDB-2025-002）。生产请使用
 *   {@link CacheConfig} 提供的 {@link org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer}。</li>
 * </ul>
 *
 * <p>自 Day 48 起，项目缓存全部改用 Spring Cache 注解
 * + GenericJackson2JsonRedisSerializer，此处的 objectRedisTemplate 仅保留供学习参考。</p>
 *
 * @author hula0710
 * @since 2026-08-04 (Day 44)
 */
@Configuration
@Profile("!test")
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    /**
     * @deprecated 使用 {@link CacheConfig#redisCacheManager} +
     *             {@link GenericJackson2JsonRedisSerializer} 替代。
     *             LaissezFaireSubTypeValidator 存在反序列化漏洞风险。
     */
    @Bean
    @Deprecated
    public RedisTemplate<String, Object> objectRedisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // Key 用 String
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Value 用 Jackson JSON（支持类型信息，防止反序列化异常）
        ObjectMapper mapper = new ObjectMapper();
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);

        Jackson2JsonRedisSerializer<Object> serializer =
                new Jackson2JsonRedisSerializer<>(mapper, Object.class);
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }
}
