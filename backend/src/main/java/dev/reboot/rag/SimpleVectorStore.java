package dev.reboot.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存向量库 + JSON 持久化（ADR 0024 第一阶段实现）。
 *
 * <p>线程安全的文档 + 向量内存存储；检索按余弦相似度降序返回。
 * {@code @PostConstruct} 自动从磁盘加载，{@code add()} 实时回写磁盘，
 * 解决「后端重启知识库清空，前端反复提示未找到」的问题。
 *
 * <p>仅用于本地开发/单测，后续由 Qdrant 适配器替换。
 *
 * @author AI 助手
 * @since 2026-08-29
 */
@Component
public class SimpleVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(SimpleVectorStore.class);
    private static final String FALLBACK_DIR = "./data";

    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Path persistPath;

    public SimpleVectorStore(EmbeddingModel embeddingModel,
                             ObjectMapper objectMapper,
                             @Value("${iah.vectorstore.path:${APP_BASE_DIR:./}/data/vectorstore.json}")
                             String persistPath) {
        this.embeddingModel = embeddingModel;
        this.objectMapper = objectMapper;
        Path p = Paths.get(persistPath).toAbsolutePath().normalize();
        // 不允许 test profile 或 app 启动时路径解析到不存在的子目录
        try {
            Path parent = p.getParent();
            if (parent != null && !Files.isDirectory(parent)) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            log.warn("无法创建向量库持久化目录 {}，回退到 {}", p.getParent(), FALLBACK_DIR);
            try {
                Files.createDirectories(Paths.get(FALLBACK_DIR));
                p = Paths.get(FALLBACK_DIR, "vectorstore.json").toAbsolutePath();
            } catch (IOException ignored) {
                // 最后兜底：内存模式（不持久化）
            }
        }
        this.persistPath = p;
    }

    @PostConstruct
    public void loadFromDisk() {
        File f = persistPath.toFile();
        if (!f.exists() || f.length() == 0) return;
        try {
            List<SerEntry> list = objectMapper.readValue(f, new TypeReference<List<SerEntry>>() {});
            for (SerEntry se : list) {
                Document doc = new Document(se.id, se.content, se.metadata);
                entries.put(se.id, new Entry(doc, se.vector));
            }
            log.info("向量库从磁盘加载完成: {} entries, file={}", entries.size(), persistPath);
        } catch (IOException e) {
            log.warn("向量库持久化文件读取失败，跳过加载: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void flushToDisk() {
        save();
    }

    private synchronized void save() {
        try {
            List<SerEntry> list = new ArrayList<>(entries.size());
            for (Entry e : entries.values()) {
                list.add(new SerEntry(
                        e.document().getId(),
                        e.document().getText(),
                        e.document().getMetadata(),
                        e.vector()));
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(persistPath.toFile(), list);
        } catch (IOException e) {
            log.warn("向量库持久化写入失败: {}", e.getMessage());
        }
    }

    @Override
    public void add(List<Document> documents) {
        for (Document document : documents) {
            float[] vector = embeddingModel.embed(document);
            entries.put(document.getId(), new Entry(document, vector));
        }
        save();
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
        if (left == null || right == null || left.length == 0 || right.length == 0) return 0.0;
        int n = Math.min(left.length, right.length);
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < n; i++) {
            dot += (double) left[i] * right[i];
            normA += (double) left[i] * left[i];
            normB += (double) right[i] * right[i];
        }
        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private record Entry(Document document, float[] vector) {}

    private record ScoredDocument(Document document, double score) {}

    /** JSON 序列化结构（Document 内部字段不直接 Jackson 友好）。 */
    private static class SerEntry {
        public String id;
        public String content;
        public Map<String, Object> metadata;
        public float[] vector;

        public SerEntry() {}
        SerEntry(String id, String content, Map<String, Object> metadata, float[] vector) {
            this.id = id; this.content = content; this.metadata = metadata; this.vector = vector;
        }
    }
}
