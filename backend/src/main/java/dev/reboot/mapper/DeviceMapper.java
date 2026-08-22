package dev.reboot.mapper;

import dev.reboot.entity.Device;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Device 表 Mapper —— 所有查询默认过滤已删除记录。
 *
 * <p>P1-01：列表查询支持站点范围过滤（siteIds=null 表示不过滤=全局管理员）。</p>
 *
 * @author hula0710
 * @since 2026-07-24
 */
@Mapper
public interface DeviceMapper {

    @Select("<script>SELECT * FROM device WHERE is_deleted = 0"
            + "<if test='siteIds != null'> AND site_id IN "
            + "<foreach collection='siteIds' item='sid' open='(' separator=',' close=')'>#{sid}</foreach>"
            + "</if> ORDER BY id DESC</script>")
    List<Device> findAll(@Param("siteIds") List<Long> siteIds);

    @Select("SELECT * FROM device WHERE id = #{id} AND is_deleted = 0")
    Device findById(Long id);

    @Select("SELECT * FROM device WHERE device_code = #{deviceCode} AND is_deleted = 0")
    Device findByCode(String deviceCode);

    @Insert("INSERT INTO device(site_id, device_name, device_code, device_type, status, ip_address, port, location) "
          + "VALUES(#{siteId}, #{deviceName}, #{deviceCode}, #{deviceType}, #{status}, #{ipAddress}, #{port}, #{location})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Device device);

    @Update("UPDATE device SET device_name=#{deviceName}, device_type=#{deviceType}, "
          + "status=#{status}, ip_address=#{ipAddress}, port=#{port}, location=#{location} "
          + "WHERE id=#{id} AND is_deleted = 0")
    int update(Device device);

    /** 逻辑删除（非物理删除）。 */
    @Update("UPDATE device SET is_deleted = 1 WHERE id = #{id}")
    int softDeleteById(Long id);

    /** 动态搜索设备 —— 支持关键字/类型/状态 + 站点范围（XML 实现）。 */
    List<Device> searchDevices(@Param("keyword") String keyword,
                               @Param("deviceType") String deviceType,
                               @Param("status") Integer status,
                               @Param("siteIds") List<Long> siteIds);

    /** 按设备类型查询（XML 实现）。 */
    List<Device> findByType(@Param("deviceType") String deviceType,
                            @Param("siteIds") List<Long> siteIds);

}
