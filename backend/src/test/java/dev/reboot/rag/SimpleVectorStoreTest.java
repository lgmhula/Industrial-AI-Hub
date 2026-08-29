package dev.reboot.rag;

import dev.reboot.config.RagProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * SimpleVectorStore 单元测试。
 *
 * @author AI 助手
 * @since 2026-08-29
 */
class SimpleVectorStoreTest {

    private SimpleVectorStore vectorStore;

    @BeforeEach
    void setUp() {
        RagProperties properties = new RagProperties();
        properties.setEmbeddingDimensions(64);
        vectorStore = new SimpleVectorStore(new LocalHashEmbeddingModel(properties));
    }

    @Test
    void addAndSimilaritySearch_shouldReturnExactMatchFirst() {
        vectorStore.add(document("1", "设备温度过高"));
        vectorStore.add(document("2", "温度过高告警"));
        vectorStore.add(document("3", "用户登录成功"));

        List<Document> results = vectorStore.similaritySearch("设备温度过高", 2);

        assertEquals(2, results.size());
        assertEquals("设备温度过高", results.get(0).getText());
        assertNotNull(results.get(0).getMetadata().get("score"));
    }

    @Test
    void size_shouldReflectStoredDocuments() {
        vectorStore.add(document("1", "设备温度过高"));
        vectorStore.add(document("2", "温度过高告警"));

        assertEquals(2, vectorStore.size());
    }

    private Document document(String id, String text) {
        return Document.builder().id(id).text(text).build();
    }
}
