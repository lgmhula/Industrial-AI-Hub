package dev.reboot.service;

import dev.reboot.dto.AlarmVO;
import dev.reboot.entity.Alarm;
import dev.reboot.mapper.AlarmMapper;
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
 * AlarmService 单元测试。
 *
 * @author hula0710
 * @since 2026-08-02
 */
@ExtendWith(MockitoExtension.class)
class AlarmServiceTest {

    @Mock private AlarmMapper alarmMapper;
    @InjectMocks private AlarmService alarmService;

    private Alarm newAlarm(Long id, int status) {
        Alarm a = new Alarm();
        a.setId(id); a.setDeviceId(1L); a.setAlarmType("OVER_TEMP");
        a.setAlarmLevel(2); a.setAlarmMessage("test"); a.setStatus(status);
        a.setTriggeredAt(LocalDateTime.now());
        return a;
    }

    @Test
    void listAllPaged_shouldReturnPage() {
        when(alarmMapper.findAll()).thenReturn(List.of(newAlarm(1L, 0)));
        var result = alarmService.listAllPaged(1, 10);
        assertEquals(1, result.getTotal());
        assertEquals("OVER_TEMP", result.getList().get(0).getAlarmType());
    }

    @Test
    void listAllPaged_shouldReturnEmpty() {
        when(alarmMapper.findAll()).thenReturn(Collections.emptyList());
        var result = alarmService.listAllPaged(1, 10);
        assertEquals(0, result.getTotal());
        assertTrue(result.getList().isEmpty());
    }

    @Test
    void listByDevicePaged_shouldFilterByDevice() {
        when(alarmMapper.findByDeviceId(1L)).thenReturn(List.of(newAlarm(1L, 0)));
        var result = alarmService.listByDevicePaged(1L, 1, 10);
        assertEquals(1, result.getTotal());
    }

    @Test
    void listByStatusPaged_shouldFilterByStatus() {
        when(alarmMapper.findByStatus(0)).thenReturn(List.of(newAlarm(1L, 0)));
        var result = alarmService.listByStatusPaged(0, 1, 10);
        assertEquals(1, result.getTotal());
    }

    @Test
    void acknowledge_shouldReturnTrue() {
        when(alarmMapper.acknowledge(1L)).thenReturn(1);
        assertTrue(alarmService.acknowledge(1L));
    }

    @Test
    void acknowledge_shouldReturnFalseWhenNotFound() {
        when(alarmMapper.acknowledge(99L)).thenReturn(0);
        assertFalse(alarmService.acknowledge(99L));
    }

    @Test
    void resolve_shouldReturnTrue() {
        when(alarmMapper.resolve(1L)).thenReturn(1);
        assertTrue(alarmService.resolve(1L));
    }

    @Test
    void resolve_shouldReturnFalseWhenNotFound() {
        when(alarmMapper.resolve(99L)).thenReturn(0);
        assertFalse(alarmService.resolve(99L));
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
