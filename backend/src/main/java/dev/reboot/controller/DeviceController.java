package dev.reboot.controller;

import com.github.pagehelper.PageInfo;
import dev.reboot.annotation.OperationLog;
import dev.reboot.annotation.RequireRole;
import dev.reboot.dto.ApiResponse;
import dev.reboot.dto.DeviceDTO;
import dev.reboot.dto.DeviceVO;
import dev.reboot.enums.RoleEnum;
import dev.reboot.service.DeviceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;


/**
 * Device REST 控制器。
 *
 * <p>业务异常统一由 {@link dev.reboot.exception.GlobalExceptionHandler} 处理。
 * P1-01：当前登录用户 ID（来自 JWT）显式传入 Service，由 Service 做站点资源作用域校验。</p>
 *
 * @author hula0710
 * @since 2026-07-24
 */
@RestController
@RequestMapping("/api/devices")
@Tag(name = "03-设备管理", description = "设备 CRUD + 分页搜索")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    /**
     * 分页搜索设备列表（当前用户可访问站点范围内）。
     */
    @GetMapping
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    @Operation(summary = "分页搜索设备（支持关键字/类型/状态）")
    public ApiResponse<PageInfo<DeviceVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String deviceType,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            HttpServletRequest request) {
        return ApiResponse.ok(deviceService.searchDevices(
                keyword, deviceType, status, page, size, currentUserId(request)));
    }

    @GetMapping("/{id}")
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    @Operation(summary = "按 ID 查询设备")
    public ApiResponse<DeviceVO> getById(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.ok(deviceService.getById(id, currentUserId(request)));
    }

    @OperationLog(operationType = "CREATE", targetType = "DEVICE", description = "创建设备")
    @PostMapping
    @RequireRole({RoleEnum.OPERATOR, RoleEnum.ADMIN})
    @Operation(summary = "创建设备")
    public ApiResponse<DeviceVO> create(@Valid @RequestBody DeviceDTO dto, HttpServletRequest request) {
        return ApiResponse.ok("设备创建成功", deviceService.create(dto, currentUserId(request)));
    }

    @OperationLog(operationType = "UPDATE", targetType = "DEVICE", description = "更新设备 {0}")
    @PutMapping("/{id}")
    @RequireRole({RoleEnum.OPERATOR, RoleEnum.ADMIN})
    @Operation(summary = "更新设备")
    public ApiResponse<DeviceVO> update(@PathVariable Long id, @Valid @RequestBody DeviceDTO dto,
                                        HttpServletRequest request) {
        return ApiResponse.ok("设备更新成功", deviceService.update(id, dto, currentUserId(request)));
    }

    @OperationLog(operationType = "DELETE", targetType = "DEVICE", description = "删除设备 {0}")
    @DeleteMapping("/{id}")
    @RequireRole(RoleEnum.ADMIN)
    @Operation(summary = "逻辑删除设备")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        deviceService.delete(id);
        return ApiResponse.ok("设备已删除", null);
    }

    private Long currentUserId(HttpServletRequest request) {
        Object v = request.getAttribute("userId");
        return v == null ? null : Long.valueOf(v.toString());
    }
}
