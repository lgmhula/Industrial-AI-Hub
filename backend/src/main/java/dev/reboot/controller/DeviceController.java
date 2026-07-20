package dev.reboot.controller;

import dev.reboot.common.ApiResponse;
import dev.reboot.entity.Device;
import dev.reboot.service.DeviceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Device REST 控制器 —— 三层架构：Controller → Service → Mapper。
 *
 * @author hula0710
 * @since 2026-07-19
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
        return ApiResponse.success(deviceService.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<Device> getById(@PathVariable Long id) {
        return ApiResponse.success(deviceService.findById(id));
    }

    @GetMapping("/type/{type}")
    public ApiResponse<List<Device>> getByType(@PathVariable String type) {
        return ApiResponse.success(deviceService.findByType(type));
    }

    @PostMapping
    public ApiResponse<Device> create(@RequestBody Device device) {
        return ApiResponse.success(deviceService.create(device));
    }

    @PutMapping("/{id}")
    public ApiResponse<Device> update(@PathVariable Long id, @RequestBody Device device) {
        return ApiResponse.success(deviceService.update(id, device));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        deviceService.delete(id);
        return ApiResponse.success("deleted: " + id);
    }
}
