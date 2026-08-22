package dev.reboot.service;

import dev.reboot.dto.AlarmVO;
import dev.reboot.entity.Alarm;
import dev.reboot.entity.Device;
import dev.reboot.enums.ErrorCode;
import dev.reboot.enums.RoleEnum;
import dev.reboot.exception.BusinessException;
import dev.reboot.mapper.AlarmMapper;
import dev.reboot.mapper.DeviceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AlarmService 单元测试（P1-01：站点作用域）。
 *
 * @author hula0710
 * @since 2026-08-02
 */
@ExtendWith(MockitoExtension.class)
class AlarmServiceTest {

    @Mock private AlarmMapper alarmMapper;
    @Mock private DeviceMapper deviceMapper;
    @Mock private SiteAccessService siteAccessService;
    @InjectMocks private AlarmService alarmService;

    private static final Long USER_ID = 1L;

    private Alarm newAlarm(Long id, int status) {
        Alarm a = new Alarm();
        a.setId(id); a.setDeviceId(1L); a.setAlarmType("OVER_TEMP");
        a.setAlarmLevel(2); a.setAlarmMessage("test"); a.setStatus(status);
        a.setTriggeredAt(LocalDateTime.now());
        return a;
    }

    private Device newDevice(Long id) {
        Device d = new Device();
        d.setId(id); d.setSiteId(10L); d.setDeviceName("d");
        return d;
    }

    @Test
    void listAllPaged_shouldReturnPage() {
        when(siteAccessService.accessibleSiteIds(USER_ID)).thenReturn(null);
        when(alarmMapper.findAll(null)).thenReturn(List.of(newAlarm(1L, 0)));
        var result = alarmService.listAllPaged(1, 10, USER_ID);
        assertEquals(1, result.getTotal());
        assertEquals("OVER_TEMP", result.getList().get(0).getAlarmType());
    }

    @Test
    void listAllPaged_noSiteAccess_shouldReturnEmpty() {
        when(siteAccessService.accessibleSiteIds(USER_ID)).thenReturn(List.of());
        var result = alarmService.listAllPaged(1, 10, USER_ID);
        assertEquals(0, result.getTotal());
        verify(alarmMapper, never()).findAll(any());
    }

    @Test
    void listAllPaged_shouldReturnEmpty() {
        when(siteAccessService.accessibleSiteIds(USER_ID)).thenReturn(null);
        when(alarmMapper.findAll(null)).thenReturn(Collections.emptyList());
        var result = alarmService.listAllPaged(1, 10, USER_ID);
        assertEquals(0, result.getTotal());
        assertTrue(result.getList().isEmpty());
    }

    @Test
    void listByDevicePaged_shouldFilterByDevice() {
        when(deviceMapper.findById(1L)).thenReturn(newDevice(1L));
        when(alarmMapper.findByDeviceId(1L)).thenReturn(List.of(newAlarm(1L, 0)));
        var result = alarmService.listByDevicePaged(1L, 1, 10, USER_ID);
        assertEquals(1, result.getTotal());
        verify(siteAccessService).assertSiteAccess(USER_ID, 10L, RoleEnum.VIEWER);
    }

    @Test
    void listByDevicePaged_noSiteAccess_shouldThrowForbidden() {
        when(deviceMapper.findById(1L)).thenReturn(newDevice(1L));
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无权访问该站点资源"))
                .when(siteAccessService).assertSiteAccess(USER_ID, 10L, RoleEnum.VIEWER);
        assertThrows(BusinessException.class, () -> alarmService.listByDevicePaged(1L, 1, 10, USER_ID));
    }

    @Test
    void listByStatusPaged_shouldFilterByStatus() {
        when(siteAccessService.accessibleSiteIds(USER_ID)).thenReturn(null);
        when(alarmMapper.findByStatus(0, null)).thenReturn(List.of(newAlarm(1L, 0)));
        var result = alarmService.listByStatusPaged(0, 1, 10, USER_ID);
        assertEquals(1, result.getTotal());
    }

    @Test
    void acknowledge_shouldReturnTrue() {
        when(alarmMapper.findById(1L)).thenReturn(newAlarm(1L, 0));
        when(deviceMapper.findById(1L)).thenReturn(newDevice(1L));
        when(alarmMapper.acknowledge(1L)).thenReturn(1);
        assertTrue(alarmService.acknowledge(1L, USER_ID));
        verify(siteAccessService).assertSiteAccess(USER_ID, 10L, RoleEnum.OPERATOR);
    }

    @Test
    void acknowledge_shouldReturnFalseWhenNotFound() {
        when(alarmMapper.findById(99L)).thenReturn(null);
        assertFalse(alarmService.acknowledge(99L, USER_ID));
        verify(alarmMapper).acknowledge(99L);
    }

    @Test
    void acknowledge_noSiteAccess_shouldThrowForbidden() {
        when(alarmMapper.findById(1L)).thenReturn(newAlarm(1L, 0));
        when(deviceMapper.findById(1L)).thenReturn(newDevice(1L));
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无权访问该站点资源"))
                .when(siteAccessService).assertSiteAccess(USER_ID, 10L, RoleEnum.OPERATOR);
        assertThrows(BusinessException.class, () -> alarmService.acknowledge(1L, USER_ID));
        verify(alarmMapper, never()).acknowledge(anyLong());
    }

    @Test
    void resolve_shouldReturnTrue() {
        when(alarmMapper.findById(1L)).thenReturn(newAlarm(1L, 1));
        when(deviceMapper.findById(1L)).thenReturn(newDevice(1L));
        when(alarmMapper.resolve(1L)).thenReturn(1);
        assertTrue(alarmService.resolve(1L, USER_ID));
    }

    @Test
    void resolve_shouldReturnFalseWhenNotFound() {
        when(alarmMapper.findById(99L)).thenReturn(null);
        assertFalse(alarmService.resolve(99L, USER_ID));
        verify(alarmMapper).resolve(99L);
    }

    @Test
    void createAlarm_shouldReturnVO() {
        when(alarmMapper.insert(any(Alarm.class))).thenReturn(1);
        AlarmVO vo = alarmService.createAlarm(1L, "OVER_TEMP", 2, "过热", LocalDateTime.now());
        assertNotNull(vo);
        assertEquals("OVER_TEMP", vo.getAlarmType());
        verify(alarmMapper).insert(any(Alarm.class));
    }
}
