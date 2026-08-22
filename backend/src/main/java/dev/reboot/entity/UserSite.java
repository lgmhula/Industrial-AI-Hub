package dev.reboot.entity;

/**
 * 用户-站点角色实体 —— 对应 reboot.user_site 表（P1-01）。
 *
 * <p>用户在某站点的角色（role_id 引用 role 表），
 * 唯一约束 (user_id, site_id)；资源访问按「站点内角色」判定。</p>
 *
 * @author AI 助手
 * @since 2026-08-23
 */
public class UserSite {

    private Long id;
    private Long userId;
    private Long siteId;
    private Long roleId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getSiteId() { return siteId; }
    public void setSiteId(Long siteId) { this.siteId = siteId; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }

    @Override
    public String toString() {
        return String.format("UserSite{id=%d, userId=%d, siteId=%d, roleId=%d}",
                id, userId, siteId, roleId);
    }
}
