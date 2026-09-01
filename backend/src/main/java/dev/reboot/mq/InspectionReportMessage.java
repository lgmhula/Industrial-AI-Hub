package dev.reboot.mq;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * @since 2026-08-31 (Day 85, Phase 1)
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
                + ", truncated=" + truncated
                + ", sites=" + siteIds.size()
                + ", triggeredBy=" + triggeredByUserId + "}";
    }
}
