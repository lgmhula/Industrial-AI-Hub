package dev.reboot.service;

import dev.reboot.annotation.OperationLog;
import dev.reboot.dto.AlarmVO;
import dev.reboot.dto.ai.AiInspectionDetectedIssue;
import dev.reboot.dto.ai.AiInspectionReportResult;
import dev.reboot.entity.Device;
import dev.reboot.mapper.DeviceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 巡检异常 → 业务报警 自动生成器（Day 86：AI 与业务闭环）。
 *
 * <p>读取 {@link AiInspectionReportResult#getDetectedIssues()} 结构化异常，
 * 对每一项：</p>
 * <ol>
 *   <li>解析 deviceId：若 issue 中 deviceId 为空，用 deviceCode 经
 *       {@link DeviceMapper#findByCode(String)} 反查；仍找不到则 WARN 跳过该项，
 *       <b>不抛异常、不阻塞其他 issue 与 Agent 主流程</b>。</li>
 *   <li>24h 跨实例幂等：Redis SETNX 键 {@code ai-alarm:{deviceId}:{alarmType}:{yyyy-MM-dd}}
 *       TTL 24h；命中则视为重复跳过（防同日报告重复生成报警）。</li>
 *   <li>写入 alarm 表：调用 {@link AlarmService#createAlarm(Long, String, Integer, String, LocalDateTime)}
 *       status=0（未处理）。</li>
 *   <li>统计写入数回填到 {@link AiInspectionReportResult#setAutoAlarmCount(int)}。</li>
 *   <li>审计：本类 {@link #createAlarms(Long, AiInspectionReportResult)} 方法标记
 *       {@code @OperationLog(operationType=AUTO_ALARM, targetType=ALARM)}，
 *       由 {@code OperationLogAspect} 自动写入 operation_log。</li>
 * </ol>
 *
 * <h3>失败语义（ADR 0031 §6 对齐：异常不阻塞主链路）</h3>
 * <p>本服务所有异常一律 catch 并 WARN 降级：
 * <ul>
 *   <li>单条 issue 的 DeviceMapper/AlarmService 异常 → 仅该条跳过，不影响其他；</li>
 *   <li>Redis 不可用 → 幂等降级为不做去重（宁愿重复也不丢报警），仍尝试 AlarmService.createAlarm；</li>
 *   <li>{@link #createAlarms(Long, AiInspectionReportResult)} 整体不对外抛异常，
 *       保证 Agent 调用方不中断（Agent 主流程最终仍可返回日报 + 投递 MQ + SSE 推送）。</li>
 * </ul>
 * </p>
 *
 * <h3>severity 与 alarm_level 对齐（Day 86，P0 语义）</h3>
 * <pre>
 *   AiInspectionDetectedIssue.severity 1 → alarm_level=1 (一般)
 *   AiInspectionDetectedIssue.severity 2 → alarm_level=2 (重要)
 *   AiInspectionDetectedIssue.severity 3 → alarm_level=3 (紧急)
 *   其他（非法）值 → 回退 1 并 WARN（DB 约束 1-3，不回退会写失败）
 * </pre>
 *
 * @author AI 助手
 * @since 2026-09-01 (Day 86)
 */
@Service
public class AiAlarmAutoCreator {

    private static final Logger log = LoggerFactory.getLogger(AiAlarmAutoCreator.class);

    /** 幂等键前缀：ai-alarm:{deviceId}:{alarmType}:{yyyy-MM-dd} TTL 24h。 */
    private static final String IDEMPOTENCY_KEY_PREFIX = "ai-alarm:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    /** 非法 severity 回退等级（与 alarm.alarm_level CHECK 1-3 对齐）。 */
    private static final int DEFAULT_ALARM_LEVEL = 1;

    private final AlarmService alarmService;
    private final DeviceMapper deviceMapper;
    /** nullable：test profile 下 Redis Mock / 未启用时，降级为不做幂等。 */
    @Nullable
    private final StringRedisTemplate redis;

    public AiAlarmAutoCreator(AlarmService alarmService,
                              DeviceMapper deviceMapper,
                              @Nullable StringRedisTemplate redis) {
        this.alarmService = alarmService;
        this.deviceMapper = deviceMapper;
        this.redis = redis;
    }

    /**
     * 解析 AI 巡检日报 detectedIssues，自动生成业务报警（审计入口）。
     *
     * <p>本方法<strong>永不抛出异常</strong>：任何路径的 RuntimeException 都会在内部
     * catch 并 WARN 降级，保证调用方（McpInspectionAgentService.generate()）的
     * Agent 主链路不中断。</p>
     *
     * @param triggeredByUserId 触发本次巡检的用户 ID（审计用，非授权依据）
     * @param result            AI 巡检日报结果（本方法会回填 autoAlarmCount）
     * @return 实际生成的报警数量；返回 0 表示 detectedIssues 为空 / 全部重复 / 全部异常
     */
    @OperationLog(operationType = "AUTO_ALARM", targetType = "ALARM",
            description = "AI 巡检日报自动生成报警: userId={0}, issues={1.detectedIssues.size}, alarms={ret}")
    public int createAlarms(@Nullable Long triggeredByUserId, AiInspectionReportResult result) {
        if (result == null) {
            log.warn("AiAlarmAutoCreator.createAlarms: result 为 null，跳过");
            return 0;
        }
        List<AiInspectionDetectedIssue> issues = result.getDetectedIssues();
        if (issues == null || issues.isEmpty()) {
            log.debug("AI 巡检日报无 detectedIssues，无需自动生成报警: date={}", result.getReportDate());
            result.setAutoAlarmCount(0);
            return 0;
        }
        int created = 0;
        LocalDate today = result.getReportDate() != null ? result.getReportDate() : LocalDate.now();
        for (AiInspectionDetectedIssue issue : issues) {
            try {
                Integer alarmId = tryCreateOne(today, issue);
                if (alarmId != null) {
                    created++;
                }
            } catch (RuntimeException ex) {
                // 单条 issue 的异常降级：不中断其他 issue，也不中断主流程
                log.warn("AI 自动生成报警失败，跳过单条 issue: issue={}", safeSummary(issue), ex);
            }
        }
        result.setAutoAlarmCount(created);
        log.info("AI 巡检自动生成报警完成: date={} issues={} created={}", today, issues.size(), created);
        return created;
    }

    // —— 单条 issue 处理（内部方法，可单测覆盖所有路径）——

    /** @return 新建报警 ID；去重命中 / device 找不到 → null。 */
    @Nullable
    Integer tryCreateOne(LocalDate reportDate, AiInspectionDetectedIssue issue) {
        Long deviceId = resolveDeviceId(issue);
        if (deviceId == null) {
            log.warn("AI 自动报警：deviceId 解析失败，跳过该 issue: {}", safeSummary(issue));
            return null;
        }
        int alarmLevel = clampLevel(issue.getSeverity());
        String alarmType = issue.getAlarmType();
        if (alarmType == null || alarmType.isBlank()) {
            log.warn("AI 自动报警：alarmType 为空，跳过: deviceId={}", deviceId);
            return null;
        }
        // 24h 幂等：SETNX 命中 → 跳过
        if (isDuplicate(reportDate, deviceId, alarmType)) {
            log.info("AI 自动报警：24h 内重复（幂等命中）跳过: deviceId={} type={} date={}",
                    deviceId, alarmType, reportDate);
            return null;
        }
        LocalDateTime triggeredAt = issue.getOccurredAt() == null ? LocalDateTime.now() : issue.getOccurredAt();
        String message = messageOrDefault(issue);
        AlarmVO vo = alarmService.createAlarm(deviceId, alarmType, alarmLevel, message, triggeredAt);
        log.info("AI 自动生成报警成功: alarmId={} deviceId={} type={} level={}",
                vo.getId(), deviceId, alarmType, alarmLevel);
        return Math.toIntExact(vo.getId());
    }

    /** 解析 deviceId：优先 issue.deviceId，否则 deviceCode 反查 DeviceMapper；仍找不到 → null。 */
    @Nullable
    Long resolveDeviceId(AiInspectionDetectedIssue issue) {
        if (issue.getDeviceId() != null) {
            return issue.getDeviceId();
        }
        String code = issue.getDeviceCode();
        if (code == null || code.isBlank()) {
            return null;
        }
        Device d = deviceMapper.findByCode(code);
        return d == null ? null : d.getId();
    }

    /** severity 夹到 [1,3] 区间（与 alarm.alarm_level CHECK 1-3 对齐）。 */
    int clampLevel(int severity) {
        if (severity < 1 || severity > 3) {
            log.warn("AI 自动报警：severity={} 超出 1-3，回退默认等级 {}", severity, DEFAULT_ALARM_LEVEL);
            return DEFAULT_ALARM_LEVEL;
        }
        return severity;
    }

    /** description 空 → 回退 "[AI 巡检] {alarmType} 异常" 兜底消息，避免 DB 层面 blank。 */
    String messageOrDefault(AiInspectionDetectedIssue issue) {
        if (issue.getDescription() != null && !issue.getDescription().isBlank()) {
            return issue.getDescription();
        }
        return "[AI 巡检] " + issue.getAlarmType() + " 异常";
    }

    /**
     * 24h 幂等：Redis SETNX；Redis 不可用时（null / 抛异常）降级为「不做去重」——
     * 返回 false，保证仍尝试写报警（宁愿重复也不丢）。
     */
    boolean isDuplicate(LocalDate reportDate, Long deviceId, String alarmType) {
        if (redis == null) {
            return false; // 无 Redis → 降级不幂等
        }
        String key = IDEMPOTENCY_KEY_PREFIX + deviceId + ":" + alarmType + ":" + reportDate;
        try {
            Boolean acquired = redis.opsForValue().setIfAbsent(key, "1", IDEMPOTENCY_TTL);
            return Boolean.FALSE.equals(acquired);
        } catch (RuntimeException ex) {
            log.warn("AI 自动报警幂等 Redis SETNX 异常，降级不做去重: key={}", key, ex);
            return false; // 宁愿重复也不丢
        }
    }

    /** 异常/日志中的紧凑 summary，不依赖 issue.toString（保持 NPE-safe）。 */
    private String safeSummary(AiInspectionDetectedIssue issue) {
        if (issue == null) return "null";
        return "AiInspectionDetectedIssue{deviceId=" + issue.getDeviceId()
                + ", deviceCode=" + issue.getDeviceCode()
                + ", severity=" + issue.getSeverity()
                + ", type=" + issue.getAlarmType() + "}";
    }
}
