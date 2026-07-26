package dev.reboot.controller;

import dev.reboot.annotation.RequireRole;
import dev.reboot.dto.AlarmVO;
import dev.reboot.dto.ApiResponse;
import dev.reboot.enums.RoleEnum;
import dev.reboot.service.AlarmService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Alarm REST 控制器。
 *
 * @author hula0710
 * @since 2026-07-26
 */
@RestController
@RequestMapping("/api/alarms")
public class AlarmController {

    private final AlarmService alarmService;

    public AlarmController(AlarmService alarmService) {
        this.alarmService = alarmService;
    }

    /** 查询所有告警。 */
    @GetMapping
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    public ApiResponse<List<AlarmVO>> listAll() {
        return ApiResponse.ok(alarmService.listAll());
    }

    /** 按设备 ID 查询告警。 */
    @GetMapping("/device/{deviceId}")
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    public ApiResponse<List<AlarmVO>> listByDevice(@PathVariable Long deviceId) {
        return ApiResponse.ok(alarmService.listByDevice(deviceId));
    }

    /** 按状态查询告警（0=未处理, 1=已确认, 2=已解决）。 */
    @GetMapping("/status/{status}")
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    public ApiResponse<List<AlarmVO>> listByStatus(@PathVariable Integer status) {
        return ApiResponse.ok(alarmService.listByStatus(status));
    }

    /** 确认告警。 */
    @PutMapping("/{id}/acknowledge")
    @RequireRole({RoleEnum.OPERATOR, RoleEnum.ADMIN})
    public ApiResponse<Void> acknowledge(@PathVariable Long id) {
        alarmService.acknowledge(id);
        return ApiResponse.ok("告警已确认", null);
    }

    /** 解决告警。 */
    @PutMapping("/{id}/resolve")
    @RequireRole({RoleEnum.OPERATOR, RoleEnum.ADMIN})
    public ApiResponse<Void> resolve(@PathVariable Long id) {
        alarmService.resolve(id);
        return ApiResponse.ok("告警已解决", null);
    }
}
