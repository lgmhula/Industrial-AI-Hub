package dev.reboot.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备数据实体 —— 对应 reboot.device_data 表。
 *
 * @author hula0710
 * @since 2026-07-24
 */
public class DeviceData {

    private Long id;
    private Long deviceId;
    private String dataType;
    private BigDecimal dataValue;
    private String unit;
    private LocalDateTime recordedAt;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return String.format("DeviceData{id=%d, deviceId=%d, dataType='%s', value=%s%s}",
                id, deviceId, dataType, dataValue, unit);
    }
}
