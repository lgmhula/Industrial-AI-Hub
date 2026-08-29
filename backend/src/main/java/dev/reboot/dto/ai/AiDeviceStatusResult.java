package dev.reboot.dto.ai;

import java.util.List;

/**
 * AI 设备状态问答结果（Day 68 Function Calling）。
 *
 * <p>{@link #referencedRealTime} = false 表示模型未调用任何工具（未参考实时数据），
 * 前端需标注「未参考实时数据」；{@link #truncated} = true 表示已达 3 轮工具调用硬限。</p>
 *
 * @author AI 助手
 * @since 2026-08-29
 */
public class AiDeviceStatusResult {

    private Long deviceId;
    private String answer;
    private int toolRounds;
    private int toolCalls;
    private boolean referencedRealTime;
    private boolean truncated;
    private List<AiToolCallTrace> toolTrace = List.of();

    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public int getToolRounds() { return toolRounds; }
    public void setToolRounds(int toolRounds) { this.toolRounds = toolRounds; }
    public int getToolCalls() { return toolCalls; }
    public void setToolCalls(int toolCalls) { this.toolCalls = toolCalls; }
    public boolean isReferencedRealTime() { return referencedRealTime; }
    public void setReferencedRealTime(boolean referencedRealTime) { this.referencedRealTime = referencedRealTime; }
    public boolean isTruncated() { return truncated; }
    public void setTruncated(boolean truncated) { this.truncated = truncated; }
    public List<AiToolCallTrace> getToolTrace() { return toolTrace; }
    public void setToolTrace(List<AiToolCallTrace> toolTrace) { this.toolTrace = toolTrace; }

    /** 审计用紧凑摘要（写入 operation_log.description，@OperationLog {ret} 占位符）。 */
    @Override
    public String toString() {
        return "AiDeviceStatusResult{deviceId=" + deviceId
                + ", rounds=" + toolRounds
                + ", calls=" + toolCalls
                + ", realtime=" + referencedRealTime
                + ", truncated=" + truncated + "}";
    }
}
