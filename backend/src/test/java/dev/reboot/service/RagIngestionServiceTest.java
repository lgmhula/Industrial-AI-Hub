package dev.reboot.service;

import dev.reboot.config.RagProperties;
import dev.reboot.rag.LocalHashEmbeddingModel;
import dev.reboot.rag.SimpleVectorStore;
import dev.reboot.rag.TextChunker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RagIngestionService 编排单元测试。
 *
 * @author AI 助手
 * @since 2026-08-29
 */
class RagIngestionServiceTest {

    @Test
    void ingest_shouldChunkEmbedAndStore() {
        RagProperties properties = new RagProperties();
        properties.setEmbeddingDimensions(64);
        properties.setChunkSize(24);
        properties.setChunkOverlap(6);
        TextChunker textChunker = new TextChunker(properties);
        SimpleVectorStore vectorStore =
                new SimpleVectorStore(new LocalHashEmbeddingModel(properties));
        RagIngestionService service = new RagIngestionService(textChunker, vectorStore);

        int count = service.ingest("manual", "设备温度过高。传感器读数异常。建议现场检查。");

        assertTrue(count >= 1);
        assertEquals(count, vectorStore.size());
    }
}
