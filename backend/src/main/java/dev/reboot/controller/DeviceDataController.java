package dev.reboot.controller;

import dev.reboot.annotation.RequireRole;
import dev.reboot.dto.ApiResponse;
import dev.reboot.dto.DataReportRequest;
import dev.reboot.dto.DeviceDataStats;
import dev.reboot.entity.DeviceData;
import dev.reboot.enums.RoleEnum;
import dev.reboot.service.DeviceDataService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;


import java.time.LocalDateTime;
import java.util.List;

/**
 * DeviceData REST 控制器（P1-01：站点作用域，userId 显式传入 Service）。
 *
 * @author hula0710
 * @since 2026-07-26
 */
@RestController
@RequestMapping("/api/device-data")
@Tag(name = "04-设备数据", description = "数据上报 + 查询 + 统计")
public class DeviceDataController {

    private final DeviceDataService deviceDataService;

    public DeviceDataController(DeviceDataService deviceDataService) {
        this.deviceDataService = deviceDataService;
    }

    /** 上报设备数据（需设备站点 OPERATOR 及以上）。 */
    @PostMapping("/device/{deviceId}")
    @RequireRole({RoleEnum.OPERATOR, RoleEnum.ADMIN})
    @Operation(summary = "上报设备数据")
    public ApiResponse<DeviceData> report(@PathVariable Long deviceId,
                                          @Valid @RequestBody DataReportRequest req,
                                          HttpServletRequest request) {
        return ApiResponse.ok("数据上报成功", deviceDataService.report(deviceId, req, currentUserId(request)));
    }

    /** 按设备 ID 查询所有数据（需设备站点 VIEWER 及以上）。 */
    @GetMapping("/device/{deviceId}")
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    @Operation(summary = "按设备查询所有数据")
    public ApiResponse<List<DeviceData>> listByDevice(@PathVariable Long deviceId,
                                                      HttpServletRequest request) {
        return ApiResponse.ok(deviceDataService.listByDevice(deviceId, currentUserId(request)));
    }

    /** 按时间范围查询设备数据。 */
    @GetMapping("/device/{deviceId}/range")
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    @Operation(summary = "按时间范围 + 数据类型查询")
    public ApiResponse<List<DeviceData>> listByTimeRange(
            @PathVariable Long deviceId,
            @RequestParam(required = false) String dataType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            HttpServletRequest request) {
        return ApiResponse.ok(deviceDataService.listByTimeRange(
                deviceId, dataType, startTime, endTime, currentUserId(request)));
    }

    /** 获取设备最新一条数据。 */
    @GetMapping("/device/{deviceId}/latest")
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    @Operation(summary = "获取最新一条数据")
    public ApiResponse<DeviceData> getLatest(
            @PathVariable Long deviceId,
            @RequestParam String dataType,
            HttpServletRequest request) {
        return ApiResponse.ok(deviceDataService.getLatest(deviceId, dataType, currentUserId(request)));
    }

    /** 聚合统计：avg/min/max/count。 */
    @GetMapping("/device/{deviceId}/stats")
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    @Operation(summary = "聚合统计（avg/min/max/count）")
    public ApiResponse<DeviceDataStats> getStats(
            @PathVariable Long deviceId,
            @RequestParam String dataType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            HttpServletRequest request) {
        return ApiResponse.ok(deviceDataService.getStats(
                deviceId, dataType, startTime, endTime, currentUserId(request)));
    }

    private Long currentUserId(HttpServletRequest request) {
        Object v = request.getAttribute("userId");
        return v == null ? null : Long.valueOf(v.toString());
    }
}
