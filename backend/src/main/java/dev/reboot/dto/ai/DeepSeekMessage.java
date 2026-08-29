package dev.reboot.dto.ai;

/**
 * DeepSeek Chat Completions 消息项。
 *
 * @author AI 助手
 * @since 2026-08-28
 */
public class DeepSeekMessage {

    private String role;
    private String content;

    public DeepSeekMessage() {
    }

    public DeepSeekMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public static DeepSeekMessage system(String content) {
        return new DeepSeekMessage("system", content);
    }

    public static DeepSeekMessage user(String content) {
        return new DeepSeekMessage("user", content);
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
