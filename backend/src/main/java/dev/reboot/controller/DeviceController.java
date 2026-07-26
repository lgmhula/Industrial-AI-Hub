package dev.reboot.controller;

import dev.reboot.annotation.RequireRole;
import dev.reboot.dto.ApiResponse;
import dev.reboot.dto.DeviceDTO;
import dev.reboot.dto.DeviceVO;
import dev.reboot.enums.RoleEnum;
import dev.reboot.service.DeviceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    public ApiResponse<List<DeviceVO>> list() {
        return ApiResponse.ok(deviceService.listAll());
    }

    @GetMapping("/{id}")
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    public ApiResponse<DeviceVO> getById(@PathVariable Long id) {
        return ApiResponse.ok(deviceService.getById(id));
    }

    @PostMapping
    @RequireRole({RoleEnum.OPERATOR, RoleEnum.ADMIN})
    public ApiResponse<DeviceVO> create(@Valid @RequestBody DeviceDTO dto) {
        return ApiResponse.ok("设备创建成功", deviceService.create(dto));
    }

    @PutMapping("/{id}")
    @RequireRole({RoleEnum.OPERATOR, RoleEnum.ADMIN})
    public ApiResponse<DeviceVO> update(@PathVariable Long id, @Valid @RequestBody DeviceDTO dto) {
        return ApiResponse.ok("设备更新成功", deviceService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @RequireRole(RoleEnum.ADMIN)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        deviceService.delete(id);
        return ApiResponse.ok("设备已删除", null);
    }
}
