package dev.reboot.dto.ai;

import java.util.List;

/**
 * RAG 知识问答结果（Week 11 Day 75）。
 *
 * @author AI 助手
 * @since 2026-08-29
 */
public class RagAnswerResult {

    private String answer;
    private List<KnowledgeChunk> sources;

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public List<KnowledgeChunk> getSources() { return sources; }
    public void setSources(List<KnowledgeChunk> sources) { this.sources = sources; }

    @Override
    public String toString() {
        String preview = answer == null ? "" : answer;
        if (preview.length() > 80) {
            preview = preview.substring(0, 80) + "...";
        }
        return "RagAnswerResult{sources=" + (sources == null ? 0 : sources.size())
                + ", answer=" + preview + "}";
    }
}
