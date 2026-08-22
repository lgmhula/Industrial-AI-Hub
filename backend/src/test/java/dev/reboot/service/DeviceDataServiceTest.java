package dev.reboot.service;

import dev.reboot.dto.DataReportRequest;
import dev.reboot.dto.DeviceDataStats;
import dev.reboot.entity.Device;
import dev.reboot.entity.DeviceData;
import dev.reboot.enums.ErrorCode;
import dev.reboot.enums.RoleEnum;
import dev.reboot.exception.BusinessException;
import dev.reboot.mapper.DeviceDataMapper;
import dev.reboot.mapper.DeviceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DeviceDataService 单元测试（P1-01：站点作用域）。
 *
 * @author hula0710
 * @since 2026-08-02
 */
@ExtendWith(MockitoExtension.class)
class DeviceDataServiceTest {

    @Mock private DeviceDataMapper deviceDataMapper;
    @Mock private AlarmDetector alarmDetector;
    @Mock private DeviceMapper deviceMapper;
    @Mock private SiteAccessService siteAccessService;
    @InjectMocks private DeviceDataService deviceDataService;

    private static final Long USER_ID = 1L;

    private Device newDevice(Long id) {
        Device d = new Device();
        d.setId(id); d.setSiteId(10L); d.setDeviceName("d");
        return d;
    }

    @Test
    void listByDevice_shouldReturnData() {
        DeviceData d = new DeviceData(); d.setId(1L); d.setDataType("TEMP");
        when(deviceMapper.findById(1L)).thenReturn(newDevice(1L));
        when(deviceDataMapper.findByDeviceId(1L)).thenReturn(List.of(d));
        List<DeviceData> result = deviceDataService.listByDevice(1L, USER_ID);
        assertEquals(1, result.size());
        verify(siteAccessService).assertSiteAccess(USER_ID, 10L, RoleEnum.VIEWER);
    }

    @Test
    void listByDevice_noSiteAccess_shouldThrowForbidden() {
        when(deviceMapper.findById(1L)).thenReturn(newDevice(1L));
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无权访问该站点资源"))
                .when(siteAccessService).assertSiteAccess(USER_ID, 10L, RoleEnum.VIEWER);
        assertThrows(BusinessException.class, () -> deviceDataService.listByDevice(1L, USER_ID));
        verify(deviceDataMapper, never()).findByDeviceId(anyLong());
    }

    @Test
    void listByDevice_shouldReturnEmpty() {
        when(deviceMapper.findById(99L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> deviceDataService.listByDevice(99L, USER_ID));
    }

    @Test
    void getLatest_shouldReturnLatest() {
        DeviceData d = new DeviceData(); d.setId(1L);
        when(deviceMapper.findById(1L)).thenReturn(newDevice(1L));
        when(deviceDataMapper.findLatest(1L, "TEMP")).thenReturn(d);
        assertNotNull(deviceDataService.getLatest(1L, "TEMP", USER_ID));
    }

    @Test
    void getLatest_shouldReturnNull() {
        when(deviceMapper.findById(1L)).thenReturn(newDevice(1L));
        when(deviceDataMapper.findLatest(1L, "TEMP")).thenReturn(null);
        assertNull(deviceDataService.getLatest(1L, "TEMP", USER_ID));
    }

    @Test
    void report_shouldInsertAndCheckAlarms() {
        DataReportRequest req = new DataReportRequest();
        req.setDataType("TEMPERATURE"); req.setDataValue(new BigDecimal("45.0")); req.setUnit("°C");

        when(deviceMapper.findById(1L)).thenReturn(newDevice(1L));
        when(alarmDetector.check(eq(1L), eq("TEMPERATURE"), any(BigDecimal.class)))
                .thenReturn(Collections.emptyList());

        DeviceData result = deviceDataService.report(1L, req, USER_ID);

        assertNotNull(result);
        assertEquals("TEMPERATURE", result.getDataType());
        verify(siteAccessService).assertSiteAccess(USER_ID, 10L, RoleEnum.OPERATOR);
        verify(deviceDataMapper).insert(any(DeviceData.class));
    }

    @Test
    void report_noSiteAccess_shouldThrowForbidden() {
        DataReportRequest req = new DataReportRequest();
        req.setDataType("TEMPERATURE"); req.setDataValue(new BigDecimal("45.0")); req.setUnit("°C");

        when(deviceMapper.findById(1L)).thenReturn(newDevice(1L));
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无权访问该站点资源"))
                .when(siteAccessService).assertSiteAccess(USER_ID, 10L, RoleEnum.OPERATOR);

        assertThrows(BusinessException.class, () -> deviceDataService.report(1L, req, USER_ID));
        verify(deviceDataMapper, never()).insert(any());
    }

    @Test
    void listByTimeRange_shouldDelegate() {
        DeviceData d = new DeviceData(); d.setId(1L);
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = LocalDateTime.now();
        when(deviceMapper.findById(1L)).thenReturn(newDevice(1L));
        when(deviceDataMapper.findByTimeRange(1L, "TEMP", start, end)).thenReturn(List.of(d));
        List<DeviceData> result = deviceDataService.listByTimeRange(1L, "TEMP", start, end, USER_ID);
        assertEquals(1, result.size());
    }

    @Test
    void getStats_shouldReturnAggregated() {
        Map<String, Object> raw = Map.of("avg", new BigDecimal("25.5"), "min", new BigDecimal("20.0"),
                "max", new BigDecimal("30.0"), "cnt", 100L);
        when(deviceMapper.findById(1L)).thenReturn(newDevice(1L));
        when(deviceDataMapper.aggregate(anyLong(), eq("TEMP"), any(), any())).thenReturn(raw);

        DeviceDataStats stats = deviceDataService.getStats(1L, "TEMP", null, null, USER_ID);

        assertEquals(new BigDecimal("25.5"), stats.getAvg());
        assertEquals(new BigDecimal("20.0"), stats.getMin());
        assertEquals(new BigDecimal("30.0"), stats.getMax());
        assertEquals(100L, stats.getCount());
    }
}
