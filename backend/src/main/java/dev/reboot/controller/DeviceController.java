package dev.reboot.controller;

import dev.reboot.dto.ApiResponse;
import dev.reboot.dto.DeviceDTO;
import dev.reboot.entity.Device;
import dev.reboot.service.DeviceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Device REST 控制器。
 *
 * <p>路由前缀 /api/devices，提供设备 CRUD。</p>
 *
 * @author hula0710
 * @since 2026-07-20
 */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping
    public ApiResponse<List<Device>> list() {
        return ApiResponse.ok(deviceService.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<Device> getById(@PathVariable Long id) {
        Device device = deviceService.getById(id);
        if (device == null) return ApiResponse.error(404, "设备不存在");
        return ApiResponse.ok(device);
    }

    @PostMapping
    public ApiResponse<Device> create(@RequestBody DeviceDTO dto) {
        return ApiResponse.ok("设备创建成功", deviceService.create(dto));
    }

    @PutMapping("/{id}")
    public ApiResponse<Device> update(@PathVariable Long id, @RequestBody DeviceDTO dto) {
        Device device = deviceService.update(id, dto);
        if (device == null) return ApiResponse.error(404, "设备不存在");
        return ApiResponse.ok("设备更新成功", device);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        if (deviceService.delete(id)) return ApiResponse.ok(null);
        return ApiResponse.error(404, "设备不存在");
    }
}
