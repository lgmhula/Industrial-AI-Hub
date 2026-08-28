package dev.reboot.dto.ai;

/**
 * 单次工具调用轨迹（Day 68 Function Calling，前端透明展示）。
 *
 * @author AI 助手
 * @since 2026-08-29
 */
public class AiToolCallTrace {

    private String toolName;
    private boolean success;

    public AiToolCallTrace() {
    }

    public AiToolCallTrace(String toolName, boolean success) {
        this.toolName = toolName;
        this.success = success;
    }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
}
