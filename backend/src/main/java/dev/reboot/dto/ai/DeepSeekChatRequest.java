package dev.reboot.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * DeepSeek Chat Completions 请求体。
 *
 * @author AI 助手
 * @since 2026-08-28
 */
public class DeepSeekChatRequest {

    private String model;
    private List<DeepSeekMessage> messages;
    private Double temperature;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    private Boolean stream;

    @JsonProperty("response_format")
    private DeepSeekResponseFormat responseFormat;

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public List<DeepSeekMessage> getMessages() { return messages; }
    public void setMessages(List<DeepSeekMessage> messages) { this.messages = messages; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
    public Boolean getStream() { return stream; }
    public void setStream(Boolean stream) { this.stream = stream; }
    public DeepSeekResponseFormat getResponseFormat() { return responseFormat; }
    public void setResponseFormat(DeepSeekResponseFormat responseFormat) { this.responseFormat = responseFormat; }
}
