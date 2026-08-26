package dev.reboot.mapper;

import dev.reboot.entity.UserSite;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * user_site 表 Mapper（P1-01）—— 站点成员与站点内角色。
 *
 * @author AI 助手
 * @since 2026-08-23
 */
@Mapper
public interface UserSiteMapper {

    /** 用户可访问的站点 id 列表（站点成员）。 */
    @Select("SELECT site_id FROM user_site WHERE user_id = #{userId}")
    List<Long> findSiteIdsByUserId(Long userId);

    /** 用户在指定站点的角色代码（JOIN role；无成员记录返回 null）。 */
    @Select("SELECT r.role_code FROM user_site us "
          + "JOIN role r ON us.role_id = r.id "
          + "WHERE us.user_id = #{userId} AND us.site_id = #{siteId}")
    String findRoleCodeByUserAndSite(@Param("userId") Long userId, @Param("siteId") Long siteId);

    @Insert("INSERT INTO user_site(user_id, site_id, role_id) VALUES(#{userId}, #{siteId}, #{roleId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserSite userSite);
}
