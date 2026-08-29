package dev.reboot.service;

import dev.reboot.rag.TextChunker;
import dev.reboot.rag.VectorStore;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RAG 入库服务（Week 11 Day 72）。
 *
 * <p>编排文档切片 → 向量化 → 入库流程，返回本次写入的块数量。</p>
 *
 * @author AI 助手
 * @since 2026-08-29
 */
@Service
public class RagIngestionService {

    private final TextChunker textChunker;
    private final VectorStore vectorStore;

    public RagIngestionService(TextChunker textChunker, VectorStore vectorStore) {
        this.textChunker = textChunker;
        this.vectorStore = vectorStore;
    }

    public int ingest(String source, String content) {
        List<Document> chunks = textChunker.chunk(source, content);
        vectorStore.add(chunks);
        return chunks.size();
    }
}
