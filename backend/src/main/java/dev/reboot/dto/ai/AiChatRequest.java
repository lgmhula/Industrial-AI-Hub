package dev.reboot.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * AI 对话请求（面向业务调用方）。
 *
 * @author AI 助手
 * @since 2026-08-28
 */
public class AiChatRequest {

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 4000, message = "消息内容不能超过 4000 字")
    private String message;

    @Size(max = 2000, message = "系统提示词不能超过 2000 字")
    private String systemPrompt;

    @Size(max = 64, message = "模型名称不能超过 64 字")
    private String model;

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
}
