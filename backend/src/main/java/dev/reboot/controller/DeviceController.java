package dev.reboot.controller;

import dev.reboot.annotation.RequireRole;
import dev.reboot.dto.ApiResponse;
import dev.reboot.dto.DeviceDTO;
import dev.reboot.entity.Device;
import dev.reboot.enums.RoleEnum;
import dev.reboot.service.DeviceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Device REST 控制器 —— 工业 AI Hub 设备管理 API。
 *
 * <h3>权限</h3>
 * <ul>
 *   <li>GET    — VIEWER 及以上</li>
 *   <li>POST   — OPERATOR 及以上</li>
 *   <li>PUT    — OPERATOR 及以上</li>
 *   <li>DELETE — ADMIN 专属</li>
 * </ul>
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
    public ApiResponse<List<Device>> list() {
        return ApiResponse.ok(deviceService.listAll());
    }

    @GetMapping("/{id}")
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    public ApiResponse<Device> getById(@PathVariable Long id) {
        Device device = deviceService.getById(id);
        if (device == null) return ApiResponse.error(404, "设备不存在");
        return ApiResponse.ok(device);
    }

    @PostMapping
    @RequireRole({RoleEnum.OPERATOR, RoleEnum.ADMIN})
    public ApiResponse<Device> create(@RequestBody DeviceDTO dto) {
        return ApiResponse.ok("设备创建成功", deviceService.create(dto));
    }

    @PutMapping("/{id}")
    @RequireRole({RoleEnum.OPERATOR, RoleEnum.ADMIN})
    public ApiResponse<Device> update(@PathVariable Long id, @RequestBody DeviceDTO dto) {
        Device device = deviceService.update(id, dto);
        if (device == null) return ApiResponse.error(404, "设备不存在");
        return ApiResponse.ok("设备更新成功", device);
    }

    @DeleteMapping("/{id}")
    @RequireRole(RoleEnum.ADMIN)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        if (deviceService.delete(id)) return ApiResponse.ok(null);
        return ApiResponse.error(404, "设备不存在");
    }
}
