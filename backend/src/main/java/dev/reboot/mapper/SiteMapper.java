package dev.reboot.mapper;

import dev.reboot.entity.Site;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Site 表 Mapper（P1-01 站点授权模型）。
 *
 * @author AI 助手
 * @since 2026-08-23
 */
@Mapper
public interface SiteMapper {

    @Select("SELECT * FROM site WHERE id = #{id}")
    Site findById(Long id);

    @Select("SELECT * FROM site WHERE site_code = #{siteCode}")
    Site findByCode(String siteCode);

    @Select("SELECT * FROM site ORDER BY id ASC")
    List<Site> findAll();

    @Insert("INSERT INTO site(site_name, site_code, description, address) "
          + "VALUES(#{siteName}, #{siteCode}, #{description}, #{address})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Site site);
}
