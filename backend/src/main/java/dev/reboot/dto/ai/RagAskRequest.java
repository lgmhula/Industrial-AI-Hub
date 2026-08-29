package dev.reboot.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * RAG 知识问答请求（Week 11 Day 75）。
 *
 * @author AI 助手
 * @since 2026-08-29
 */
public class RagAskRequest {

    @NotBlank(message = "问题不能为空")
    @Size(max = 2000, message = "问题不能超过 2000 字")
    private String question;

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
}
