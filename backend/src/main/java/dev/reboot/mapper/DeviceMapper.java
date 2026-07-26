package dev.reboot.mapper;

import dev.reboot.entity.Device;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Device 表 Mapper —— 所有查询默认过滤已删除记录。
 *
 * @author hula0710
 * @since 2026-07-24
 */
@Mapper
public interface DeviceMapper {

    @Select("SELECT * FROM device WHERE is_deleted = 0 ORDER BY id DESC")
    List<Device> findAll();

    @Select("SELECT * FROM device WHERE id = #{id} AND is_deleted = 0")
    Device findById(Long id);

    @Select("SELECT * FROM device WHERE device_code = #{deviceCode} AND is_deleted = 0")
    Device findByCode(String deviceCode);

    @Insert("INSERT INTO device(device_name, device_code, device_type, status, ip_address, port, location) "
          + "VALUES(#{deviceName}, #{deviceCode}, #{deviceType}, #{status}, #{ipAddress}, #{port}, #{location})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Device device);

    @Update("UPDATE device SET device_name=#{deviceName}, device_type=#{deviceType}, "
          + "status=#{status}, ip_address=#{ipAddress}, port=#{port}, location=#{location} "
          + "WHERE id=#{id} AND is_deleted = 0")
    int update(Device device);

    /** 逻辑删除（非物理删除）。 */
    @Update("UPDATE device SET is_deleted = 1 WHERE id = #{id}")
    int softDeleteById(Long id);

    /**
     * 动态搜索设备 —— 支持关键字模糊匹配、设备类型筛选、状态筛选。
     *
     * <p>使用 MyBatis {@code <script>} 标签实现动态 SQL。</p>
     */
    @Select({"<script>",
        "SELECT * FROM device WHERE is_deleted = 0",
        "<if test='keyword != null and keyword.length() > 0'>",
        "  AND (device_name LIKE CONCAT('%', #{keyword}, '%') OR device_code LIKE CONCAT('%', #{keyword}, '%'))",
        "</if>",
        "<if test='deviceType != null and deviceType.length() > 0'>",
        "  AND device_type = #{deviceType}",
        "</if>",
        "<if test='status != null'>",
        "  AND status = #{status}",
        "</if>",
        "ORDER BY id DESC",
        "</script>"})
    List<Device> searchDevices(@Param("keyword") String keyword,
                               @Param("deviceType") String deviceType,
                               @Param("status") Integer status);

    @Select("SELECT * FROM device WHERE device_type = #{deviceType} AND is_deleted = 0")
    List<Device> findByType(String deviceType);

}
