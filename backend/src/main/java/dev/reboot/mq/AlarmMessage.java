package dev.reboot.mq;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 报警消息 DTO — 通过 RabbitMQ 在 Producer/Consumer 间传递。
 *
 * <p>实现 {@link Serializable} 以支持 Jackson JSON 序列化。</p>
 *
 * @author hula0710
 * @since 2026-08-07 (Day 51)
 */
public class AlarmMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long deviceId;
    private String alarmType;
    private Integer alarmLevel;
    private String alarmMessage;
    private BigDecimal dataValue;
    private LocalDateTime triggeredAt;

    public AlarmMessage() {}

    public AlarmMessage(Long deviceId, String alarmType, Integer alarmLevel,
                        String alarmMessage, BigDecimal dataValue, LocalDateTime triggeredAt) {
        this.deviceId = deviceId;
        this.alarmType = alarmType;
        this.alarmLevel = alarmLevel;
        this.alarmMessage = alarmMessage;
        this.dataValue = dataValue;
        this.triggeredAt = triggeredAt;
    }

    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public String getAlarmType() { return alarmType; }
    public void setAlarmType(String alarmType) { this.alarmType = alarmType; }
    public Integer getAlarmLevel() { return alarmLevel; }
    public void setAlarmLevel(Integer alarmLevel) { this.alarmLevel = alarmLevel; }
    public String getAlarmMessage() { return alarmMessage; }
    public void setAlarmMessage(String alarmMessage) { this.alarmMessage = alarmMessage; }
    public BigDecimal getDataValue() { return dataValue; }
    public void setDataValue(BigDecimal dataValue) { this.dataValue = dataValue; }
    public LocalDateTime getTriggeredAt() { return triggeredAt; }
    public void setTriggeredAt(LocalDateTime triggeredAt) { this.triggeredAt = triggeredAt; }

    @Override
    public String toString() {
        return "AlarmMessage{deviceId=" + deviceId + ", type=" + alarmType
                + ", level=" + alarmLevel + ", msg=" + alarmMessage + "}";
    }
}
