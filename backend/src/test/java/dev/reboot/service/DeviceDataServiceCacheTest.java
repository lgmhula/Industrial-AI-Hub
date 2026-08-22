package dev.reboot.service;

import dev.reboot.config.CacheConfig;
import dev.reboot.dto.DataReportRequest;
import dev.reboot.dto.DeviceDataStats;
import dev.reboot.entity.Device;
import dev.reboot.entity.DeviceData;
import dev.reboot.mapper.DeviceDataMapper;
import dev.reboot.mapper.DeviceMapper;
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

    @Autowired
    private DeviceMapper deviceMapper;

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
        DeviceMapper deviceMapper() { return mock(DeviceMapper.class); }

        @Bean
        SiteAccessService siteAccessService() { return mock(SiteAccessService.class); }

        @Bean
        DeviceDataService deviceDataService(DeviceDataMapper mapper, AlarmDetector detector,
                                            DeviceMapper deviceMapper, SiteAccessService siteAccess) {
            return new DeviceDataService(mapper, detector, deviceMapper, siteAccess);
        }
    }

    private Device device(Long id) {
        Device d = new Device();
        d.setId(id); d.setSiteId(10L); d.setDeviceName("d");
        return d;
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
        when(deviceMapper.findById(101L)).thenReturn(device(101L));
        when(deviceDataMapper.aggregate(101L, "temperature", t1(), t2())).thenReturn(statsRaw());

        deviceDataService.getStats(101L, "temperature", t1(), t2(), 1L);
        deviceDataService.getStats(101L, "temperature", t1(), t2(), 1L);

        verify(deviceDataMapper, times(1)).aggregate(101L, "temperature", t1(), t2());
    }

    @Test
    void getStats_differentKeys_shouldNotShareCache() {
        when(deviceMapper.findById(102L)).thenReturn(device(102L));
        when(deviceDataMapper.aggregate(102L, "temperature", t1(), t2())).thenReturn(statsRaw());
        when(deviceDataMapper.aggregate(102L, "humidity", t1(), t2())).thenReturn(statsRaw());

        deviceDataService.getStats(102L, "temperature", t1(), t2(), 1L);
        deviceDataService.getStats(102L, "humidity", t1(), t2(), 1L);

        verify(deviceDataMapper, times(1)).aggregate(102L, "temperature", t1(), t2());
        verify(deviceDataMapper, times(1)).aggregate(102L, "humidity", t1(), t2());
    }

    @Test
    void listByTimeRange_shouldHitCacheOnSecondCall() {
        when(deviceMapper.findById(103L)).thenReturn(device(103L));
        when(deviceDataMapper.findByTimeRange(103L, "temperature", t1(), t2())).thenReturn(List.of());

        deviceDataService.listByTimeRange(103L, "temperature", t1(), t2(), 1L);
        deviceDataService.listByTimeRange(103L, "temperature", t1(), t2(), 1L);

        verify(deviceDataMapper, times(1)).findByTimeRange(103L, "temperature", t1(), t2());
    }

    @Test
    void report_shouldEvictAggregateAndRangeCaches() {
        when(deviceMapper.findById(104L)).thenReturn(device(104L));
        when(deviceDataMapper.aggregate(104L, "temperature", t1(), t2())).thenReturn(statsRaw());
        when(deviceDataMapper.findByTimeRange(104L, "temperature", t1(), t2())).thenReturn(List.of());

        deviceDataService.getStats(104L, "temperature", t1(), t2(), 1L);
        deviceDataService.listByTimeRange(104L, "temperature", t1(), t2(), 1L);

        DataReportRequest req = new DataReportRequest();
        req.setDataType("temperature");
        req.setDataValue(new BigDecimal("22.5"));
        req.setUnit("C");
        deviceDataService.report(104L, req, 1L);

        deviceDataService.getStats(104L, "temperature", t1(), t2(), 1L);
        deviceDataService.listByTimeRange(104L, "temperature", t1(), t2(), 1L);

        verify(deviceDataMapper, times(2)).aggregate(104L, "temperature", t1(), t2());
        verify(deviceDataMapper, times(2)).findByTimeRange(104L, "temperature", t1(), t2());
    }
}
