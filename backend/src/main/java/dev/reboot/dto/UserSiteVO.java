package dev.reboot.dto;

/**
 * 用户站点成员记录 VO（分配站点对话框展示用）。
 *
 * @author AI 助手
 * @since 2026-09-04
 */
public class UserSiteVO {
    private Long siteId;
    private String siteName;
    private String siteCode;
    private Long roleId;
    private String roleCode;

    public Long getSiteId() { return siteId; }
    public void setSiteId(Long siteId) { this.siteId = siteId; }
    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = siteName; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
}
