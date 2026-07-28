package dev.reboot.mapper;

import dev.reboot.entity.Alarm;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Alarm 表 Mapper —— 告警 CRUD + 分页 + 确认/解决。
 *
 * @author hula0710
 * @since 2026-07-20
 */
@Mapper
public interface AlarmMapper {

    /** 全量查询（按触发时间倒序）。 */
    @Select("SELECT * FROM alarm ORDER BY triggered_at DESC")
    List<Alarm> findAll();

    /** 分页查询全部告警。 */
    @Select("SELECT * FROM alarm ORDER BY triggered_at DESC LIMIT #{offset}, #{size}")
    List<Alarm> findAllPaged(@Param("offset") int offset, @Param("size") int size);

    /** 统计总数。 */
    @Select("SELECT COUNT(*) FROM alarm")
    long count();

    /** 按设备 ID 查询。 */
    @Select("SELECT * FROM alarm WHERE device_id = #{deviceId} ORDER BY triggered_at DESC")
    List<Alarm> findByDeviceId(Long deviceId);

    /** 按设备 ID 分页查询。 */
    @Select("SELECT * FROM alarm WHERE device_id = #{deviceId} ORDER BY triggered_at DESC LIMIT #{offset}, #{size}")
    List<Alarm> findByDeviceIdPaged(@Param("deviceId") Long deviceId,
                                    @Param("offset") int offset, @Param("size") int size);

    /** 按设备统计数。 */
    @Select("SELECT COUNT(*) FROM alarm WHERE device_id = #{deviceId}")
    long countByDeviceId(Long deviceId);

    /** 按状态查询。 */
    @Select("SELECT * FROM alarm WHERE status = #{status} ORDER BY triggered_at DESC")
    List<Alarm> findByStatus(Integer status);

    /** 按状态分页查询。 */
    @Select("SELECT * FROM alarm WHERE status = #{status} ORDER BY triggered_at DESC LIMIT #{offset}, #{size}")
    List<Alarm> findByStatusPaged(@Param("status") Integer status,
                                  @Param("offset") int offset, @Param("size") int size);

    /** 按状态统计数。 */
    @Select("SELECT COUNT(*) FROM alarm WHERE status = #{status}")
    long countByStatus(Integer status);

    /** 插入告警。 */
    @Insert("INSERT INTO alarm(device_id, alarm_type, alarm_level, alarm_message, status, triggered_at) "
          + "VALUES(#{deviceId}, #{alarmType}, #{alarmLevel}, #{alarmMessage}, #{status}, #{triggeredAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Alarm alarm);

    /** 确认告警。 */
    @Update("UPDATE alarm SET status=1 WHERE id=#{id}")
    int acknowledge(Long id);

    /** 解决告警。 */
    @Update("UPDATE alarm SET status=2, resolved_at=NOW() WHERE id=#{id}")
    int resolve(Long id);
}
