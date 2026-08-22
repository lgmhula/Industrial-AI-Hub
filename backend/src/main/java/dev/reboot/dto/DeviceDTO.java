package dev.reboot.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Device 数据传输对象 — 用于接收前端请求参数。
 *
 * <p>核心字段使用 Jakarta Validation 注解，由 {@code @Valid} 触发校验。</p>
 *
 * @author hula0710
 * @since 2026-07-20
 */
public class DeviceDTO {

    @NotBlank(message = "设备名称不能为空")
    private String deviceName;

    @NotBlank(message = "设备编码不能为空")
    private String deviceCode;

    @NotBlank(message = "设备类型不能为空")
    private String deviceType;

    /** 站点 ID（P1-01）。可选：缺省时取创建者唯一站点/默认站点。 */
    private Long siteId;

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
    public Long getSiteId() { return siteId; }
    public void setSiteId(Long siteId) { this.siteId = siteId; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
}
