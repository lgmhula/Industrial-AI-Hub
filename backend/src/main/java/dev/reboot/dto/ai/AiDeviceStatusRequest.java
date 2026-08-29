package dev.reboot.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * AI 设备状态问答请求（Day 68 Function Calling）。
 *
 * @author AI 助手
 * @since 2026-08-29
 */
public class AiDeviceStatusRequest {

    @NotNull(message = "设备 ID 不能为空")
    private Long deviceId;

    @NotBlank(message = "问题不能为空")
    @Size(max = 2000, message = "问题不能超过 2000 字")
    private String question;

    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
}
