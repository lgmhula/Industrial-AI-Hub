package dev.reboot.dto.ai;

import java.util.List;

/**
 * 告警摘要（DeepSeek 结构化 JSON 输出）。
 *
 * @author AI 助手
 * @since 2026-08-28
 */
public class AiAlarmSummary {

    private String summary;
    private List<String> possibleCauses;
    private List<String> suggestedActions;
    private String priority;

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public List<String> getPossibleCauses() { return possibleCauses; }
    public void setPossibleCauses(List<String> possibleCauses) { this.possibleCauses = possibleCauses; }
    public List<String> getSuggestedActions() { return suggestedActions; }
    public void setSuggestedActions(List<String> suggestedActions) { this.suggestedActions = suggestedActions; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
}
