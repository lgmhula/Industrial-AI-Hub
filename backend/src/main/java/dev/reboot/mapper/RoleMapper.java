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

    @Select("SELECT * FROM role ORDER BY id")
    List<Role> findAll();

    @Select("SELECT * FROM role WHERE id = #{id}")
    Role findById(Long id);

    @Select("SELECT * FROM role WHERE role_code = #{roleCode}")
    Role findByCode(String roleCode);
}
