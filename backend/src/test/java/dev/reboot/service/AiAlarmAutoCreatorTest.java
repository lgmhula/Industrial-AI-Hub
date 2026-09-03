package dev.reboot.service;

import dev.reboot.dto.AlarmVO;
import dev.reboot.dto.ai.AiInspectionDetectedIssue;
import dev.reboot.dto.ai.AiInspectionReportResult;
import dev.reboot.entity.Device;
import dev.reboot.mapper.DeviceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AiAlarmAutoCreator 单元测试（Day 86：AI 巡检异常自动生成报警）。
 *
 * <p>覆盖场景：
 * <ol>
 *   <li>正常创建：deviceId 存在 + 幂等 SETNX 首次 → AlarmService.createAlarm 被调用；</li>
 *   <li>幂等去重：同 deviceId + alarmType + reportDate 二次 → 跳过 AlarmService；</li>
 *   <li>deviceCode 反查：issue.deviceId 为 null，DeviceMapper.findByCode 命中 → 仍创建；</li>
 *   <li>device 找不到：issue.deviceCode 反查 null → 跳过（不抛，不调 AlarmService）；</li>
 *   <li>severity 非法（0/4）：clamp 回退 1，仍写入 DB；</li>
 *   <li>Redis 异常：isDuplicate 降级不幂等，AlarmService 仍被调用（宁愿重复也不丢）；</li>
 *   <li>无 Redis（constructor null）：降级不幂等，AlarmService 仍被调用；</li>
 *   <li>空 detectedIssues（0 条）：立即 return 0，不调 AlarmService/DeviceMapper；</li>
 *   <li>null result：立即 return 0，不抛；</li>
 *   <li>单条 AlarmService 异常：降级跳过，其他 issue 仍创建。</li>
 * </ol>
 * </p>
 *
 * @author AI 助手
 * @since 2026-09-01 (Day 86)
 */
@ExtendWith(MockitoExtension.class)
class AiAlarmAutoCreatorTest {

    @Mock private AlarmService alarmService;
    @Mock private DeviceMapper deviceMapper;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;

    private AiAlarmAutoCreator creator;

    private final LocalDate today = LocalDate.of(2026, 9, 1);

    @BeforeEach
    void setUp() {
        // lenient：部分测试（clampLevel / empty / noRedis / nullResult / deviceNotExist）
        // 走不到 redis ops，避免 UnnecessaryStubbingException 报 ERROR。
        org.mockito.Mockito.lenient().when(redis.opsForValue()).thenReturn(valueOps);
        creator = new AiAlarmAutoCreator(alarmService, deviceMapper, redis);
    }

    /** 构造带 3 个字段的最小 AlarmVO（AlarmService 返回值）。 */
    private AlarmVO alarm(Long id, Long deviceId, int level, String type) {
        AlarmVO vo = new AlarmVO();
        vo.setId(id);
        vo.setDeviceId(deviceId);
        vo.setAlarmLevel(level);
        vo.setAlarmType(type);
        return vo;
    }

    /** ① 正常创建：1 issue → 1 alarm。 */
    @Test
    void createAlarms_normalIssue_shouldCreateOneAlarm() {
        AiInspectionReportResult result = resultOf(issue(101L, null, 2,
                "TEMPERATURE_HIGH", "温度超限 88℃ > 阈值 85℃", null));
        // 幂等：首次 → SETNX 命中未命中（TRUE）
        when(valueOps.setIfAbsent(anyString(), eq("1"), any())).thenReturn(Boolean.TRUE);
        when(alarmService.createAlarm(eq(101L), eq("TEMPERATURE_HIGH"), eq(2),
                anyString(), any())).thenReturn(alarm(9001L, 101L, 2, "TEMPERATURE_HIGH"));

        int created = creator.createAlarms(1L, result);

        assertEquals(1, created, "返回值应=创建数 1");
        assertEquals(1, result.getAutoAlarmCount(), "result.autoAlarmCount 应回填 1");
        verify(alarmService).createAlarm(eq(101L), eq("TEMPERATURE_HIGH"), eq(2),
                eq("温度超限 88℃ > 阈值 85℃"), any());
    }

