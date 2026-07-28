package dev.reboot.controller;

import com.github.pagehelper.PageInfo;
import dev.reboot.annotation.OperationLog;
import dev.reboot.annotation.RequireRole;
import dev.reboot.dto.ApiResponse;
import dev.reboot.dto.DeviceDTO;
import dev.reboot.dto.DeviceVO;
import dev.reboot.enums.RoleEnum;
import dev.reboot.service.DeviceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * Device REST 控制器。
 *
 * <p>业务异常统一由 {@link dev.reboot.exception.GlobalExceptionHandler} 处理。</p>
 *
 * @author hula0710
 * @since 2026-07-24
 */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    /**
     * 分页搜索设备列表。
     *
     * <p>支持关键字模糊搜索（设备名称/编码）、设备类型筛选、状态筛选。</p>
     */
    @GetMapping
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    public ApiResponse<PageInfo<DeviceVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String deviceType,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(deviceService.searchDevices(keyword, deviceType, status, page, size));
    }

    @GetMapping("/{id}")
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    public ApiResponse<DeviceVO> getById(@PathVariable Long id) {
        return ApiResponse.ok(deviceService.getById(id));
    }

    @OperationLog(operationType = "CREATE", targetType = "DEVICE", description = "创建设备")
    @PostMapping
    @RequireRole({RoleEnum.OPERATOR, RoleEnum.ADMIN})
    public ApiResponse<DeviceVO> create(@Valid @RequestBody DeviceDTO dto) {
        return ApiResponse.ok("设备创建成功", deviceService.create(dto));
    }

    @OperationLog(operationType = "UPDATE", targetType = "DEVICE", description = "更新设备 {0}")
    @PutMapping("/{id}")
    @RequireRole({RoleEnum.OPERATOR, RoleEnum.ADMIN})
    public ApiResponse<DeviceVO> update(@PathVariable Long id, @Valid @RequestBody DeviceDTO dto) {
        return ApiResponse.ok("设备更新成功", deviceService.update(id, dto));
    }

    @OperationLog(operationType = "DELETE", targetType = "DEVICE", description = "删除设备 {0}")
    @DeleteMapping("/{id}")
    @RequireRole(RoleEnum.ADMIN)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        deviceService.delete(id);
        return ApiResponse.ok("设备已删除", null);
    }
}
