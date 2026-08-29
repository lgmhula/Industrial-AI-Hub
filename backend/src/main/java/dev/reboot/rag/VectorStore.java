package dev.reboot.rag;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 向量存储抽象（ADR 0024）。
 *
 * <p>业务层只依赖此接口；当前由 {@link SimpleVectorStore} 提供内存实现，
 * 后续可替换为 Qdrant 等真实向量库适配器。</p>
 *
 * @author AI 助手
 * @since 2026-08-29
 */
public interface VectorStore {

    void add(List<Document> documents);

    void add(Document document);

    List<Document> similaritySearch(String query, int topK);

    int size();
}
