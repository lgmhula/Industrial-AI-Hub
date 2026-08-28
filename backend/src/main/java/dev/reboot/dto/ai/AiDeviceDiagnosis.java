package dev.reboot.dto.ai;

import java.util.List;

/**
 * 设备健康诊断（DeepSeek 结构化 JSON 输出）。
 *
 * @author AI 助手
 * @since 2026-08-28
 */
public class AiDeviceDiagnosis {

    private String healthLevel;
    private String summary;
    private List<String> issues;
    private List<String> suggestedActions;

    public String getHealthLevel() { return healthLevel; }
    public void setHealthLevel(String healthLevel) { this.healthLevel = healthLevel; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public List<String> getIssues() { return issues; }
    public void setIssues(List<String> issues) { this.issues = issues; }
    public List<String> getSuggestedActions() { return suggestedActions; }
    public void setSuggestedActions(List<String> suggestedActions) { this.suggestedActions = suggestedActions; }
}
