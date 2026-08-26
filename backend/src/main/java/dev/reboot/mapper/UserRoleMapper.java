package dev.reboot.mapper;

import dev.reboot.entity.UserRole;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * user_role 表 Mapper —— RBAC 权限基础。
 *
 * @author hula0710
 * @since 2026-07-24
 */
@Mapper
public interface UserRoleMapper {

    /** 删除用户的所有角色关联（逻辑删除用户时使用）。 */
    @Delete("DELETE FROM user_role WHERE user_id = #{userId}")
    int deleteByUserId(Long userId);

    /** 删除用户的指定角色关联。 */
    @Delete("DELETE FROM user_role WHERE user_id = #{userId} AND role_id = #{roleId}")
    int deleteByUserAndRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Select("SELECT * FROM user_role WHERE user_id = #{userId}")
    List<UserRole> findByUserId(Long userId);

    @Select("SELECT r.role_code FROM user_role ur "
          + "JOIN role r ON ur.role_id = r.id "
          + "WHERE ur.user_id = #{userId}")
    List<String> findRoleCodesByUserId(Long userId);

    @Select("<script>SELECT ur.user_id, r.role_code FROM user_role ur "
          + "JOIN role r ON ur.role_id = r.id "
          + "WHERE ur.user_id IN "
          + "<foreach collection='userIds' item='uid' open='(' separator=',' close=')'>#{uid}</foreach></script>")
    List<java.util.Map<String, Object>> findRoleCodesByUserIds(@Param("userIds") List<Long> userIds);

    @Insert("INSERT INTO user_role(user_id, role_id) VALUES(#{userId}, #{roleId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserRole userRole);
}