    /** ② 幂等去重：同 deviceId + alarmType + date 二次 → 跳过。 */
    @Test
    void createAlarms_duplicateIdempotency_shouldSkipSecondCall() {
        AiInspectionDetectedIssue issue = issue(101L, null, 3,
                "CONNECTION_LOST", "设备离线 > 5min", null);
        AiInspectionReportResult r1 = resultOf(issue);
        AiInspectionReportResult r2 = resultOf(issue);
        // 第一次：SETNX 成功（TRUE=首次）
        when(valueOps.setIfAbsent(anyString(), eq("1"), any())).thenReturn(Boolean.TRUE);
        when(alarmService.createAlarm(any(), any(), any(), any(), any()))
                .thenReturn(alarm(9001L, 101L, 3, "CONNECTION_LOST"));
        assertEquals(1, creator.createAlarms(1L, r1));

        // 第二次：SETNX 失败（FALSE=重复）
        when(valueOps.setIfAbsent(anyString(), eq("1"), any())).thenReturn(Boolean.FALSE);
        int created2 = creator.createAlarms(1L, r2);

        assertEquals(0, created2, "幂等命中：应返回 0");
        assertEquals(0, r2.getAutoAlarmCount(), "回填=0");
        // alarmService 整个第二次调用不应出现（一次是 r1）
        verify(alarmService).createAlarm(any(), any(), any(), any(), any());
    }

    /** ③ deviceCode 反查：issue.deviceId=null，findByCode 命中 → 仍创建。 */
    @Test
    void createAlarms_deviceCodeLookup_shouldResolveAndCreate() {
        Device d = new Device();
        d.setId(202L);
        when(deviceMapper.findByCode("CNC-007")).thenReturn(d);
        when(valueOps.setIfAbsent(anyString(), eq("1"), any())).thenReturn(Boolean.TRUE);
        when(alarmService.createAlarm(eq(202L), eq("ERROR_RATE_SPIKE"), eq(1),
                anyString(), any())).thenReturn(alarm(9100L, 202L, 1, "ERROR_RATE_SPIKE"));
        AiInspectionReportResult result = resultOf(issue(null, "CNC-007", 1,
                "ERROR_RATE_SPIKE", "30min 内错误率从 0.2% 升至 3.1%", LocalDateTime.now()));

        int created = creator.createAlarms(2L, result);

        assertEquals(1, created);
        assertEquals(1, result.getAutoAlarmCount());
        verify(deviceMapper).findByCode("CNC-007");
        verify(alarmService).createAlarm(eq(202L), any(), any(), any(), any());
    }

    /** ④ device 找不到：deviceCode 反查 null → 跳过（不抛，不调 AlarmService）。 */
    @Test
    void createAlarms_deviceCodeNotExist_shouldSkipWithoutException() {
        when(deviceMapper.findByCode("GHOST-999")).thenReturn(null);
        AiInspectionReportResult result = resultOf(issue(null, "GHOST-999", 2,
                "UNKNOWN", "message", null));

        int created = creator.createAlarms(3L, result);

        assertEquals(0, created, "设备不存在 → 返回 0，不抛异常");
        assertEquals(0, result.getAutoAlarmCount());
        verify(deviceMapper).findByCode("GHOST-999");
        verify(alarmService, never()).createAlarm(any(), any(), any(), any(), any());
    }

    /** ⑤ severity 非法：clamp 回退 1（DB CHECK 1-3，不回退会写失败）。 */
    @Test
    void clampLevel_illegalSeverity_shouldFallbackTo1() {
        assertEquals(1, creator.clampLevel(0));
        assertEquals(1, creator.clampLevel(-10));
        assertEquals(1, creator.clampLevel(999));
        assertEquals(1, creator.clampLevel(1), "合法值 1 不变");
        assertEquals(2, creator.clampLevel(2), "合法值 2 不变");
        assertEquals(3, creator.clampLevel(3), "合法值 3 不变");
    }

    /** ⑥ Redis 异常：isDuplicate 降级不幂等，AlarmService 仍被调用。 */
    @Test
    void createAlarms_redisUnavailable_shouldFallbackCreateAlarm() {
        AiInspectionReportResult result = resultOf(issue(5L, null, 2,
                "TEMP", "warn", null));
        when(valueOps.setIfAbsent(anyString(), any(), any()))
                .thenThrow(new RuntimeException("Redis connection reset"));
        when(alarmService.createAlarm(any(), any(), any(), any(), any()))
                .thenReturn(alarm(9999L, 5L, 2, "TEMP"));

        int created = creator.createAlarms(1L, result);

        assertEquals(1, created, "Redis 不可用：降级「不幂等」→ 宁愿重复仍创建 alarm");
        verify(alarmService).createAlarm(eq(5L), eq("TEMP"), eq(2), eq("warn"), any());
    }

