package dev.reboot.controller;

import dev.reboot.annotation.OperationLog;
import dev.reboot.annotation.RequireRole;
import dev.reboot.dto.ApiResponse;
import dev.reboot.dto.ai.RagIngestResult;
import dev.reboot.enums.RoleEnum;
import dev.reboot.service.PdfIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * RAG 知识库控制器（Week 11）。
 *
 * @author AI 助手
 * @since 2026-08-29
 */
@RestController
@RequestMapping("/api/rag")
@Tag(name = "09-RAG 知识库", description = "私有知识文档导入与检索（Week 11）")
public class RagController {

    private final PdfIngestionService pdfIngestionService;

    public RagController(PdfIngestionService pdfIngestionService) {
        this.pdfIngestionService = pdfIngestionService;
    }

    /** 上传 PDF 知识文档并入库。 */
    @OperationLog(operationType = "INGEST", targetType = "KNOWLEDGE",
            description = "上传 RAG 知识文档 {ret}")
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireRole(RoleEnum.ADMIN)
    @Operation(summary = "上传 PDF 知识文档并入库")
    public ApiResponse<RagIngestResult> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(pdfIngestionService.ingest(file));
    }
}
