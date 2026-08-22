package dev.reboot.entity;

import java.time.LocalDateTime;

/**
 * 站点实体 —— 对应 reboot.site 表（P1-01 站点授权模型）。
 *
 * <p>资源归属层级：User → Site → Device → Alarm/DeviceData。
 * 站点为设备资源作用域（工厂/车间），用户经 user_site 获得站点内角色。</p>
 *
 * @author AI 助手
 * @since 2026-08-23
 */
public class Site {

    private Long id;
    private String siteName;
    private String siteCode;
    private String description;
    private String address;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = siteName; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return String.format("Site{id=%d, name='%s', code='%s'}", id, siteName, siteCode);
    }
}
