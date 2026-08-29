package dev.reboot.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.reboot.dto.AlarmSiteVO;
import dev.reboot.entity.Alarm;
import dev.reboot.entity.Device;
import dev.reboot.entity.DeviceData;
import dev.reboot.enums.RoleEnum;
import dev.reboot.exception.BusinessException;
import dev.reboot.mapper.AlarmMapper;
import dev.reboot.mapper.DeviceDataMapper;
import dev.reboot.mapper.DeviceMapper;
import dev.reboot.service.SiteAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备状态查询工具集 —— Spring AI {@link Tool} 声明式注册（零手写 JSON Schema，ADR 0023）。
 *
 * <p>三个工具由模型按需调用，业务语义：</p>
 * <ol>
 *   <li>{@code get_device_basic}：设备基础信息；</li>
 *   <li>{@code list_device_recent_alarms}：单设备最近告警；</li>
 *   <li>{@code list_active_alarms_by_site}：站点未处理告警。</li>
 * </ol>
 *
 * <p>安全：站点资源作用域与业务模块一致（ADR 0020）——调用方（{@code DeviceStatusAgentService}）
 * 把当前用户 ID 放入 {@link ToolContext}，工具内对设备/站点做 VIEWER 级访问断言；
 * 拒绝访问或资源不存在时返回 {@code {"error": "..."}} JSON（而非抛异常），
 * 让模型能够向用户如实解释，而不是整轮失败。</p>
 *
 * @author AI 助手
 * @since 2026-08-29
 */
@Component
public class DeviceAiTools {

    private static final Logger log = LoggerFactory.getLogger(DeviceAiTools.class);

    /** ToolContext 中携带当前用户 ID 的键（与 DeviceStatusAgentService 约定）。 */
    public static final String CONTEXT_USER_ID = "userId";

    private final DeviceMapper deviceMapper;
    private final AlarmMapper alarmMapper;
    private final DeviceDataMapper deviceDataMapper;
    private final SiteAccessService siteAccessService;
    private final ObjectMapper objectMapper;

    public DeviceAiTools(DeviceMapper deviceMapper,
                         AlarmMapper alarmMapper,
                         DeviceDataMapper deviceDataMapper,
                         SiteAccessService siteAccessService,
                         ObjectMapper objectMapper) {
        this.deviceMapper = deviceMapper;
        this.alarmMapper = alarmMapper;
        this.deviceDataMapper = deviceDataMapper;
        this.siteAccessService = siteAccessService;
        this.objectMapper = objectMapper;
    }

    /** 查询设备基础信息（名称/编码/类型/状态/位置/IP/端口）。 */
    @Tool(name = "get_device_basic", description = "查询设备基础信息：名称、编码、类型、运行状态（1=在线 0=离线 2=维护中）、安装位置、IP 与端口。传入设备 ID。")
    public String getDeviceBasic(@ToolParam(description = "设备 ID") Long deviceId, ToolContext toolContext) {
        try {
            Device device = requireDevice(deviceId);
            assertViewerAccess(device.getSiteId(), toolContext);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", device.getId());
            data.put("deviceName", device.getDeviceName());
            data.put("deviceCode", device.getDeviceCode());
            data.put("deviceType", device.getDeviceType());
            data.put("status", device.getStatus());
            data.put("statusLabel", statusLabel(device.getStatus()));
            data.put("location", device.getLocation());
            data.put("ipAddress", device.getIpAddress());
            data.put("port", device.getPort());
            data.put("siteId", device.getSiteId());
            data.put("updatedAt", String.valueOf(device.getUpdatedAt()));
            return toJson(data);
        } catch (BusinessException e) {
            return errorJson(e.getMessage());
        }
    }

    /** 查询指定设备最近告警（默认 5 条，最多 20 条）。 */
    @Tool(name = "list_device_recent_alarms", description = "查询指定设备最近的告警记录（含未处理/已确认/已解决）。传入设备 ID 与可选条数 limit（1-20，默认 5）。")
    public String listDeviceRecentAlarms(
            @ToolParam(description = "设备 ID") Long deviceId,
            @ToolParam(description = "返回条数上限（1-20，默认 5）") Integer limit,
            ToolContext toolContext) {
        try {
            Device device = requireDevice(deviceId);
            assertViewerAccess(device.getSiteId(), toolContext);

            List<Alarm> alarms = alarmMapper.findByDeviceId(deviceId).stream()
                    .limit(clampLimit(limit, 5))
                    .toList();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("deviceId", deviceId);
            data.put("count", alarms.size());
            data.put("alarms", alarms.stream().map(this::alarmSummary).toList());
            return toJson(data);
        } catch (BusinessException e) {
            return errorJson(e.getMessage());
        }
    }

