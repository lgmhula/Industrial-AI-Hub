package dev.reboot.controller;

import dev.reboot.annotation.OperationLog;
import dev.reboot.annotation.RequireRole;
import dev.reboot.dto.ApiResponse;
import dev.reboot.dto.ai.AiAlarmSummary;
import dev.reboot.dto.ai.AiChatRequest;
import dev.reboot.dto.ai.AiChatResult;
import dev.reboot.dto.ai.AiDeviceDiagnosis;
import dev.reboot.dto.ai.AiInspectionReportResult;
import dev.reboot.dto.ai.AiDeviceStatusRequest;
import dev.reboot.dto.ai.AiDeviceStatusResult;
import dev.reboot.enums.RoleEnum;
import dev.reboot.service.AiService;
import dev.reboot.service.DeviceAnalysisAgentService;
import dev.reboot.service.DeviceStatusAgentService;
import dev.reboot.service.McpInspectionAgentService;
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
    private final DeviceStatusAgentService deviceStatusAgentService;
    private final DeviceAnalysisAgentService deviceAnalysisAgentService;
    private final McpInspectionAgentService mcpInspectionAgentService;

    public AiController(AiService aiService,
                        DeviceStatusAgentService deviceStatusAgentService,
                        DeviceAnalysisAgentService deviceAnalysisAgentService,
                        McpInspectionAgentService mcpInspectionAgentService) {
        this.aiService = aiService;
        this.deviceStatusAgentService = deviceStatusAgentService;
        this.deviceAnalysisAgentService = deviceAnalysisAgentService;
        this.mcpInspectionAgentService = mcpInspectionAgentService;
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

    /**
     * AI 设备状态问答（Function Calling：模型自动调用项目工具查询实时数据）。
     *
     * <p>{@code {ret}} 占位符由 {@link dev.reboot.aop.OperationLogAspect} 替换为结果摘要
     * （设备 ID / 工具轮次 / 调用数 / 是否参考实时数据 / 是否截断），满足 FUNCTION_CALL 审计。</p>
     */
    @OperationLog(operationType = "FUNCTION_CALL", targetType = "AI",
            description = "AI 设备状态问答（工具调用） {ret}")
    @PostMapping("/agents/device-status")
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    @Operation(summary = "AI 设备状态问答（Function Calling）")
    public ApiResponse<AiDeviceStatusResult> deviceStatus(@Valid @RequestBody AiDeviceStatusRequest request,
                                                          HttpServletRequest http) {
        return ApiResponse.ok(deviceStatusAgentService.answer(request, currentUserId(http)));
    }

    /** 设备分析多步 Agent：先查设备 → 再查数据 → 再分析。 */
    @OperationLog(operationType = "FUNCTION_CALL", targetType = "AI",
            description = "AI 设备分析 Agent（多步推理） {ret}")
    @PostMapping("/agents/device-analysis")
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    @Operation(summary = "AI 设备分析 Agent（多步推理）")
    public ApiResponse<AiDeviceStatusResult> deviceAnalysis(@Valid @RequestBody AiDeviceStatusRequest request,
                                                            HttpServletRequest http) {
        return ApiResponse.ok(deviceAnalysisAgentService.analyze(request, currentUserId(http)));
    }

    /** AI 设备巡检日报（Agent + MCP 联调）：Agent 通过 MCP 客户端只读工具自动巡检并生成日报。 */
    @OperationLog(operationType = "INSPECTION", targetType = "MCP",
            description = "AI 设备巡检日报（MCP 工具调用） {ret}")
    @PostMapping("/agents/inspection-report")
    @RequireRole(RoleEnum.ADMIN)
    @Operation(summary = "AI 设备巡检日报（Agent + MCP）")
    public ApiResponse<AiInspectionReportResult> inspectionReport() {
        return ApiResponse.ok(mcpInspectionAgentService.generate());
    }

    private Long currentUserId(HttpServletRequest request) {
        Object v = request.getAttribute("userId");
        return v == null ? null : Long.valueOf(v.toString());
    }
}
