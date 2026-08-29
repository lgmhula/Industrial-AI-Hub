package dev.reboot.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.reboot.dto.AlarmSiteVO;
import dev.reboot.entity.Alarm;
import dev.reboot.entity.Device;
import dev.reboot.entity.DeviceData;
import dev.reboot.enums.ErrorCode;
import dev.reboot.enums.RoleEnum;
import dev.reboot.exception.BusinessException;
import dev.reboot.mapper.AlarmMapper;
import dev.reboot.mapper.DeviceDataMapper;
import dev.reboot.mapper.DeviceMapper;
import dev.reboot.service.SiteAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DeviceAiTools 单元测试 —— @Tool 工具 JSON 输出 + 站点资源作用域。
 *
 * @author AI 助手
 * @since 2026-08-29
 */
@ExtendWith(MockitoExtension.class)
class DeviceAiToolsTest {

    @Mock private DeviceMapper deviceMapper;
    @Mock private AlarmMapper alarmMapper;
    @Mock private DeviceDataMapper deviceDataMapper;
    @Mock private SiteAccessService siteAccessService;

    private DeviceAiTools tools;
    private ToolContext toolContext;

    @BeforeEach
    void setUp() {
        tools = new DeviceAiTools(deviceMapper, alarmMapper, deviceDataMapper, siteAccessService, new ObjectMapper());
        toolContext = new ToolContext(Map.of(DeviceAiTools.CONTEXT_USER_ID, 7L));
    }

    @Test
    void getDeviceBasic_shouldReturnDeviceJsonAndEnforceSiteAccess() {
        when(deviceMapper.findById(1L)).thenReturn(device());

        String result = tools.getDeviceBasic(1L, toolContext);

        assertTrue(result.contains("\"deviceName\":\"测试设备\""));
        assertTrue(result.contains("\"deviceCode\":\"DEV-001\""));
        assertTrue(result.contains("\"status\":1"));
        assertTrue(result.contains("\"statusLabel\":\"在线\""));
        verify(siteAccessService).assertSiteAccess(7L, 10L, RoleEnum.VIEWER);
    }

    @Test
    void getDeviceBasic_noSiteAccess_shouldReturnErrorJson() {
        when(deviceMapper.findById(1L)).thenReturn(device());
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无权访问该站点资源"))
                .when(siteAccessService).assertSiteAccess(7L, 10L, RoleEnum.VIEWER);

        String result = tools.getDeviceBasic(1L, toolContext);

        assertTrue(result.contains("\"error\""));
        assertTrue(result.contains("无权访问该站点资源"));
    }

    @Test
    void getDeviceBasic_unknownDevice_shouldReturnErrorJson() {
        when(deviceMapper.findById(99L)).thenReturn(null);

        String result = tools.getDeviceBasic(99L, toolContext);

        assertTrue(result.contains("\"error\""));
        assertTrue(result.contains("设备不存在"));
        verify(siteAccessService, org.mockito.Mockito.never()).assertSiteAccess(any(), any(), any());
    }

    @Test
    void listDeviceRecentAlarms_shouldLimitAndReturnAlarmJson() {
        when(deviceMapper.findById(1L)).thenReturn(device());
        when(alarmMapper.findByDeviceId(1L)).thenReturn(List.of(alarm(1L), alarm(2L), alarm(3L)));

        String result = tools.listDeviceRecentAlarms(1L, 2, toolContext);

        assertTrue(result.contains("\"count\":2"));
        assertTrue(result.contains("\"alarmType\":\"OVER_TEMP\""));
        verify(siteAccessService).assertSiteAccess(7L, 10L, RoleEnum.VIEWER);
    }

    @Test
    void listDeviceRecentAlarms_defaultLimitIsFive() {
        when(deviceMapper.findById(1L)).thenReturn(device());
        when(alarmMapper.findByDeviceId(1L)).thenReturn(List.of(
                alarm(1L), alarm(2L), alarm(3L), alarm(4L), alarm(5L), alarm(6L)));

        String result = tools.listDeviceRecentAlarms(1L, null, toolContext);

        assertTrue(result.contains("\"count\":5"));
    }

    @Test
    void listActiveAlarmsBySite_shouldQueryMapperAndReturnJson() {
        AlarmSiteVO vo = new AlarmSiteVO();
        vo.setId(1L);
        vo.setDeviceId(1L);
        vo.setDeviceName("测试设备");
        vo.setAlarmType("OVER_TEMP");
        vo.setAlarmLevel(2);
        vo.setAlarmMessage("温度超过 80°C");
        vo.setTriggeredAt(LocalDateTime.of(2026, 8, 29, 9, 0));
        when(alarmMapper.findActiveBySiteId(10L, 10)).thenReturn(List.of(vo));

        String result = tools.listActiveAlarmsBySite(10L, null, toolContext);

        assertTrue(result.contains("\"siteId\":10"));
        assertTrue(result.contains("\"deviceName\":\"测试设备\""));
        verify(siteAccessService).assertSiteAccess(7L, 10L, RoleEnum.VIEWER);
        verify(alarmMapper).findActiveBySiteId(eq(10L), eq(10));
    }

    @Test
    void listActiveAlarmsBySite_noAccess_shouldReturnErrorJson() {
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无权访问该站点资源"))
                .when(siteAccessService).assertSiteAccess(7L, 10L, RoleEnum.VIEWER);

        String result = tools.listActiveAlarmsBySite(10L, 5, toolContext);

        assertTrue(result.contains("\"error\""));
        assertFalse(result.contains("\"alarms\""));
    }

    @Test
    void listDeviceRecentData_shouldReturnDataJson() {
        when(deviceMapper.findById(1L)).thenReturn(device());
        when(deviceDataMapper.findByDeviceId(1L)).thenReturn(List.of(
                deviceData(1L, "TEMPERATURE", "82.5", "°C"),
                deviceData(2L, "PRESSURE", "0.6", "MPa")));

        String result = tools.listDeviceRecentData(1L, 2, toolContext);

        assertTrue(result.contains("\"count\":2"));
        assertTrue(result.contains("\"dataType\":\"TEMPERATURE\""));
        verify(siteAccessService).assertSiteAccess(7L, 10L, RoleEnum.VIEWER);
        verify(deviceDataMapper).findByDeviceId(1L);
    }

    private Device device() {
        Device d = new Device();
        d.setId(1L);
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
}
