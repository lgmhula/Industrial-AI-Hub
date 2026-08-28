package dev.reboot.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DeepSeek Chat Completions 单个候选结果。
 *
 * @author AI 助手
 * @since 2026-08-28
 */
public class DeepSeekChoice {

    private Integer index;
    private DeepSeekMessage message;

    @JsonProperty("finish_reason")
    private String finishReason;

    public Integer getIndex() { return index; }
    public void setIndex(Integer index) { this.index = index; }
    public DeepSeekMessage getMessage() { return message; }
    public void setMessage(DeepSeekMessage message) { this.message = message; }
    public String getFinishReason() { return finishReason; }
    public void setFinishReason(String finishReason) { this.finishReason = finishReason; }
}
