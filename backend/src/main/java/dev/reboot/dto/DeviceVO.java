package dev.reboot.dto;

import dev.reboot.entity.Device;

import java.time.LocalDateTime;

/**
 * 设备视图对象 —— 不含 isDeleted 等内部标记字段。
 *
 * @author hula0710
 * @since 2026-07-24
 */
public class DeviceVO {

    private Long id;
    private Long siteId;
    private String deviceName;
    private String deviceCode;
    private String deviceType;
    private Integer status;
    private String ipAddress;
    private Integer port;
    private String location;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSiteId() { return siteId; }
    public void setSiteId(Long siteId) { this.siteId = siteId; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    /** 从 Device entity 构造（不含 isDeleted 字段）。 */
    public static DeviceVO from(Device device) {
        DeviceVO vo = new DeviceVO();
        vo.setId(device.getId());
        vo.setSiteId(device.getSiteId());
        vo.setDeviceName(device.getDeviceName());
        vo.setDeviceCode(device.getDeviceCode());
        vo.setDeviceType(device.getDeviceType());
        vo.setStatus(device.getStatus());
        vo.setIpAddress(device.getIpAddress());
        vo.setPort(device.getPort());
        vo.setLocation(device.getLocation());
        vo.setCreatedAt(device.getCreatedAt());
        vo.setUpdatedAt(device.getUpdatedAt());
        return vo;
    }
}
