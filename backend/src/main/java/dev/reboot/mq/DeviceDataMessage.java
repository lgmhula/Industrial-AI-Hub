package dev.reboot.mq;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备数据消息 DTO — 通过 Fanout Exchange 广播到多个下游系统。
 *
 * @author hula0710
 * @since 2026-08-09 (Day 55)
 */
public class DeviceDataMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long deviceId;
    private String dataType;
    private BigDecimal dataValue;
    private String unit;
    private LocalDateTime recordedAt;

    public DeviceDataMessage() {}

    public DeviceDataMessage(Long deviceId, String dataType, BigDecimal dataValue,
                              String unit, LocalDateTime recordedAt) {
        this.deviceId = deviceId;
        this.dataType = dataType;
        this.dataValue = dataValue;
        this.unit = unit;
        this.recordedAt = recordedAt;
    }

    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public BigDecimal getDataValue() { return dataValue; }
    public void setDataValue(BigDecimal dataValue) { this.dataValue = dataValue; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }

    @Override
    public String toString() {
        return "DeviceData{deviceId=" + deviceId + ", type=" + dataType
                + ", value=" + dataValue + unit + "}";
    }
}
