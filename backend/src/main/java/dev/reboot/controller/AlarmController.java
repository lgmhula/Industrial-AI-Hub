package dev.reboot.controller;

import dev.reboot.annotation.RequireRole;
import dev.reboot.dto.AlarmVO;
import dev.reboot.dto.ApiResponse;
import dev.reboot.enums.RoleEnum;
import dev.reboot.service.AlarmService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Alarm REST 控制器 —— 报警查询 + 确认/解决。
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

    /** 分页查询所有告警。 */
    @GetMapping
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    public ApiResponse<Map<String, Object>> listAllPaged(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(alarmService.listAllPaged(page, size));
    }

    /** 按设备 ID 分页查询告警。 */
    @GetMapping("/device/{deviceId}")
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    public ApiResponse<Map<String, Object>> listByDevicePaged(
            @PathVariable Long deviceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(alarmService.listByDevicePaged(deviceId, page, size));
    }

    /** 按状态分页查询告警（0=未处理, 1=已确认, 2=已解决）。 */
    @GetMapping("/status/{status}")
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    public ApiResponse<Map<String, Object>> listByStatusPaged(
            @PathVariable Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(alarmService.listByStatusPaged(status, page, size));
    }

    /** 查询所有告警（不分页，兼容旧调用）。 */
    @GetMapping("/all")
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    public ApiResponse<List<AlarmVO>> listAll() {
        return ApiResponse.ok(alarmService.listAll());
    }

    /** 确认告警。 */
    @PutMapping("/{id}/acknowledge")
    @RequireRole({RoleEnum.OPERATOR, RoleEnum.ADMIN})
    public ApiResponse<Void> acknowledge(@PathVariable Long id) {
        if (alarmService.acknowledge(id)) {
            return ApiResponse.ok("告警已确认", null);
        }
        return ApiResponse.error(404, "确认失败，告警不存在");
    }

    /** 解决告警。 */
    @PutMapping("/{id}/resolve")
    @RequireRole({RoleEnum.OPERATOR, RoleEnum.ADMIN})
    public ApiResponse<Void> resolve(@PathVariable Long id) {
        if (alarmService.resolve(id)) {
            return ApiResponse.ok("告警已解决", null);
        }
        return ApiResponse.error(404, "解决失败，告警不存在");
    }
}
