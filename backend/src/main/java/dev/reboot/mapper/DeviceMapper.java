package dev.reboot.mapper;

import dev.reboot.entity.Device;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Device 表 Mapper。
 *
 * @author hula0710
 * @since 2026-07-20
 */
@Mapper
public interface DeviceMapper {

    @Select("SELECT * FROM device ORDER BY id DESC")
    List<Device> findAll();

    @Select("SELECT * FROM device WHERE id = #{id}")
    Device findById(Long id);

    @Select("SELECT * FROM device WHERE device_code = #{deviceCode}")
    Device findByCode(String deviceCode);

    @Insert("INSERT INTO device(device_name, device_code, device_type, status, ip_address, port, location) "
          + "VALUES(#{deviceName}, #{deviceCode}, #{deviceType}, #{status}, #{ipAddress}, #{port}, #{location})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Device device);

    @Update("UPDATE device SET device_name=#{deviceName}, device_type=#{deviceType}, "
          + "status=#{status}, ip_address=#{ipAddress}, port=#{port}, location=#{location} "
          + "WHERE id=#{id}")
    int update(Device device);

    @Delete("DELETE FROM device WHERE id = #{id}")
    int deleteById(Long id);

    @Select("SELECT * FROM device WHERE device_type = #{deviceType}")
    List<Device> findByType(String deviceType);
}
