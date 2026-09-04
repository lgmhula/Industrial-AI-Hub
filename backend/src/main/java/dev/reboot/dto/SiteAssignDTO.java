package dev.reboot.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 分配用户到站点的 DTO。
 *
 * @author AI 助手
 * @since 2026-09-04
 */
public class SiteAssignDTO {

    @NotNull(message = "站点 ID 不能为空")
    private Long siteId;

    @NotNull(message = "角色 ID 不能为空")
    private Long roleId;

    public Long getSiteId() { return siteId; }
    public void setSiteId(Long siteId) { this.siteId = siteId; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
}
