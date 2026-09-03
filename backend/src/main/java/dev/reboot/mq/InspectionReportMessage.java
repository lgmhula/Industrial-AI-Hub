package dev.reboot.mq;

import dev.reboot.dto.ai.AiInspectionDetectedIssue;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 巡检日报消息 DTO — Agent 经 RabbitMQ 投递给 Report Consumer（Day 85，ADR 0031）。
 *
 * <p>本类只承担「Agent → Consumer」段的消息契约，<b>不感知</b> SSE / Push Gateway /
 * 浏览器连接；任何 emitter / SseEmitter 引用都属于 Phase 2 之后的 Push Gateway 层，
 * 不得反向污染本 DTO。</p>
 *
 * <h3>字段来源</h3>
 * <table>
 *   <tr><th>字段</th><th>来源</th><th>用途</th></tr>
 *   <tr><td>reportDate</td><td>AiInspectionReportResult.reportDate</td><td>幂等键 {@code inspection:{reportDate}:{siteId}}</td></tr>
 *   <tr><td>report</td><td>AiInspectionReportResult.report</td><td>Consumer 路由后写入 SSE payload</td></tr>
 *   <tr><td>toolRounds / toolCalls / deviceCount / alarmCount / truncated</td><td>AiInspectionReportResult 同名</td><td>审计摘要，便于 operation_log 与前端展示</td></tr>
 *   <tr><td>autoAlarmCount / detectedIssues</td><td>AiInspectionReportResult 同名（Day 86 AI→ALARM 闭环产出）</td><td>前端 SSE 渲染：AI 自动生成的报警数量标签 + 结构化异常折叠卡（severity badge / 设备 / 类型 / 描述）</td></tr>
 *   <tr><td>siteIds</td><td>Agent 巡检覆盖的站点集合（ADMIN 巡检全部时可为空 List）</td><td>Consumer 路由范围；空集 = 全站点（ADMIN 语义，ADR 0020/0031 §5.4）</td></tr>
 *   <tr><td>triggeredByUserId</td><td>触发巡检的 ADMIN userId</td><td><b>仅日志/审计用</b>，Consumer 不得据此越权路由（ADR 0031 §5.5：Push Gateway 只认 emitter 绑定 userId）</td></tr>
 *   <tr><td>generatedAt</td><td>消息构建时间</td><td>排查消息延迟、Consumer 重投</td></tr>
 * </table>
 *
 * <h3>序列化</h3>
 * <p>实现 {@link Serializable} 以兼容 {@code Jackson2JsonMessageConverter}
 * （与 {@link AlarmMessage} 同模式，ADR 0031 §6.1）。</p>
 *
 * @author AI 助手
 * @since 2026-08-31 (Day 85, Phase 1), Day 87 扩 autoAlarmCount + detectedIssues
 */
public class InspectionReportMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private LocalDate reportDate;
    private String report;
    private int toolRounds;
    private int toolCalls;
    private int deviceCount;
    private int alarmCount;
    private boolean truncated;
    /** Day 86：AiAlarmAutoCreator 回填的「AI 自动生成报警数量」，前端作 meta tag。 */
    private int autoAlarmCount;
    /** Day 86：AI 结构化异常列表，前端渲染 severity badge + 设备/类型/描述折叠卡。 */
    private List<AiInspectionDetectedIssue> detectedIssues = new ArrayList<>();
    /** 路由范围；空 List 表示全站点（ADMIN 巡检语义）。 */
    private List<Long> siteIds = List.of();
    /** 触发者 userId — 仅审计用，Consumer/Push Gateway 不得据此越权路由（ADR 0031 §5.5）。 */
    private Long triggeredByUserId;
    private LocalDateTime generatedAt;

    public InspectionReportMessage() {}

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
    public int getAutoAlarmCount() { return autoAlarmCount; }
    public void setAutoAlarmCount(int autoAlarmCount) { this.autoAlarmCount = autoAlarmCount; }
    public List<AiInspectionDetectedIssue> getDetectedIssues() { return detectedIssues; }
    public void setDetectedIssues(List<AiInspectionDetectedIssue> detectedIssues) {
        this.detectedIssues = detectedIssues == null ? new ArrayList<>() : detectedIssues;
    }
    public List<Long> getSiteIds() { return siteIds; }
    public void setSiteIds(List<Long> siteIds) { this.siteIds = siteIds == null ? List.of() : siteIds; }
    public Long getTriggeredByUserId() { return triggeredByUserId; }
    public void setTriggeredByUserId(Long triggeredByUserId) { this.triggeredByUserId = triggeredByUserId; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }

    /** 审计用紧凑摘要（Producer 日志 / Consumer 日志，避免泄漏完整日报正文）。 */
    @Override
    public String toString() {
        return "InspectionReportMessage{date=" + reportDate
                + ", rounds=" + toolRounds
                + ", calls=" + toolCalls
                + ", devices=" + deviceCount
                + ", alarms=" + alarmCount
                + ", autoAlarms=" + autoAlarmCount
                + ", issues=" + (detectedIssues == null ? 0 : detectedIssues.size())
                + ", truncated=" + truncated
                + ", sites=" + siteIds.size()
                + ", triggeredBy=" + triggeredByUserId + "}";
    }
}
