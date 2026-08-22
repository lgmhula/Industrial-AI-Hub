package dev.reboot.service;

import dev.reboot.config.CacheConfig;
import dev.reboot.dto.DeviceDTO;
import dev.reboot.dto.DeviceVO;
import dev.reboot.entity.Device;
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

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = DeviceServiceCacheTest.CacheTestConfig.class)
class DeviceServiceCacheTest {

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private DeviceMapper deviceMapper;

    @Configuration
    @EnableCaching
    static class CacheTestConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(CacheConfig.CACHE_DEVICE_DETAIL);
        }

        @Bean
        DeviceMapper deviceMapper() { return mock(DeviceMapper.class); }

        @Bean
        SiteAccessService siteAccessService() { return mock(SiteAccessService.class); }

        @Bean
        DeviceService deviceService(DeviceMapper mapper, SiteAccessService siteAccess) {
            return new DeviceService(mapper, siteAccess);
        }
    }

    private Device newDevice(Long id, String name, String code) {
        Device d = new Device();
        d.setId(id);
        d.setSiteId(10L);
        d.setDeviceName(name);
        d.setDeviceCode(code);
        d.setDeviceType("泵");
        d.setStatus(1);
        d.setIsDeleted(0);
        d.setCreatedAt(LocalDateTime.now());
        d.setUpdatedAt(LocalDateTime.now());
        return d;
    }

    private DeviceDTO newDTO(String name, String code) {
        DeviceDTO dto = new DeviceDTO();
        dto.setDeviceName(name);
        dto.setDeviceCode(code);
        dto.setDeviceType("泵");
        dto.setStatus(1);
        return dto;
    }

    @Test
    void getById_shouldHitCacheOnSecondCall() {
        Device device = newDevice(201L, "泵A", "PUMP-001");
        when(deviceMapper.findById(201L)).thenReturn(device);

        DeviceVO first = deviceService.getById(201L, 1L);
        DeviceVO second = deviceService.getById(201L, 1L);

        assertEquals("PUMP-001", first.getDeviceCode());
        assertEquals("PUMP-001", second.getDeviceCode());
        verify(deviceMapper, times(1)).findById(201L);
    }

    @Test
    void getById_differentIds_shouldNotShareCache() {
        Device d202 = newDevice(202L, "泵A", "PUMP-A");
        Device d203 = newDevice(203L, "阀B", "VALVE-B");
        when(deviceMapper.findById(202L)).thenReturn(d202);
        when(deviceMapper.findById(203L)).thenReturn(d203);

        deviceService.getById(202L, 1L);
        deviceService.getById(203L, 1L);

        verify(deviceMapper, times(1)).findById(202L);
        verify(deviceMapper, times(1)).findById(203L);
    }

    @Test
    void update_shouldEvictCache() {
        Device device = newDevice(204L, "泵A", "PUMP-004");
        when(deviceMapper.findById(204L)).thenReturn(device);

        deviceService.getById(204L, 1L);
        deviceService.update(204L, newDTO("泵A-plus", "PUMP-004"), 1L);
        deviceService.getById(204L, 1L);

        // 3 calls: initial getById, update's internal findById, post-eviction getById
        verify(deviceMapper, times(3)).findById(204L);
    }

    @Test
    void delete_shouldEvictCache() {
        Device device = newDevice(205L, "泵A", "PUMP-005");
        when(deviceMapper.findById(205L)).thenReturn(device);
        when(deviceMapper.softDeleteById(205L)).thenReturn(1);

        deviceService.getById(205L, 1L);
        deviceService.delete(205L);
        deviceService.getById(205L, 1L);

        // 3 calls: initial getById, delete's internal findById, post-eviction getById
        verify(deviceMapper, times(3)).findById(205L);
    }
}
