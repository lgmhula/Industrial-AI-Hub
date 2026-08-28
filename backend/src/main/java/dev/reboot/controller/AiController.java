package dev.reboot.controller;

import dev.reboot.annotation.OperationLog;
import dev.reboot.annotation.RequireRole;
import dev.reboot.dto.ApiResponse;
import dev.reboot.dto.ai.AiAlarmSummary;
import dev.reboot.dto.ai.AiChatRequest;
import dev.reboot.dto.ai.AiChatResult;
import dev.reboot.dto.ai.AiDeviceDiagnosis;
import dev.reboot.enums.RoleEnum;
import dev.reboot.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI REST 控制器 —— DeepSeek 文本补全 + 业务场景分析（Phase 4）。
 *
 * @author AI 助手
 * @since 2026-08-28
 */
@RestController
@RequestMapping("/api/ai")
@Tag(name = "08-AI 分析", description = "DeepSeek 驱动的 AI 能力（Phase 4）")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    /** 通用文本补全。 */
    @OperationLog(operationType = "CHAT", targetType = "AI", description = "AI 文本补全")
    @PostMapping("/chat")
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    @Operation(summary = "DeepSeek 文本补全")
    public ApiResponse<AiChatResult> chat(@Valid @RequestBody AiChatRequest request) {
        return ApiResponse.ok(aiService.chat(request));
    }

    /** 告警摘要生成。 */
    @OperationLog(operationType = "SUMMARY", targetType = "AI", targetIdArg = 0,
            description = "AI 生成告警摘要 {0}")
    @PostMapping("/alarms/{id}/summary")
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    @Operation(summary = "AI 生成告警摘要与处理建议")
    public ApiResponse<AiAlarmSummary> alarmSummary(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.ok(aiService.summarizeAlarm(id, currentUserId(request)));
    }

    /** 设备健康诊断建议。 */
    @OperationLog(operationType = "DIAGNOSE", targetType = "AI", targetIdArg = 0,
            description = "AI 设备健康诊断 {0}")
    @PostMapping("/devices/{id}/diagnose")
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    @Operation(summary = "AI 设备健康诊断")
    public ApiResponse<AiDeviceDiagnosis> deviceDiagnosis(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.ok(aiService.diagnoseDevice(id, currentUserId(request)));
    }

    private Long currentUserId(HttpServletRequest request) {
        Object v = request.getAttribute("userId");
        return v == null ? null : Long.valueOf(v.toString());
    }
}