    /** 查询指定站点未处理（status=0）告警（默认 10 条，最多 20 条）。 */
    @Tool(name = "list_active_alarms_by_site", description = "查询指定站点下所有未处理（未确认/未解决）的告警。传入站点 ID 与可选条数 limit（1-20，默认 10）。")
    public String listActiveAlarmsBySite(
            @ToolParam(description = "站点 ID") Long siteId,
            @ToolParam(description = "返回条数上限（1-20，默认 10）") Integer limit,
            ToolContext toolContext) {
        try {
            assertViewerAccess(siteId, toolContext);

            List<AlarmSiteVO> alarms = alarmMapper.findActiveBySiteId(siteId, clampLimit(limit, 10));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("siteId", siteId);
            data.put("count", alarms.size());
            data.put("alarms", alarms.stream().map(this::siteAlarmSummary).toList());
            return toJson(data);
        } catch (BusinessException e) {
            return errorJson(e.getMessage());
        }
    }

    /** 查询指定设备最近的运行数据（默认 10 条，最多 20 条）。 */
    @Tool(name = "list_device_recent_data", description = "查询指定设备最近的运行数据（温度/压力/湿度/转速等）。传入设备 ID 与可选条数 limit（1-20，默认 10）。")
    public String listDeviceRecentData(
            @ToolParam(description = "设备 ID") Long deviceId,
            @ToolParam(description = "返回条数上限（1-20，默认 10）") Integer limit,
            ToolContext toolContext) {
        try {
            Device device = requireDevice(deviceId);
            assertViewerAccess(device.getSiteId(), toolContext);

            List<DeviceData> dataList = deviceDataMapper.findByDeviceId(deviceId).stream()
                    .limit(clampLimit(limit, 10))
                    .toList();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("deviceId", deviceId);
            data.put("count", dataList.size());
            data.put("data", dataList.stream().map(this::deviceDataSummary).toList());
            return toJson(data);
        } catch (BusinessException e) {
            return errorJson(e.getMessage());
        }
    }

    // ==================== 内部工具 ====================

    private Device requireDevice(Long deviceId) {
        if (deviceId == null) {
            throw new BusinessException(dev.reboot.enums.ErrorCode.BAD_REQUEST, "缺少设备 ID");
        }
        Device device = deviceMapper.findById(deviceId);
        if (device == null) {
            throw new BusinessException(dev.reboot.enums.ErrorCode.NOT_FOUND, "设备不存在");
        }
        return device;
    }

    private void assertViewerAccess(Long siteId, ToolContext toolContext) {
        Long userId = userIdFrom(toolContext);
        siteAccessService.assertSiteAccess(userId, siteId, RoleEnum.VIEWER);
    }

    private Long userIdFrom(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object value = toolContext.getContext().get(CONTEXT_USER_ID);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.valueOf(value.toString());
    }

    private int clampLimit(Integer limit, int defaultValue) {
        if (limit == null) {
            return defaultValue;
        }
        return Math.max(1, Math.min(limit, 20));
    }

    private Map<String, Object> alarmSummary(Alarm alarm) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", alarm.getId());
        m.put("deviceId", alarm.getDeviceId());
        m.put("alarmType", alarm.getAlarmType());
        m.put("alarmLevel", alarm.getAlarmLevel());
        m.put("alarmMessage", alarm.getAlarmMessage());
        m.put("status", alarm.getStatus());
        m.put("triggeredAt", String.valueOf(alarm.getTriggeredAt()));
        return m;
    }

    private Map<String, Object> siteAlarmSummary(AlarmSiteVO alarm) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", alarm.getId());
        m.put("deviceId", alarm.getDeviceId());
        m.put("deviceName", alarm.getDeviceName());
        m.put("alarmType", alarm.getAlarmType());
        m.put("alarmLevel", alarm.getAlarmLevel());
        m.put("alarmMessage", alarm.getAlarmMessage());
        m.put("triggeredAt", String.valueOf(alarm.getTriggeredAt()));
        return m;
    }

    private Map<String, Object> deviceDataSummary(DeviceData deviceData) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", deviceData.getId());
        m.put("dataType", deviceData.getDataType());
        m.put("value", deviceData.getDataValue());
        m.put("unit", deviceData.getUnit());
        m.put("recordedAt", String.valueOf(deviceData.getRecordedAt()));
        return m;
    }

    private String statusLabel(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 1 -> "在线";
            case 0 -> "离线";
            case 2 -> "维护中";
            default -> "未知";
        };
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("工具结果 JSON 序列化失败: {}", e.getMessage());
            return "{\"error\":\"工具结果序列化失败\"}";
        }
    }

    private String errorJson(String message) {
        log.warn("AI 工具拒绝/失败: {}", message);
        Map<String, String> m = new LinkedHashMap<>();
        m.put("error", message);
        return toJson(m);
    }
}
