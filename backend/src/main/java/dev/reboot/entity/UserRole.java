package dev.reboot.entity;

/**
 * 用户角色关联实体 —— 对应 reboot.user_role 表。
 *
 * @author hula0710
 * @since 2026-07-20
 */
public class UserRole {

    private Long id;
    private Long userId;
    private Long roleId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }

    @Override
    public String toString() {
        return String.format("UserRole{id=%d, userId=%d, roleId=%d}", id, userId, roleId);
    }
}
