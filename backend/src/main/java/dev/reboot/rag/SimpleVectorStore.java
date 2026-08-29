package dev.reboot.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存向量库（ADR 0024 第一阶段实现）。
 *
 * <p>线程安全的文档 + 向量内存存储；检索按余弦相似度降序返回。仅用于本地
 * 开发/单测，后续由 Qdrant 适配器替换。</p>
 *
 * @author AI 助手
 * @since 2026-08-29
 */
@Component
public class SimpleVectorStore implements VectorStore {

    private final EmbeddingModel embeddingModel;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public SimpleVectorStore(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public void add(List<Document> documents) {
        for (Document document : documents) {
            float[] vector = embeddingModel.embed(document);
            entries.put(document.getId(), new Entry(document, vector));
        }
    }

    @Override
    public void add(Document document) {
        add(List.of(document));
    }

    @Override
    public List<Document> similaritySearch(String query, int topK) {
        float[] queryVector = embeddingModel.embed(query);
        int limit = Math.max(0, topK);
        return entries.values().stream()
                .map(entry -> new ScoredDocument(entry.document(), cosine(queryVector, entry.vector())))
                .sorted(Comparator.comparingDouble(ScoredDocument::score).reversed())
                .limit(limit)
                .map(scored -> {
                    scored.document().getMetadata().put("score", scored.score());
                    return scored.document();
                })
                .toList();
    }

    @Override
    public int size() {
        return entries.size();
    }

    private double cosine(float[] left, float[] right) {
        double dot = 0.0;
        for (int i = 0; i < left.length; i++) {
            dot += (double) left[i] * right[i];
        }
        return dot;
    }

    private record Entry(Document document, float[] vector) {
    }

    private record ScoredDocument(Document document, double score) {
    }
}
