package dev.reboot.dto;

import dev.reboot.entity.Alarm;

import java.time.LocalDateTime;

/**
 * 告警视图对象。
 *
 * @author hula0710
 * @since 2026-07-25
 */
public class AlarmVO {

    private Long id;
    private Long deviceId;
    private String alarmType;
    private Integer alarmLevel;
    private String alarmMessage;
    private Integer status;
    private LocalDateTime triggeredAt;
    private LocalDateTime resolvedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
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
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    public static AlarmVO from(Alarm alarm) {
        AlarmVO vo = new AlarmVO();
        vo.setId(alarm.getId());
        vo.setDeviceId(alarm.getDeviceId());
        vo.setAlarmType(alarm.getAlarmType());
        vo.setAlarmLevel(alarm.getAlarmLevel());
        vo.setAlarmMessage(alarm.getAlarmMessage());
        vo.setStatus(alarm.getStatus());
        vo.setTriggeredAt(alarm.getTriggeredAt());
        vo.setResolvedAt(alarm.getResolvedAt());
        return vo;
    }
}
