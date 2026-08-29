package dev.reboot.controller;

import dev.reboot.annotation.OperationLog;
import dev.reboot.annotation.RequireRole;
import dev.reboot.dto.ApiResponse;
import dev.reboot.dto.ai.RagAnswerResult;
import dev.reboot.dto.ai.RagAskRequest;
import dev.reboot.dto.ai.RagIngestResult;
import dev.reboot.enums.RoleEnum;
import dev.reboot.service.AiService;
import dev.reboot.service.PdfIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final AiService aiService;

    public RagController(PdfIngestionService pdfIngestionService, AiService aiService) {
        this.pdfIngestionService = pdfIngestionService;
        this.aiService = aiService;
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

    /** 根据知识库片段回答运维问题。 */
    @OperationLog(operationType = "CHAT", targetType = "KNOWLEDGE",
            description = "RAG 知识问答 {ret}")
    @PostMapping("/ask")
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    @Operation(summary = "根据知识库回答设备运维问题")
    public ApiResponse<RagAnswerResult> ask(@Valid @RequestBody RagAskRequest request) {
        return ApiResponse.ok(aiService.answerWithRag(request.getQuestion()));
    }
}
