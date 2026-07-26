package dev.reboot.controller;

import dev.reboot.annotation.RequireRole;
import dev.reboot.dto.ApiResponse;
import dev.reboot.entity.DeviceData;
import dev.reboot.enums.RoleEnum;
import dev.reboot.service.DeviceDataService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * DeviceData REST 控制器。
 *
 * @author hula0710
 * @since 2026-07-26
 */
@RestController
@RequestMapping("/api/device-data")
public class DeviceDataController {

    private final DeviceDataService deviceDataService;

    public DeviceDataController(DeviceDataService deviceDataService) {
        this.deviceDataService = deviceDataService;
    }

    /** 按设备 ID 查询所有数据记录。 */
    @GetMapping("/device/{deviceId}")
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    public ApiResponse<List<DeviceData>> listByDevice(@PathVariable Long deviceId) {
        return ApiResponse.ok(deviceDataService.listByDevice(deviceId));
    }

    /** 获取设备最新一条指定类型的数据。 */
    @GetMapping("/device/{deviceId}/latest")
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    public ApiResponse<DeviceData> getLatest(
            @PathVariable Long deviceId,
            @RequestParam String dataType) {
        return ApiResponse.ok(deviceDataService.getLatest(deviceId, dataType));
    }
}
