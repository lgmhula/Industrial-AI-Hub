package dev.reboot.dto.ai;

/**
 * DeepSeek JSON 输出模式参数（response_format）。
 *
 * @author AI 助手
 * @since 2026-08-28
 */
public class DeepSeekResponseFormat {

    private String type;

    public DeepSeekResponseFormat() {
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
