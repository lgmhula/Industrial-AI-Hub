package code.day47;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Day 47 — Spring Cache 注解演示。
 *
 * <p>使用进程内 {@link ConcurrentMapCacheManager} 演示
 * {@code @Cacheable} 命中/未命中与 {@code @CacheEvict} 失效，
 * 生产环境由 {@code CacheConfig} 的 RedisCacheManager 承载。</p>
 *
 * @author hula0710
 * @since 2026-08-04
 */
public class SpringCacheDemo {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(DemoConfig.class)) {
            DeviceQueryService service = ctx.getBean(DeviceQueryService.class);

            System.out.println("Day 47 - Spring Cache Annotations\n");
            System.out.println("--- First call (MISS, DB 执行) ---");
            service.getStats(1L, "temperature");
            System.out.println("--- Second call (HIT, 走缓存) ---");
            service.getStats(1L, "temperature");
            System.out.println("--- Evict ---");
            service.evictStats(1L, "temperature");
            System.out.println("--- After evict (MISS, DB 再次执行) ---");
            service.getStats(1L, "temperature");
        }
    }

    /** 最小 Spring 配置：开启注解缓存 + 进程内 CacheManager。 */
    @Configuration
    @EnableCaching
    static class DemoConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("device-data:stats");
        }

        @Bean
        DeviceQueryService deviceQueryService() {
            return new DeviceQueryService();
        }
    }

    /** 模拟设备数据聚合查询服务。 */
    static class DeviceQueryService {

        private int dbCalls = 0;

        /**
         * 按设备 + 数据类型查询统计。
         *
         * @param deviceId 设备 ID
         * @param dataType 数据类型
         * @return 模拟统计结果
         */
        @Cacheable(cacheNames = "device-data:stats", key = "#deviceId + ':' + #dataType")
        public String getStats(Long deviceId, String dataType) {
            dbCalls++;
            System.out.println("  [DB] query stats device=" + deviceId
                    + " type=" + dataType + " (call #" + dbCalls + ")");
            return deviceId + ":" + dataType + ":avg=25.5";
        }

        /**
         * 数据变化后删除对应缓存。
         *
         * @param deviceId 设备 ID
         * @param dataType 数据类型
         */
        @CacheEvict(cacheNames = "device-data:stats", key = "#deviceId + ':' + #dataType")
        public void evictStats(Long deviceId, String dataType) {
            System.out.println("  [EVICT] device=" + deviceId + " type=" + dataType);
        }
    }
}
