package dev.reboot.controller;

import dev.reboot.annotation.RequireRole;
import dev.reboot.dto.ApiResponse;
import dev.reboot.dto.DeviceDTO;
import dev.reboot.dto.DeviceVO;
import dev.reboot.enums.RoleEnum;
import dev.reboot.service.DeviceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Device REST 控制器。
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
        DeviceVO vo = deviceService.getById(id);
        if (vo == null) return ApiResponse.error(404, "设备不存在");
        return ApiResponse.ok(vo);
    }

    @PostMapping
    @RequireRole({RoleEnum.OPERATOR, RoleEnum.ADMIN})
    public ApiResponse<DeviceVO> create(@RequestBody DeviceDTO dto) {
        return ApiResponse.ok("设备创建成功", deviceService.create(dto));
    }

    @PutMapping("/{id}")
    @RequireRole({RoleEnum.OPERATOR, RoleEnum.ADMIN})
    public ApiResponse<DeviceVO> update(@PathVariable Long id, @RequestBody DeviceDTO dto) {
        DeviceVO vo = deviceService.update(id, dto);
        if (vo == null) return ApiResponse.error(404, "设备不存在");
        return ApiResponse.ok("设备更新成功", vo);
    }

    @DeleteMapping("/{id}")
    @RequireRole(RoleEnum.ADMIN)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        if (deviceService.delete(id)) return ApiResponse.ok(null);
        return ApiResponse.error(404, "设备不存在");
    }
}
