package dev.reboot.service;

import dev.reboot.dto.ai.RagIngestResult;
import dev.reboot.enums.ErrorCode;
import dev.reboot.exception.BusinessException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * PDF 知识文档导入服务（Week 11 Day 74，ADR 0025）。
 *
 * <p>负责 PDF 字节 → 文本提取，再交给 {@link RagIngestionService} 完成
 * 切片 → 向量化 → 入库。</p>
 *
 * @author AI 助手
 * @since 2026-08-29
 */
@Service
public class PdfIngestionService {

    private final RagIngestionService ragIngestionService;

    public PdfIngestionService(RagIngestionService ragIngestionService) {
        this.ragIngestionService = ragIngestionService;
    }

    public RagIngestResult ingest(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "PDF 文件不能为空");
        }
        String fileName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename() : "document.pdf";
        try {
            return ingest(fileName, file.getBytes());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "PDF 文件读取失败: " + e.getMessage(), e);
        }
    }

    public RagIngestResult ingest(String fileName, byte[] bytes) {
        try {
            String text = extractText(fileName, bytes);
            if (!StringUtils.hasText(text)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "未提取到文本内容");
            }
            int chunks = ragIngestionService.ingest(fileName, text);

            RagIngestResult result = new RagIngestResult();
            result.setFileName(fileName);
            result.setCharacters(text.length());
            result.setChunks(chunks);
            return result;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文档解析失败: " + e.getMessage(), e);
        }
    }

    /** 按文件扩展名分发：txt/md 直接读取，PDF 用 PDFBox 解析。 */
    private String extractText(String fileName, byte[] bytes) throws IOException {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (lower.endsWith(".txt") || lower.endsWith(".md")) {
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
        // 默认按 PDF 解析
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        }
    }
}
