package dev.reboot.service;

import dev.reboot.dto.DataReportRequest;
import dev.reboot.dto.DeviceDataStats;
import dev.reboot.entity.DeviceData;
import dev.reboot.mapper.DeviceDataMapper;
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
 * DeviceDataService 单元测试。
 *
 * @author hula0710
 * @since 2026-08-02
 */
@ExtendWith(MockitoExtension.class)
class DeviceDataServiceTest {

    @Mock private DeviceDataMapper deviceDataMapper;
    @Mock private AlarmDetector alarmDetector;
    @InjectMocks private DeviceDataService deviceDataService;

    @Test
    void listByDevice_shouldReturnData() {
        DeviceData d = new DeviceData(); d.setId(1L); d.setDataType("TEMP");
        when(deviceDataMapper.findByDeviceId(1L)).thenReturn(List.of(d));
        List<DeviceData> result = deviceDataService.listByDevice(1L);
        assertEquals(1, result.size());
    }

    @Test
    void listByDevice_shouldReturnEmpty() {
        when(deviceDataMapper.findByDeviceId(99L)).thenReturn(Collections.emptyList());
        assertTrue(deviceDataService.listByDevice(99L).isEmpty());
    }

    @Test
    void getLatest_shouldReturnLatest() {
        DeviceData d = new DeviceData(); d.setId(1L);
        when(deviceDataMapper.findLatest(1L, "TEMP")).thenReturn(d);
        assertNotNull(deviceDataService.getLatest(1L, "TEMP"));
    }

    @Test
    void getLatest_shouldReturnNull() {
        when(deviceDataMapper.findLatest(1L, "TEMP")).thenReturn(null);
        assertNull(deviceDataService.getLatest(1L, "TEMP"));
    }

    @Test
    void report_shouldInsertAndCheckAlarms() {
        DataReportRequest req = new DataReportRequest();
        req.setDataType("TEMPERATURE"); req.setDataValue(new BigDecimal("45.0")); req.setUnit("°C");

        when(alarmDetector.check(eq(1L), eq("TEMPERATURE"), any(BigDecimal.class)))
                .thenReturn(Collections.emptyList());

        DeviceData result = deviceDataService.report(1L, req);

        assertNotNull(result);
        assertEquals("TEMPERATURE", result.getDataType());
        verify(deviceDataMapper).insert(any(DeviceData.class));
    }

    @Test
    void listByTimeRange_shouldDelegate() {
        DeviceData d = new DeviceData(); d.setId(1L);
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = LocalDateTime.now();
        when(deviceDataMapper.findByTimeRange(1L, "TEMP", start, end)).thenReturn(List.of(d));
        List<DeviceData> result = deviceDataService.listByTimeRange(1L, "TEMP", start, end);
        assertEquals(1, result.size());
    }

    @Test
    void getStats_shouldReturnAggregated() {
        Map<String, Object> raw = Map.of("avg", new BigDecimal("25.5"), "min", new BigDecimal("20.0"),
                "max", new BigDecimal("30.0"), "cnt", 100L);
        when(deviceDataMapper.aggregate(anyLong(), eq("TEMP"), any(), any())).thenReturn(raw);

        DeviceDataStats stats = deviceDataService.getStats(1L, "TEMP", null, null);

        assertEquals(new BigDecimal("25.5"), stats.getAvg());
        assertEquals(new BigDecimal("20.0"), stats.getMin());
        assertEquals(new BigDecimal("30.0"), stats.getMax());
        assertEquals(100L, stats.getCount());
    }
}
