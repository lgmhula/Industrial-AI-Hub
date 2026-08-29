package dev.reboot.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.reboot.entity.Alarm;
import dev.reboot.entity.Device;
import dev.reboot.entity.DeviceData;
import dev.reboot.enums.ErrorCode;
import dev.reboot.exception.BusinessException;
import dev.reboot.mapper.AlarmMapper;
import dev.reboot.mapper.DeviceDataMapper;
import dev.reboot.mapper.DeviceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 只读设备查询工具（Day 80，ADR 0027）。
 *
 * <p>与内部 Agent 工具（{@link dev.reboot.tool.DeviceAiTools}）分离：MCP 端点不携带
 * 用户身份（MCP 1.0 规范未定义鉴权头，Spring AI 传输也未透传 HTTP Header），
 * 因此这里只暴露只读查询，且仅通过 {@link McpToolConfig} 显式注册到
 * {@code ToolCallbackProvider}，不把内部业务工具自动暴露给外部客户端。</p>
 *
 * <p>授权边界：MCP Server 视为内部可信通道（内网/本机），Day 82 MCP 客户端集成时
 * 再引入传输层鉴权与 RBAC；当前工具无站点作用域断言。</p>
 *
 * @author AI 助手
 * @since 2026-08-29
 */
@Component
public class McpDeviceTools {

    private static final Logger log = LoggerFactory.getLogger(McpDeviceTools.class);

    private static final int MAX_LIST_LIMIT = 50;

    private final DeviceMapper deviceMapper;
    private final DeviceDataMapper deviceDataMapper;
    private final AlarmMapper alarmMapper;
    private final ObjectMapper objectMapper;

    public McpDeviceTools(DeviceMapper deviceMapper,
                          DeviceDataMapper deviceDataMapper,
                          AlarmMapper alarmMapper,
                          ObjectMapper objectMapper) {
        this.deviceMapper = deviceMapper;
        this.deviceDataMapper = deviceDataMapper;
        this.alarmMapper = alarmMapper;
        this.objectMapper = objectMapper;
    }

    /** 列出全部设备（只读，默认 20 条，最多 50 条）。 */
    @Tool(name = "mcp_list_devices", description = "列出设备（名称/编码/类型/状态/站点）。可选 limit（1-50，默认 20）。")
    public String listDevices(@ToolParam(description = "返回条数上限（1-50，默认 20）") Integer limit) {
        try {
            List<Device> devices = deviceMapper.findAll(null).stream()
                    .limit(clampLimit(limit, 20))
                    .toList();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("count", devices.size());
            data.put("devices", devices.stream().map(this::deviceSummary).toList());
            return toJson(data);
        } catch (BusinessException e) {
            return errorJson(e.getMessage());
        }
    }

    /** 查询单台设备基础信息。 */
    @Tool(name = "mcp_get_device_basic", description = "查询设备基础信息：名称、编码、类型、状态（1=在线 0=离线 2=维护中）、位置、IP、端口、站点。")
    public String getDeviceBasic(@ToolParam(description = "设备 ID") Long deviceId) {
        try {
            Device device = requireDevice(deviceId);
            return toJson(deviceSummary(device));
        } catch (BusinessException e) {
            return errorJson(e.getMessage());
        }
    }

    /** 查询设备最近运行数据（默认 10 条，最多 50 条）。 */
    @Tool(name = "mcp_list_device_recent_data",
            description = "查询指定设备最近的运行数据（温度/压力/湿度/转速等）。可选 limit（1-50，默认 10）。")
    public String listDeviceRecentData(
            @ToolParam(description = "设备 ID") Long deviceId,
            @ToolParam(description = "返回条数上限（1-50，默认 10）") Integer limit) {
        try {
            requireDevice(deviceId);
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

    /** 查询设备最近告警（默认 5 条，最多 50 条）。 */
    @Tool(name = "mcp_list_device_recent_alarms",
            description = "查询指定设备最近的告警记录（含未处理/已确认/已解决）。可选 limit（1-50，默认 5）。")
    public String listDeviceRecentAlarms(
            @ToolParam(description = "设备 ID") Long deviceId,
            @ToolParam(description = "返回条数上限（1-50，默认 5）") Integer limit) {
        try {
            requireDevice(deviceId);
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

    private Device requireDevice(Long deviceId) {
        if (deviceId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少设备 ID");
        }
        Device device = deviceMapper.findById(deviceId);
        if (device == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "设备不存在");
        }
        return device;
    }

    private int clampLimit(Integer limit, int defaultValue) {
        if (limit == null) {
            return defaultValue;
        }
        return Math.max(1, Math.min(limit, MAX_LIST_LIMIT));
    }

    private Map<String, Object> deviceSummary(Device device) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", device.getId());
        m.put("deviceName", device.getDeviceName());
        m.put("deviceCode", device.getDeviceCode());
        m.put("deviceType", device.getDeviceType());
        m.put("status", device.getStatus());
        m.put("statusLabel", statusLabel(device.getStatus()));
        m.put("location", device.getLocation());
        m.put("ipAddress", device.getIpAddress());
        m.put("port", device.getPort());
        m.put("siteId", device.getSiteId());
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
            log.error("MCP 工具结果 JSON 序列化失败: {}", e.getMessage());
            return "{\"error\":\"工具结果序列化失败\"}";
        }
    }

    private String errorJson(String message) {
        log.warn("MCP 工具拒绝/失败: {}", message);
        Map<String, String> m = new LinkedHashMap<>();
        m.put("error", message);
        return toJson(m);
    }
}
