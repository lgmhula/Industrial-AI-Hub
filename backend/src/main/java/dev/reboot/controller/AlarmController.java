package dev.reboot.controller;

import dev.reboot.annotation.OperationLog;
import dev.reboot.annotation.RequireRole;
import dev.reboot.dto.AlarmVO;
import dev.reboot.dto.ApiResponse;
import dev.reboot.enums.ErrorCode;
import dev.reboot.enums.RoleEnum;
import dev.reboot.service.AlarmService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;


import com.github.pagehelper.PageInfo;
import java.util.List;

/**
 * Alarm REST 控制器 —— 报警查询 + 确认/解决。
 *
 * @author hula0710
 * @since 2026-07-26
 */
@RestController
@RequestMapping("/api/alarms")
@Validated
@Tag(name = "05-报警管理", description = "报警查询 + 确认/解决")
public class AlarmController {

    private final AlarmService alarmService;

    public AlarmController(AlarmService alarmService) {
        this.alarmService = alarmService;
    }

    /** 分页查询所有告警。 */
    @GetMapping
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    @Operation(summary = "分页查询所有告警")
    public ApiResponse<PageInfo<AlarmVO>> listAllPaged(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(alarmService.listAllPaged(page, size));
    }

    /** 按设备 ID 分页查询告警。 */
    @GetMapping("/device/{deviceId}")
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    @Operation(summary = "按设备 ID 分页查询告警")
    public ApiResponse<PageInfo<AlarmVO>> listByDevicePaged(
            @PathVariable Long deviceId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(alarmService.listByDevicePaged(deviceId, page, size));
    }

    /** 按状态分页查询告警（0=未处理, 1=已确认, 2=已解决）。 */
    @GetMapping("/status/{status}")
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    @Operation(summary = "按状态分页查询告警")
    public ApiResponse<PageInfo<AlarmVO>> listByStatusPaged(
            @PathVariable Integer status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(alarmService.listByStatusPaged(status, page, size));
    }


    /** 确认告警。 */
    @OperationLog(operationType = "ACKNOWLEDGE", targetType = "ALARM", description = "确认告警 {0}")
    @PutMapping("/{id}/acknowledge")
    @RequireRole({RoleEnum.OPERATOR, RoleEnum.ADMIN})
    @Operation(summary = "确认告警")
    public ApiResponse<Void> acknowledge(@PathVariable Long id) {
        if (alarmService.acknowledge(id)) {
            return ApiResponse.ok("告警已确认", null);
        }
        return ApiResponse.error(ErrorCode.NOT_FOUND.getCode(), "确认失败，告警不存在");
    }

    /** 解决告警。 */
    @OperationLog(operationType = "RESOLVE", targetType = "ALARM", description = "解决告警 {0}")
    @PutMapping("/{id}/resolve")
    @RequireRole({RoleEnum.OPERATOR, RoleEnum.ADMIN})
    @Operation(summary = "解决告警")
    public ApiResponse<Void> resolve(@PathVariable Long id) {
        if (alarmService.resolve(id)) {
            return ApiResponse.ok("告警已解决", null);
        }
        return ApiResponse.error(ErrorCode.NOT_FOUND.getCode(), "解决失败，告警不存在");
    }
}
