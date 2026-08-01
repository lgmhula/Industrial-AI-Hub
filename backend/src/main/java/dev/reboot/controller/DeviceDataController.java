package dev.reboot.controller;

import dev.reboot.annotation.RequireRole;
import dev.reboot.dto.ApiResponse;
import dev.reboot.dto.DataReportRequest;
import dev.reboot.dto.DeviceDataStats;
import dev.reboot.entity.DeviceData;
import dev.reboot.enums.RoleEnum;
import dev.reboot.service.DeviceDataService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;


import java.time.LocalDateTime;
import java.util.List;

/**
 * DeviceData REST 控制器。
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

    /** 上报设备数据。 */
    @PostMapping("/device/{deviceId}")
    @RequireRole({RoleEnum.OPERATOR, RoleEnum.ADMIN})
    @Operation(summary = "上报设备数据")
    public ApiResponse<DeviceData> report(@PathVariable Long deviceId,
                                          @Valid @RequestBody DataReportRequest req) {
        return ApiResponse.ok("数据上报成功", deviceDataService.report(deviceId, req));
    }

    /** 按设备 ID 查询所有数据。 */
    @GetMapping("/device/{deviceId}")
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    @Operation(summary = "按设备查询所有数据")
    public ApiResponse<List<DeviceData>> listByDevice(@PathVariable Long deviceId) {
        return ApiResponse.ok(deviceDataService.listByDevice(deviceId));
    }

    /**
     * 按时间范围查询设备数据。
     *
     * @param startTime 开始时间 (ISO-8601, 可选)
     * @param endTime   结束时间 (ISO-8601, 可选)
     * @param dataType  数据类型（可选）
     */
    @GetMapping("/device/{deviceId}/range")
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    @Operation(summary = "按时间范围 + 数据类型查询")
    public ApiResponse<List<DeviceData>> listByTimeRange(
            @PathVariable Long deviceId,
            @RequestParam(required = false) String dataType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return ApiResponse.ok(deviceDataService.listByTimeRange(deviceId, dataType, startTime, endTime));
    }

    /** 获取设备最新一条数据。 */
    @GetMapping("/device/{deviceId}/latest")
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    @Operation(summary = "获取最新一条数据")
    public ApiResponse<DeviceData> getLatest(
            @PathVariable Long deviceId,
            @RequestParam String dataType) {
        return ApiResponse.ok(deviceDataService.getLatest(deviceId, dataType));
    }

    /**
     * 聚合统计：avg/min/max/count。
     *
     * @param dataType 数据类型（必选）
     */
    @GetMapping("/device/{deviceId}/stats")
    @RequireRole({RoleEnum.VIEWER, RoleEnum.OPERATOR, RoleEnum.ADMIN})
    @Operation(summary = "聚合统计（avg/min/max/count）")
    public ApiResponse<DeviceDataStats> getStats(
            @PathVariable Long deviceId,
            @RequestParam String dataType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return ApiResponse.ok(deviceDataService.getStats(deviceId, dataType, startTime, endTime));
    }
}
