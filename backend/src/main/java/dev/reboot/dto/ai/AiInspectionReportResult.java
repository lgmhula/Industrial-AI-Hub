package dev.reboot.dto.ai;

import java.time.LocalDate;
import java.util.List;

/**
 * AI 设备巡检日报结果（Week 12 Day 83，ADR 0030）。
 *
 * <p>由 Agent 通过 MCP 客户端只读工具自动巡检生成；{@link #deviceCount} /
 * {@link #alarmCount} 来自巡检会话的真实工具调用记录，{@link #truncated}
 * 表示是否达到工具调用轮次硬限。</p>
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

    /** 审计用紧凑摘要（写入 operation_log.description，@OperationLog {ret} 占位符）。 */
    @Override
    public String toString() {
        return "AiInspectionReportResult{date=" + reportDate
                + ", rounds=" + toolRounds
                + ", calls=" + toolCalls
                + ", devices=" + deviceCount
                + ", alarms=" + alarmCount
                + ", truncated=" + truncated + "}";
    }
}
