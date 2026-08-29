package dev.reboot.rag;

import dev.reboot.config.RagProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TextChunker 单元测试。
 *
 * @author AI 助手
 * @since 2026-08-29
 */
class TextChunkerTest {

    @Test
    void chunk_shouldSplitIntoDocumentsWithMetadata() {
        RagProperties properties = new RagProperties();
        properties.setChunkSize(24);
        properties.setChunkOverlap(6);
        TextChunker chunker = new TextChunker(properties);

        List<Document> documents = chunker.chunk("device-manual",
                "设备温度过高。传感器读数异常。建议现场检查。冷却风扇转速下降。");

        assertFalse(documents.isEmpty());
        Document first = documents.get(0);
        assertEquals("device-manual", first.getMetadata().get("source"));
        assertEquals(0, first.getMetadata().get("chunkIndex"));
        assertEquals(documents.size(), first.getMetadata().get("chunkCount"));
        assertFalse(first.getText().isBlank());
        assertTrue(first.getId().startsWith("device-manual#"));
    }

    @Test
    void chunk_blankContent_shouldReturnEmptyList() {
        RagProperties properties = new RagProperties();
        TextChunker chunker = new TextChunker(properties);

        assertTrue(chunker.chunk("manual", "   ").isEmpty());
    }

    @Test
    void chunk_shouldProduceNonBlankChunksForLongText() {
        RagProperties properties = new RagProperties();
        properties.setChunkSize(30);
        properties.setChunkOverlap(8);
        TextChunker chunker = new TextChunker(properties);

        List<Document> documents = chunker.chunk("manual",
                "工业设备运维手册。设备需要定期巡检。温度传感器需要校准。告警需要及时确认处理。");

        assertTrue(documents.size() >= 1);
        for (Document document : documents) {
            assertFalse(document.getText().isBlank());
        }
    }
}
