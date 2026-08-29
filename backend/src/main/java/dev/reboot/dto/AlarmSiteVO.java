package dev.reboot.dto;

import java.time.LocalDateTime;

/**
 * 站点活动告警视图对象 —— AI 工具 list_active_alarms_by_site 查询载体。
 *
 * <p>附带设备名称，便于 AI 直接理解告警归属（Day 68 Function Calling）。</p>
 *
 * @author AI 助手
 * @since 2026-08-29
 */
public class AlarmSiteVO {

    private Long id;
    private Long deviceId;
    private String deviceName;
    private String alarmType;
    private Integer alarmLevel;
    private String alarmMessage;
    private Integer status;
    private LocalDateTime triggeredAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public String getAlarmType() { return alarmType; }
    public void setAlarmType(String alarmType) { this.alarmType = alarmType; }
    public Integer getAlarmLevel() { return alarmLevel; }
    public void setAlarmLevel(Integer alarmLevel) { this.alarmLevel = alarmLevel; }
    public String getAlarmMessage() { return alarmMessage; }
    public void setAlarmMessage(String alarmMessage) { this.alarmMessage = alarmMessage; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getTriggeredAt() { return triggeredAt; }
    public void setTriggeredAt(LocalDateTime triggeredAt) { this.triggeredAt = triggeredAt; }
}
