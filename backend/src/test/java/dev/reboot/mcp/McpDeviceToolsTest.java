package dev.reboot.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.reboot.entity.Alarm;
import dev.reboot.entity.Device;
import dev.reboot.entity.DeviceData;
import dev.reboot.mapper.AlarmMapper;
import dev.reboot.mapper.DeviceDataMapper;
import dev.reboot.mapper.DeviceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * McpDeviceTools 单元测试 —— MCP 只读工具 JSON 输出 + 缺失资源降级（ADR 0027）。
 *
 * @author AI 助手
 * @since 2026-08-29
 */
@ExtendWith(MockitoExtension.class)
class McpDeviceToolsTest {

    @Mock private DeviceMapper deviceMapper;
    @Mock private DeviceDataMapper deviceDataMapper;
    @Mock private AlarmMapper alarmMapper;

    private McpDeviceTools tools;

    @BeforeEach
    void setUp() {
        tools = new McpDeviceTools(deviceMapper, deviceDataMapper, alarmMapper, new ObjectMapper());
    }

    @Test
    void listDevices_shouldReturnDeviceJsonAndApplyLimit() {
        when(deviceMapper.findAll(null)).thenReturn(List.of(device(1L), device(2L), device(3L)));

        String result = tools.listDevices(2);

        assertTrue(result.contains("\"count\":2"));
        assertTrue(result.contains("\"deviceName\":\"测试设备\""));
        assertTrue(result.contains("\"statusLabel\":\"在线\""));
        verify(deviceMapper).findAll(null);
    }

    @Test
    void listDevices_defaultLimitShouldCapToTwenty() {
        when(deviceMapper.findAll(null)).thenReturn(java.util.stream.IntStream.rangeClosed(1, 30)
                .mapToObj(id -> device((long) id)).toList());

        String result = tools.listDevices(null);

        assertTrue(result.contains("\"count\":20"));
    }

    @Test
    void getDeviceBasic_shouldReturnDeviceJson() {
        when(deviceMapper.findById(1L)).thenReturn(device(1L));

        String result = tools.getDeviceBasic(1L);

        assertTrue(result.contains("\"deviceCode\":\"DEV-001\""));
        assertTrue(result.contains("\"status\":1"));
        assertTrue(result.contains("\"siteId\":10"));
    }

    @Test
    void getDeviceBasic_unknownDevice_shouldReturnErrorJson() {
        when(deviceMapper.findById(99L)).thenReturn(null);

        String result = tools.getDeviceBasic(99L);

        assertTrue(result.contains("\"error\""));
        assertTrue(result.contains("设备不存在"));
    }

    @Test
    void getDeviceBasic_missingId_shouldReturnErrorJson() {
        String result = tools.getDeviceBasic(null);

        assertTrue(result.contains("\"error\""));
        assertTrue(result.contains("缺少设备 ID"));
        verify(deviceMapper, never()).findById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void listDeviceRecentData_shouldReturnDataJson() {
        when(deviceMapper.findById(1L)).thenReturn(device(1L));
        when(deviceDataMapper.findByDeviceId(1L)).thenReturn(List.of(
                deviceData(1L, "TEMPERATURE", "82.5", "°C"),
                deviceData(2L, "PRESSURE", "0.6", "MPa")));

        String result = tools.listDeviceRecentData(1L, 2);

        assertTrue(result.contains("\"count\":2"));
        assertTrue(result.contains("\"dataType\":\"TEMPERATURE\""));
        verify(deviceDataMapper).findByDeviceId(1L);
    }

    @Test
    void listDeviceRecentData_unknownDevice_shouldNotQueryData() {
        when(deviceMapper.findById(99L)).thenReturn(null);

        String result = tools.listDeviceRecentData(99L, 10);

        assertTrue(result.contains("\"error\""));
        verify(deviceDataMapper, never()).findByDeviceId(99L);
    }

    @Test
    void listDeviceRecentAlarms_shouldReturnAlarmJson() {
        when(deviceMapper.findById(1L)).thenReturn(device(1L));
        when(alarmMapper.findByDeviceId(1L)).thenReturn(List.of(
                alarm(1L), alarm(2L), alarm(3L)));

        String result = tools.listDeviceRecentAlarms(1L, 2);

        assertTrue(result.contains("\"count\":2"));
        assertTrue(result.contains("\"alarmType\":\"OVER_TEMP\""));
        assertFalse(result.contains("\"alarmLevel\":3"));
        verify(alarmMapper).findByDeviceId(1L);
    }

    private Device device(Long id) {
        Device d = new Device();
        d.setId(id);
        d.setSiteId(10L);
        d.setDeviceName("测试设备");
        d.setDeviceCode("DEV-001");
        d.setDeviceType("PLC");
        d.setLocation("1号车间");
        d.setIpAddress("192.168.1.10");
        d.setPort(502);
        d.setStatus(1);
        d.setUpdatedAt(LocalDateTime.of(2026, 8, 29, 8, 0));
        return d;
    }

    private DeviceData deviceData(Long id, String type, String value, String unit) {
        DeviceData d = new DeviceData();
        d.setId(id);
        d.setDeviceId(1L);
        d.setDataType(type);
        d.setDataValue(new BigDecimal(value));
        d.setUnit(unit);
        d.setRecordedAt(LocalDateTime.of(2026, 8, 29, 9, 0));
        return d;
    }

    private Alarm alarm(Long id) {
        Alarm a = new Alarm();
        a.setId(id);
        a.setDeviceId(1L);
        a.setAlarmType("OVER_TEMP");
        a.setAlarmLevel(2);
        a.setAlarmMessage("温度超过 80°C");
        a.setStatus(0);
        a.setTriggeredAt(LocalDateTime.of(2026, 8, 29, 9, 0));
        return a;
    }
}
