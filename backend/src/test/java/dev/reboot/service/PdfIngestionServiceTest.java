package dev.reboot.service;

import dev.reboot.config.RagProperties;
import dev.reboot.dto.ai.RagIngestResult;
import dev.reboot.enums.ErrorCode;
import dev.reboot.exception.BusinessException;
import dev.reboot.rag.LocalHashEmbeddingModel;
import dev.reboot.rag.SimpleVectorStore;
import dev.reboot.rag.TextChunker;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PdfIngestionService 单元测试。
 *
 * @author AI 助手
 * @since 2026-08-29
 */
class PdfIngestionServiceTest {

    private PdfIngestionService pdfIngestionService;
    private SimpleVectorStore vectorStore;

    @BeforeEach
    void setUp() {
        RagProperties properties = new RagProperties();
        properties.setEmbeddingDimensions(64);
        properties.setChunkSize(40);
        properties.setChunkOverlap(8);
        vectorStore = new SimpleVectorStore(new LocalHashEmbeddingModel(properties));
        RagIngestionService ragIngestionService =
                new RagIngestionService(new TextChunker(properties), vectorStore);
        pdfIngestionService = new PdfIngestionService(ragIngestionService);
    }

    @Test
    void ingest_shouldExtractTextChunkAndStore() throws IOException {
        RagIngestResult result = pdfIngestionService.ingest("device-manual.pdf",
                pdfWithText("Device temperature too high. Sensor reading abnormal. Check on site."));

        assertEquals("device-manual.pdf", result.getFileName());
        assertTrue(result.getCharacters() > 0);
        assertTrue(result.getChunks() >= 1);
        assertEquals(result.getChunks(), vectorStore.size());
    }

    @Test
    void ingest_blankPdf_shouldThrowBadRequest() throws IOException {
        BusinessException e = assertThrows(BusinessException.class,
                () -> pdfIngestionService.ingest("empty.pdf", blankPdf()));

        assertEquals(ErrorCode.BAD_REQUEST, e.getErrorCode());
    }

    private byte[] pdfWithText(String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText(text);
                content.endText();
            }
            return toBytes(document);
        }
    }

    private byte[] blankPdf() throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            return toBytes(document);
        }
    }

    private byte[] toBytes(PDDocument document) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out);
        return out.toByteArray();
    }
}
