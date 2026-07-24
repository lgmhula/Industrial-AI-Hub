package dev.reboot.enums;

/**
 * 角色枚举 —— 对应 role 表的 role_code 字段。
 *
 * <p>权限层级：ADMIN &gt; OPERATOR &gt; VIEWER。</p>
 *
 * @author hula0710
 * @since 2026-07-24
 */
public enum RoleEnum {

    ADMIN("ADMIN", 1L),
    OPERATOR("OPERATOR", 2L),
    VIEWER("VIEWER", 3L);

    private final String code;
    private final long roleId; // 对应 role 表主键，init.sql 固定分配

    RoleEnum(String code, long roleId) {
        this.code = code;
        this.roleId = roleId;
    }

    public String getCode() { return code; }
    public long getRoleId() { return roleId; }

    /** 根据 role_code 字符串解析枚举。 */
    public static RoleEnum fromCode(String code) {
        for (RoleEnum r : values()) {
            if (r.code.equalsIgnoreCase(code)) return r;
        }
        return null;
    }

    /** 是否权限高于或等于目标角色。 */
    public boolean isAtLeast(RoleEnum target) {
        return this.ordinal() <= target.ordinal();
    }
}
