package dev.reboot.controller;

import dev.reboot.annotation.RequireRole;
import dev.reboot.dto.ApiResponse;
import dev.reboot.entity.OperationLog;
import dev.reboot.enums.RoleEnum;
import dev.reboot.service.OperationLogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * OperationLog REST 控制器。
 *
 * @author hula0710
 * @since 2026-07-26
 */
@RestController
@RequestMapping("/api/operation-logs")
public class OperationLogController {

    private final OperationLogService operationLogService;

    public OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    /** 查询最近操作日志（仅 ADMIN）。 */
    @GetMapping
    @RequireRole(RoleEnum.ADMIN)
    public ApiResponse<List<OperationLog>> listRecent() {
        return ApiResponse.ok(operationLogService.listRecent());
    }
}
