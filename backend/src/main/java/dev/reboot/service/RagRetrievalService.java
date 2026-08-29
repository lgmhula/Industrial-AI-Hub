package dev.reboot.service;

import dev.reboot.dto.ai.KnowledgeChunk;
import dev.reboot.enums.ErrorCode;
import dev.reboot.exception.BusinessException;
import dev.reboot.rag.VectorStore;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * RAG 检索服务（Week 11 Day 73）。
 *
 * <p>根据用户问题从 {@link VectorStore} 检索 Top-K 相关片段，并把 Spring AI
 * {@code Document} 映射为业务 DTO {@link KnowledgeChunk}。</p>
 *
 * @author AI 助手
 * @since 2026-08-29
 */
@Service
public class RagRetrievalService {

    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 20;

    private final VectorStore vectorStore;

    public RagRetrievalService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public List<KnowledgeChunk> retrieve(String question, Integer topK) {
        if (!StringUtils.hasText(question)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "检索问题不能为空");
        }
        int k = topK == null ? DEFAULT_TOP_K : Math.max(1, Math.min(MAX_TOP_K, topK));
        return vectorStore.similaritySearch(question.trim(), k).stream()
                .map(this::toChunk)
                .toList();
    }

    private KnowledgeChunk toChunk(Document document) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setSource((String) document.getMetadata().get("source"));
        chunk.setChunkIndex((Integer) document.getMetadata().get("chunkIndex"));
        chunk.setChunkCount((Integer) document.getMetadata().get("chunkCount"));
        chunk.setContent(document.getText());
        chunk.setScore((Double) document.getMetadata().get("score"));
        return chunk;
    }
}
