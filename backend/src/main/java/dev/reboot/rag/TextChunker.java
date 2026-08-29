package dev.reboot.rag;

import dev.reboot.config.RagProperties;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档切片器（Week 11，ADR 0024）。
 *
 * <p>按句子聚合到不超过 {@code chunk-size} 的块，相邻块按 {@code chunk-overlap}
 * 保留尾部上下文，降低跨边界语义丢失。</p>
 *
 * @author AI 助手
 * @since 2026-08-29
 */
@Component
public class TextChunker {

    private final RagProperties properties;

    public TextChunker(RagProperties properties) {
        this.properties = properties;
    }

    public List<Document> chunk(String source, String content) {
        String text = content == null ? "" : content;
        if (text.isBlank()) {
            return List.of();
        }

        List<String> chunks = buildChunks(splitSentences(text));
        String normalizedSource = source == null || source.isBlank() ? "unknown" : source;
        List<Document> documents = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("source", normalizedSource);
            metadata.put("chunkIndex", i);
            metadata.put("chunkCount", chunks.size());
            documents.add(Document.builder()
                    .id(normalizedSource + "#" + i)
                    .text(chunks.get(i))
                    .metadata(metadata)
                    .build());
        }
        return documents;
    }

    private List<String> buildChunks(List<String> sentences) {
        int maxChars = Math.max(1, properties.getChunkSize());
        int overlap = Math.max(0, properties.getChunkOverlap());
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String sentence : sentences) {
            if (current.length() > 0 && current.length() + sentence.length() > maxChars) {
                chunks.add(current.toString().trim());
                int start = Math.max(0, current.length() - overlap);
                current = new StringBuilder(current.substring(start));
            }
            if (sentence.length() > maxChars && current.length() == 0) {
                chunks.add(sentence.trim());
                continue;
            }
            current.append(sentence);
        }
        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }

    private List<String> splitSentences(String text) {
        String[] parts = text.split("(?<=[。！？.!?；;\\n])");
        List<String> sentences = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                sentences.add(trimmed);
            }
        }
        return sentences;
    }
}
