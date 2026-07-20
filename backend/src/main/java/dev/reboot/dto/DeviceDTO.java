package dev.reboot.dto;

/**
 * Device 数据传输对象 — 用于接收前端请求参数。
 *
 * @author hula0710
 * @since 2026-07-20
 */
public class DeviceDTO {

    private String deviceName;
    private String deviceCode;
    private String deviceType;
    private Integer status;
    private String ipAddress;
    private Integer port;
    private String location;

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
}
