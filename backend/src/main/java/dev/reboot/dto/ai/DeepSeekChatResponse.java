package dev.reboot.dto.ai;

import java.util.List;

/**
 * DeepSeek Chat Completions 响应体。
 *
 * @author AI 助手
 * @since 2026-08-28
 */
public class DeepSeekChatResponse {

    private String id;
    private String object;
    private Long created;
    private String model;
    private List<DeepSeekChoice> choices;
    private DeepSeekUsage usage;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getObject() { return object; }
    public void setObject(String object) { this.object = object; }
    public Long getCreated() { return created; }
    public void setCreated(Long created) { this.created = created; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public List<DeepSeekChoice> getChoices() { return choices; }
    public void setChoices(List<DeepSeekChoice> choices) { this.choices = choices; }
    public DeepSeekUsage getUsage() { return usage; }
    public void setUsage(DeepSeekUsage usage) { this.usage = usage; }
}
