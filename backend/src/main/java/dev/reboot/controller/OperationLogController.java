package dev.reboot.controller;

import dev.reboot.annotation.RequireRole;
import dev.reboot.dto.ApiResponse;
import dev.reboot.entity.OperationLog;
import dev.reboot.enums.RoleEnum;
import dev.reboot.service.OperationLogService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;


import java.util.List;
import java.util.Map;

/**
 * OperationLog REST 控制器 —— 日志查询。
 *
 * @author hula0710
 * @since 2026-07-26
 */
@RestController
@RequestMapping("/api/operation-logs")
@Validated
@Tag(name = "06-操作日志", description = "管理员专属 — 操作日志查询")
public class OperationLogController {

    private final OperationLogService operationLogService;

    public OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    /** 分页查询操作日志。 */
    @GetMapping
    @RequireRole(RoleEnum.ADMIN)
    @Operation(summary = "分页查询操作日志")
    public ApiResponse<Map<String, Object>> listPaged(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(operationLogService.listPaged(page, size));
    }

    /** 按用户 ID 分页查询。 */
    @GetMapping("/user/{userId}")
    @RequireRole(RoleEnum.ADMIN)
    @Operation(summary = "按用户 ID 查询日志")
    public ApiResponse<Map<String, Object>> listByUserId(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(operationLogService.listByUserId(userId, page, size));
    }

    /** 查询最近 100 条（不分页，兼容旧调用）。 */
    @GetMapping("/recent")
    @RequireRole(RoleEnum.ADMIN)
    @Operation(summary = "最近日志")
    public ApiResponse<List<OperationLog>> listRecent() {
        return ApiResponse.ok(operationLogService.listRecent());
    }
}
