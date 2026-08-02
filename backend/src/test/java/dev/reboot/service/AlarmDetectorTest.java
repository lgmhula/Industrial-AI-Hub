package dev.reboot.service;

import dev.reboot.dto.AlarmVO;
import dev.reboot.rule.AlarmRule;
import dev.reboot.rule.Operator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AlarmDetector 单元测试 —— 覆盖 8 条报警规则引擎。
 *
 * @author hula0710
 * @since 2026-08-02
 */
@ExtendWith(MockitoExtension.class)
class AlarmDetectorTest {

    @Mock private AlarmService alarmService;
    @InjectMocks private AlarmDetector detector;

    private final List<AlarmRule> rules = List.of(
        new AlarmRule("TEMPERATURE", Operator.GT, "40.0", 2, "OVER_TEMP",  "温度过高 {value}"),
        new AlarmRule("TEMPERATURE", Operator.LT, "0.0",  2, "UNDER_TEMP", "温度过低 {value}"),
        new AlarmRule("PRESSURE",    Operator.GT, "110.0",1, "OVER_PRESSURE","压力过高 {value}"),
        new AlarmRule("PRESSURE",    Operator.LT, "90.0", 2, "UNDER_PRESSURE","压力过低 {value}"),
        new AlarmRule("SPEED",       Operator.GT, "3000.0",3,"OVER_SPEED",  "转速过高 {value}"),
        new AlarmRule("SPEED",       Operator.LT, "100.0", 2,"UNDER_SPEED", "转速偏低 {value}"),
        new AlarmRule("HUMIDITY",    Operator.GT, "90.0",  1,"OVER_HUMIDITY","湿度过高 {value}"),
        new AlarmRule("HUMIDITY",    Operator.LT, "10.0",  1,"UNDER_HUMIDITY","湿度过低 {value}")
    );

    @BeforeEach
    void setUp() {
        detector = new AlarmDetector(rules, alarmService);
    }

    @Test
    void shouldTriggerOverTemp() {
        when(alarmService.createAlarm(anyLong(), eq("OVER_TEMP"), eq(2), anyString(), any()))
                .thenReturn(new AlarmVO());
        List<AlarmVO> result = detector.check(1L, "TEMPERATURE", new BigDecimal("45.0"));
        assertEquals(1, result.size());
    }

    @Test
    void shouldTriggerUnderTemp() {
        when(alarmService.createAlarm(anyLong(), eq("UNDER_TEMP"), eq(2), anyString(), any()))
                .thenReturn(new AlarmVO());
        List<AlarmVO> result = detector.check(1L, "TEMPERATURE", new BigDecimal("-5.0"));
        assertEquals(1, result.size());
    }

    @Test
    void shouldNotTriggerWhenValueIsNormal() {
        List<AlarmVO> result = detector.check(1L, "TEMPERATURE", new BigDecimal("25.0"));
        assertEquals(0, result.size());
    }

    @Test
    void shouldTriggerOverPressure() {
        when(alarmService.createAlarm(anyLong(), eq("OVER_PRESSURE"), eq(1), anyString(), any()))
                .thenReturn(new AlarmVO());
        List<AlarmVO> result = detector.check(1L, "PRESSURE", new BigDecimal("120.0"));
        assertEquals(1, result.size());
    }

    @Test
    void shouldTriggerUnderPressure() {
        when(alarmService.createAlarm(anyLong(), eq("UNDER_PRESSURE"), eq(2), anyString(), any()))
                .thenReturn(new AlarmVO());
        List<AlarmVO> result = detector.check(1L, "PRESSURE", new BigDecimal("80.0"));
        assertEquals(1, result.size());
    }

    @Test
    void shouldTriggerOverSpeed() {
        when(alarmService.createAlarm(anyLong(), eq("OVER_SPEED"), eq(3), anyString(), any()))
                .thenReturn(new AlarmVO());
        List<AlarmVO> result = detector.check(1L, "SPEED", new BigDecimal("3500.0"));
        assertEquals(1, result.size());
    }

    @Test
    void shouldTriggerUnderSpeed() {
        when(alarmService.createAlarm(anyLong(), eq("UNDER_SPEED"), eq(2), anyString(), any()))
                .thenReturn(new AlarmVO());
        List<AlarmVO> result = detector.check(1L, "SPEED", new BigDecimal("50.0"));
        assertEquals(1, result.size());
    }

    @Test
    void shouldTriggerOverHumidity() {
        when(alarmService.createAlarm(anyLong(), eq("OVER_HUMIDITY"), eq(1), anyString(), any()))
                .thenReturn(new AlarmVO());
        List<AlarmVO> result = detector.check(1L, "HUMIDITY", new BigDecimal("95.0"));
        assertEquals(1, result.size());
    }

    @Test
    void shouldTriggerUnderHumidity() {
        when(alarmService.createAlarm(anyLong(), eq("UNDER_HUMIDITY"), eq(1), anyString(), any()))
                .thenReturn(new AlarmVO());
        List<AlarmVO> result = detector.check(1L, "HUMIDITY", new BigDecimal("5.0"));
        assertEquals(1, result.size());
    }

    @Test
    void shouldReturnEmptyWhenValueNull() {
        List<AlarmVO> result = detector.check(1L, "TEMPERATURE", null);
        assertEquals(0, result.size());
    }

    @Test
    void shouldReturnEmptyWhenDataTypeNull() {
        List<AlarmVO> result = detector.check(1L, null, BigDecimal.TEN);
        assertEquals(0, result.size());
    }

    @Test
    void unknownDataType_shouldNotTrigger() {
        List<AlarmVO> result = detector.check(1L, "VOLTAGE", new BigDecimal("999"));
        assertEquals(0, result.size());
    }
}
