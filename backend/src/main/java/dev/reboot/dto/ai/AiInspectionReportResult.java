package dev.reboot.dto.ai;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 设备巡检日报结果（Week 12 Day 83，ADR 0030；Day 86 扩 detectedIssues）。
 *
 * <p>由 Agent 通过 MCP 客户端只读工具自动巡检生成；{@link #deviceCount} /
 * {@link #alarmCount} 来自巡检会话的真实工具调用记录，{@link #truncated}
 * 表示是否达到工具调用轮次硬限。</p>
 *
 * <p>Day 86 AI 与业务闭环：{@link #detectedIssues} 是 AI 巡检过程中识别到的结构化异常项，
 * 由 McpInspectionAgentService 返回后交给 {@code AiAlarmAutoCreator} 自动写入业务 alarm 表，
 * 形成 AI 发现 → 触发报警 → 运维流程确认/解决 的全链路闭环。</p>
 *
 * @author AI 助手
 * @since 2026-08-30
 */
public class AiInspectionReportResult {

    private LocalDate reportDate;
    private String report;
    private int toolRounds;
    private int toolCalls;
    private int deviceCount;
    private int alarmCount;
    private boolean truncated;
    private List<AiToolCallTrace> toolTrace = List.of();

    /** AI 识别到的结构化异常（Day 86），用于自动生成业务报警。 */
    private List<AiInspectionDetectedIssue> detectedIssues = new ArrayList<>();

    /** 本次巡检自动生成报警数（AiAlarmAutoCreator 统计，回填便于审计）。 */
    private int autoAlarmCount;

    public LocalDate getReportDate() { return reportDate; }
    public void setReportDate(LocalDate reportDate) { this.reportDate = reportDate; }
    public String getReport() { return report; }
    public void setReport(String report) { this.report = report; }
    public int getToolRounds() { return toolRounds; }
    public void setToolRounds(int toolRounds) { this.toolRounds = toolRounds; }
    public int getToolCalls() { return toolCalls; }
    public void setToolCalls(int toolCalls) { this.toolCalls = toolCalls; }
    public int getDeviceCount() { return deviceCount; }
    public void setDeviceCount(int deviceCount) { this.deviceCount = deviceCount; }
    public int getAlarmCount() { return alarmCount; }
    public void setAlarmCount(int alarmCount) { this.alarmCount = alarmCount; }
    public boolean isTruncated() { return truncated; }
    public void setTruncated(boolean truncated) { this.truncated = truncated; }
    public List<AiToolCallTrace> getToolTrace() { return toolTrace; }
    public void setToolTrace(List<AiToolCallTrace> toolTrace) { this.toolTrace = toolTrace; }
    public List<AiInspectionDetectedIssue> getDetectedIssues() { return detectedIssues; }
    public void setDetectedIssues(List<AiInspectionDetectedIssue> detectedIssues) {
        this.detectedIssues = detectedIssues == null ? new ArrayList<>() : detectedIssues;
    }
    public int getAutoAlarmCount() { return autoAlarmCount; }
    public void setAutoAlarmCount(int autoAlarmCount) { this.autoAlarmCount = autoAlarmCount; }

    /** 审计用紧凑摘要（写入 operation_log.description，@OperationLog {ret} 占位符）。 */
    @Override
    public String toString() {
        return "AiInspectionReportResult{date=" + reportDate
                + ", rounds=" + toolRounds
                + ", calls=" + toolCalls
                + ", devices=" + deviceCount
                + ", alarms=" + alarmCount
                + ", issues=" + (detectedIssues == null ? 0 : detectedIssues.size())
                + ", autoAlarms=" + autoAlarmCount
                + ", truncated=" + truncated + "}";
    }
}
