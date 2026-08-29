package dev.reboot.dto.ai;

/**
 * RAG 检索结果块（Week 11 Day 73）。
 *
 * <p>由 {@code dev.reboot.service.RagRetrievalService} 从 Spring AI
 * {@code Document} 映射而来，避免把框架类型直接暴露给上层。</p>
 *
 * @author AI 助手
 * @since 2026-08-29
 */
public class KnowledgeChunk {

    private String source;
    private Integer chunkIndex;
    private Integer chunkCount;
    private String content;
    private Double score;

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
    public Integer getChunkCount() { return chunkCount; }
    public void setChunkCount(Integer chunkCount) { this.chunkCount = chunkCount; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
}
