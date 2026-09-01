package dev.reboot.dto.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * AI 巡检日报中的结构化异常项（Day 86：AI 巡检异常自动生成报警）。
 *
 * <p>与自由文本的 {@link AiInspectionReportResult#getReport()} 并列，作为 AI 自动生成业务报警
 * 的唯一可信来源；避免在 Service 层硬解析中文自由文本（鲁棒性差）。</p>
 *
 * <ul>
 *   <li>{@code deviceId} 与 {@code deviceCode} 二选一：若 {@code deviceId} 为空，
 *       AiAlarmAutoCreator 会通过 DeviceMapper.findByCode 反查；仍找不到则跳过该 issue 并 WARN。</li>
 *   <li>{@code severity} 取值与 alarm.alarm_level 对齐：1=一般 2=重要 3=紧急。</li>
 *   <li>{@code alarmType} 建议大写字母+下划线短标记（如 TEMPERATURE_HIGH/CONNECTION_LOST/ERROR_RATE_SPIKE），
 *       用于 24h 去重键区分不同规则。</li>
 *   <li>{@code description} 会原样写入 alarm_message，建议 ≤ 500 字符。</li>
 *   <li>{@code occurredAt} 为缺失时，AiAlarmAutoCreator 回退为 {@code LocalDateTime.now()}。</li>
 * </ul>
 *
 * @author AI 助手
 * @since 2026-09-01 (Day 86)
 */
public class AiInspectionDetectedIssue {

    private Long deviceId;

    private String deviceCode;

    @Min(1) @Max(3)
    private int severity;

    @NotBlank(message = "alarmType 不能为空")
    private String alarmType;

    @NotBlank(message = "description 不能为空")
    @Size(max = 500, message = "description 最多 500 字符")
    private String description;

    private LocalDateTime occurredAt;

    public AiInspectionDetectedIssue() {}

    public AiInspectionDetectedIssue(Long deviceId, String deviceCode, int severity,
                                      String alarmType, String description,
                                      LocalDateTime occurredAt) {
        this.deviceId = deviceId;
        this.deviceCode = deviceCode;
        this.severity = severity;
        this.alarmType = alarmType;
        this.description = description;
        this.occurredAt = occurredAt;
    }

    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }
    public int getSeverity() { return severity; }
    public void setSeverity(int severity) { this.severity = severity; }
    public String getAlarmType() { return alarmType; }
    public void setAlarmType(String alarmType) { this.alarmType = alarmType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
}
