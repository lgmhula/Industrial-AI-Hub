package dev.reboot.service;

import dev.reboot.config.CacheConfig;
import dev.reboot.dto.DataReportRequest;
import dev.reboot.dto.DeviceDataStats;
import dev.reboot.entity.DeviceData;
import dev.reboot.mapper.DeviceDataMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = DeviceDataServiceCacheTest.CacheTestConfig.class)
class DeviceDataServiceCacheTest {

    @Autowired
    private DeviceDataService deviceDataService;

    @Autowired
    private DeviceDataMapper deviceDataMapper;

    @Configuration
    @EnableCaching
    static class CacheTestConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(
                    CacheConfig.CACHE_DEVICE_STATS, CacheConfig.CACHE_DEVICE_RANGE);
        }

        @Bean
        DeviceDataMapper deviceDataMapper() { return mock(DeviceDataMapper.class); }

        @Bean
        AlarmDetector alarmDetector() { return mock(AlarmDetector.class); }

        @Bean
        DeviceDataService deviceDataService(DeviceDataMapper mapper, AlarmDetector detector) {
            return new DeviceDataService(mapper, detector);
        }
    }

    private LocalDateTime t1() { return LocalDateTime.of(2026, 1, 1, 0, 0); }
    private LocalDateTime t2() { return LocalDateTime.of(2026, 1, 1, 23, 59); }

    private Map<String, Object> statsRaw() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("avg", BigDecimal.ONE);
        raw.put("min", BigDecimal.ZERO);
        raw.put("max", BigDecimal.TEN);
        raw.put("cnt", 100L);
        return raw;
    }

    @Test
    void getStats_shouldHitCacheOnSecondCall() {
        when(deviceDataMapper.aggregate(101L, "temperature", t1(), t2())).thenReturn(statsRaw());

        deviceDataService.getStats(101L, "temperature", t1(), t2());
        deviceDataService.getStats(101L, "temperature", t1(), t2());

        verify(deviceDataMapper, times(1)).aggregate(101L, "temperature", t1(), t2());
    }

    @Test
    void getStats_differentKeys_shouldNotShareCache() {
        when(deviceDataMapper.aggregate(102L, "temperature", t1(), t2())).thenReturn(statsRaw());
        when(deviceDataMapper.aggregate(102L, "humidity", t1(), t2())).thenReturn(statsRaw());

        deviceDataService.getStats(102L, "temperature", t1(), t2());
        deviceDataService.getStats(102L, "humidity", t1(), t2());

        verify(deviceDataMapper, times(1)).aggregate(102L, "temperature", t1(), t2());
        verify(deviceDataMapper, times(1)).aggregate(102L, "humidity", t1(), t2());
    }

    @Test
    void listByTimeRange_shouldHitCacheOnSecondCall() {
        when(deviceDataMapper.findByTimeRange(103L, "temperature", t1(), t2())).thenReturn(List.of());

        deviceDataService.listByTimeRange(103L, "temperature", t1(), t2());
        deviceDataService.listByTimeRange(103L, "temperature", t1(), t2());

        verify(deviceDataMapper, times(1)).findByTimeRange(103L, "temperature", t1(), t2());
    }

    @Test
    void report_shouldEvictAggregateAndRangeCaches() {
        when(deviceDataMapper.aggregate(104L, "temperature", t1(), t2())).thenReturn(statsRaw());
        when(deviceDataMapper.findByTimeRange(104L, "temperature", t1(), t2())).thenReturn(List.of());

        deviceDataService.getStats(104L, "temperature", t1(), t2());
        deviceDataService.listByTimeRange(104L, "temperature", t1(), t2());

        DataReportRequest req = new DataReportRequest();
        req.setDataType("temperature");
        req.setDataValue(new BigDecimal("22.5"));
        req.setUnit("C");
        deviceDataService.report(104L, req);

        deviceDataService.getStats(104L, "temperature", t1(), t2());
        deviceDataService.listByTimeRange(104L, "temperature", t1(), t2());

        verify(deviceDataMapper, times(2)).aggregate(104L, "temperature", t1(), t2());
        verify(deviceDataMapper, times(2)).findByTimeRange(104L, "temperature", t1(), t2());
    }
}
