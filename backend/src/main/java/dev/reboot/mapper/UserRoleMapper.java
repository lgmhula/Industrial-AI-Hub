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

    @Select("SELECT * FROM `user_role` WHERE user_id = #{userId}")
    List<UserRole> findByUserId(Long userId);

    @Select("SELECT r.role_code FROM `user_role` ur "
          + "JOIN `role` r ON ur.role_id = r.id "
          + "WHERE ur.user_id = #{userId}")
    List<String> findRoleCodesByUserId(Long userId);

    @Insert("INSERT INTO `user_role`(user_id, role_id) VALUES(#{userId}, #{roleId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserRole userRole);
}
