package dev.reboot.mapper;

import dev.reboot.entity.DeviceData;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * DeviceData 表 Mapper。
 *
 * @author hula0710
 * @since 2026-07-20
 */
@Mapper
public interface DeviceDataMapper {

    @Select("SELECT * FROM device_data WHERE device_id = #{deviceId} ORDER BY recorded_at DESC")
    List<DeviceData> findByDeviceId(Long deviceId);

    @Insert("INSERT INTO device_data(device_id, data_type, data_value, unit, recorded_at) "
          + "VALUES(#{deviceId}, #{dataType}, #{dataValue}, #{unit}, #{recordedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DeviceData deviceData);

    @Select("SELECT * FROM device_data WHERE device_id = #{deviceId} AND data_type = #{dataType} "
          + "ORDER BY recorded_at DESC LIMIT 1")
    DeviceData findLatest(Long deviceId, String dataType);
}
