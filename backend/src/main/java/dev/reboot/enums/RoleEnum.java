package dev.reboot.enums;

/**
 * 角色枚举 —— 用于 @RequireRole 注解的权限判定。
 *
 * <p>权限等级用于 isAtLeast() 比较，值越小权限越高。</p>
 *
 * @author hula0710
 * @since 2026-07-24
 */
public enum RoleEnum {

    // ADMIN(1, "ROLE_ADMIN"),
    // OPERATOR(2, "ROLE_OPERATOR"),
    // VIEWER(3, "ROLE_VIEWER");
    ADMIN(1, "ADMIN"),
    OPERATOR(2, "OPERATOR"),
    VIEWER(3, "VIEWER");

    private final int level;
    private final String roleCode;

    RoleEnum(int level, String roleCode) {
        this.level = level;
        this.roleCode = roleCode;
    }

    public int getLevel() { return level; }
    public String getRoleCode() { return roleCode; }

    /**
     * 当前角色权限是否 ≥ target。
     * level 值越小权限越高，因此 this.level <= target.level 为 true。
     */
    public boolean isAtLeast(RoleEnum target) {
        return this.level <= target.level;
    }

    public Long getRoleId() {
        return (long) this.ordinal() + 1;
    }

    public static RoleEnum fromCode(String code) {
        for (RoleEnum r : values()) {
            if (r.roleCode.equals(code)) return r;
        }
        return null;
    }
}
