package dev.reboot.service;

import dev.reboot.config.RagProperties;
import dev.reboot.dto.ai.KnowledgeChunk;
import dev.reboot.enums.ErrorCode;
import dev.reboot.exception.BusinessException;
import dev.reboot.rag.LocalHashEmbeddingModel;
import dev.reboot.rag.SimpleVectorStore;
import dev.reboot.rag.TextChunker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RagRetrievalService 单元测试。
 *
 * @author AI 助手
 * @since 2026-08-29
 */
class RagRetrievalServiceTest {

    private RagRetrievalService retrievalService;

    @BeforeEach
    void setUp() {
        RagProperties properties = new RagProperties();
        properties.setEmbeddingDimensions(64);
        properties.setChunkSize(24);
        properties.setChunkOverlap(6);
        SimpleVectorStore vectorStore =
                new SimpleVectorStore(new LocalHashEmbeddingModel(properties));
        RagIngestionService ingestionService =
                new RagIngestionService(new TextChunker(properties), vectorStore);
        ingestionService.ingest("manual", "设备温度过高。传感器读数异常。建议现场检查。");
        ingestionService.ingest("user-guide", "用户登录成功。角色权限配置。");
        retrievalService = new RagRetrievalService(vectorStore);
    }

    @Test
    void retrieve_shouldReturnTopKChunksWithScore() {
        List<KnowledgeChunk> chunks = retrievalService.retrieve("设备温度过高", 3);

        assertTrue(chunks.size() >= 1 && chunks.size() <= 3);
        KnowledgeChunk first = chunks.get(0);
        assertEquals("manual", first.getSource());
        assertNotNull(first.getContent());
        assertNotNull(first.getScore());
    }

    @Test
    void retrieve_blankQuestion_shouldThrowBadRequest() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> retrievalService.retrieve("   ", 5));

        assertEquals(ErrorCode.BAD_REQUEST, e.getErrorCode());
    }

    @Test
    void retrieve_topKShouldBeClamped() {
        List<KnowledgeChunk> chunks = retrievalService.retrieve("设备", 100);

        assertTrue(chunks.size() <= 20);
    }

    @Test
    void retrieve_resultsShouldBeSortedByScoreDesc() {
        List<KnowledgeChunk> chunks = retrievalService.retrieve("设备", 3);

        for (int i = 1; i < chunks.size(); i++) {
            assertTrue(chunks.get(i - 1).getScore() >= chunks.get(i).getScore());
        }
    }
}
