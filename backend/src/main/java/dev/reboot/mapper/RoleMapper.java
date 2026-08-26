package dev.reboot.mapper;

import dev.reboot.entity.Role;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Role 表 Mapper。
 *
 * @author hula0710
 * @since 2026-07-20
 */
@Mapper
public interface RoleMapper {

    @Select("SELECT * FROM role WHERE is_deleted = 0 ORDER BY id")
    List<Role> findAll();

    @Select("SELECT * FROM role WHERE id = #{id} AND is_deleted = 0")
    Role findById(Long id);

    @Select("SELECT * FROM role WHERE role_code = #{roleCode} AND is_deleted = 0")
    Role findByCode(String roleCode);

    @Insert("INSERT INTO role(role_name, role_code, description, status) "
          + "VALUES(#{roleName}, #{roleCode}, #{description}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Role role);

    @Update("UPDATE role SET role_name=#{roleName}, description=#{description}, status=#{status} "
          + "WHERE id=#{id} AND is_deleted = 0")
    int update(Role role);

    @Update("UPDATE role SET is_deleted = 1 WHERE id = #{id}")
    int softDeleteById(Long id);

    @Update("UPDATE role SET status = #{status} WHERE id = #{id} AND is_deleted = 0")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