    /** ⑦ 无 Redis（constructor null）：降级不幂等，alarm 仍创建。 */
    @Test
    void createAlarms_noRedis_shouldFallbackCreateAlarm() {
        AiAlarmAutoCreator noRedis = new AiAlarmAutoCreator(alarmService, deviceMapper, null);
        AiInspectionReportResult result = resultOf(issue(7L, null, 3, "SENSOR", "msg", null));
        when(alarmService.createAlarm(eq(7L), eq("SENSOR"), eq(3), eq("msg"), any()))
                .thenReturn(alarm(7777L, 7L, 3, "SENSOR"));

        int created = noRedis.createAlarms(1L, result);

        assertEquals(1, created);
    }

    /** ⑧ 空 detectedIssues → 0 快速返回，不调 AlarmService/DeviceMapper。 */
    @Test
    void createAlarms_emptyIssues_shouldReturnZeroFast() {
        AiInspectionReportResult r = new AiInspectionReportResult();

        int created = creator.createAlarms(null, r);

        assertEquals(0, created);
        assertEquals(0, r.getAutoAlarmCount());
        verify(alarmService, never()).createAlarm(any(), any(), any(), any(), any());
        verify(deviceMapper, never()).findByCode(anyString());
    }

    /** ⑨ null result → 0 不抛。 */
    @Test
    void createAlarms_nullResult_shouldReturnZeroSafe() {
        int created = creator.createAlarms(1L, null);
        assertEquals(0, created);
    }

    /** ⑩ 混合 3 issues：1 正常创建 + 1 AlarmService 异常降级 + 1 幂等命中 → 其他 issue 不受影响。 */
    @Test
    void createAlarms_mixedIssues_failureShouldNotBlockOthers() {
        AiInspectionDetectedIssue ok = issue(1L, null, 2, "T_HIGH", "正常创建", null);
        AiInspectionDetectedIssue bad = issue(2L, null, 2, "T_LOW", "DB 写失败", null);
        AiInspectionDetectedIssue dup = issue(3L, null, 2, "DUP", "幂等命中", null);
        AiInspectionReportResult result = resultOf(ok, bad, dup);

        // 幂等：ok→首次, bad→首次, dup→重复
        when(valueOps.setIfAbsent(anyString(), eq("1"), any()))
                .thenReturn(Boolean.TRUE)   // ok
                .thenReturn(Boolean.TRUE)   // bad
                .thenReturn(Boolean.FALSE); // dup
        when(alarmService.createAlarm(eq(1L), any(), any(), any(), any()))
                .thenReturn(alarm(10L, 1L, 2, "T_HIGH"));
        when(alarmService.createAlarm(eq(2L), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("DB 写入模拟异常"));

        int created = creator.createAlarms(42L, result);

        assertEquals(1, created, "1 正常创建；1 异常降级跳过；1 幂等跳过 → 合计 1");
        assertEquals(1, result.getAutoAlarmCount());
        ArgumentCaptor<Long> did = ArgumentCaptor.forClass(Long.class);
        verify(alarmService, org.mockito.Mockito.times(2))
                .createAlarm(did.capture(), any(), any(), any(), any());
        // 两次调用的 deviceId 顺序应 = [1, 2]（dup 不调 alarmService）
        assertEquals(List.of(1L, 2L), did.getAllValues());
    }

    // —— Helper ——

    private AiInspectionReportResult resultOf(AiInspectionDetectedIssue... issues) {
        AiInspectionReportResult r = new AiInspectionReportResult();
        r.setReportDate(today);
        r.setReport("日报正文");
        r.setDetectedIssues(List.of(issues));
        return r;
    }

    private AiInspectionDetectedIssue issue(Long deviceId, String deviceCode, int severity,
                                            String alarmType, String description,
                                            LocalDateTime occurredAt) {
        return new AiInspectionDetectedIssue(deviceId, deviceCode, severity,
                alarmType, description, occurredAt);
    }
}
