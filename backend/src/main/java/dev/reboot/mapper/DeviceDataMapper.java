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

    /**
     * 按设备和数据类型查询指定时间范围内的记录。
     *
     * @param deviceId  设备 ID
     * @param dataType  数据类型（可选，null=全部类型）
     * @param startTime 开始时间（含）
     * @param endTime   结束时间（含）
     */
    @Select("<script>"
          + "SELECT * FROM device_data WHERE device_id = #{deviceId}"
          + "<if test='dataType != null and dataType.length() > 0'> AND data_type = #{dataType}</if>"
          + "<if test='startTime != null'> AND recorded_at &gt;= #{startTime}</if>"
          + "<if test='endTime != null'> AND recorded_at &lt;= #{endTime}</if>"
          + " ORDER BY recorded_at ASC"
          + "</script>")
    List<DeviceData> findByTimeRange(@Param("deviceId") Long deviceId,
                                     @Param("dataType") String dataType,
                                     @Param("startTime") java.time.LocalDateTime startTime,
                                     @Param("endTime") java.time.LocalDateTime endTime);

    /**
     * 指定时间范围内的聚合统计：avg/min/max/count。
     *
     * @return Map 键: avg/min/max/count
     */
    @Select("<script>"
          + "SELECT COALESCE(AVG(data_value),0) AS avg, COALESCE(MIN(data_value),0) AS min,"
          + " COALESCE(MAX(data_value),0) AS max, COUNT(*) AS cnt"
          + " FROM device_data WHERE device_id = #{deviceId} AND data_type = #{dataType}"
          + "<if test='startTime != null'> AND recorded_at &gt;= #{startTime}</if>"
          + "<if test='endTime != null'> AND recorded_at &lt;= #{endTime}</if>"
          + "</script>")
    java.util.Map<String, Object> aggregate(@Param("deviceId") Long deviceId,
                                            @Param("dataType") String dataType,
                                            @Param("startTime") java.time.LocalDateTime startTime,
                                            @Param("endTime") java.time.LocalDateTime endTime);
}