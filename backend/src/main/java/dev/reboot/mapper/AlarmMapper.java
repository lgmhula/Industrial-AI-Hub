package dev.reboot.mapper;

import dev.reboot.entity.Alarm;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Alarm 表 Mapper。
 *
 * @author hula0710
 * @since 2026-07-20
 */
@Mapper
public interface AlarmMapper {

    @Select("SELECT * FROM alarm ORDER BY triggered_at DESC")
    List<Alarm> findAll();

    @Select("SELECT * FROM alarm WHERE device_id = #{deviceId} ORDER BY triggered_at DESC")
    List<Alarm> findByDeviceId(Long deviceId);

    @Select("SELECT * FROM alarm WHERE status = #{status} ORDER BY triggered_at DESC")
    List<Alarm> findByStatus(Integer status);

    @Insert("INSERT INTO alarm(device_id, alarm_type, alarm_level, alarm_message, status, triggered_at) "
          + "VALUES(#{deviceId}, #{alarmType}, #{alarmLevel}, #{alarmMessage}, #{status}, #{triggeredAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Alarm alarm);

    @Update("UPDATE alarm SET status=1 WHERE id=#{id}")
    int acknowledge(Long id);

    @Update("UPDATE alarm SET status=2, resolved_at=NOW() WHERE id=#{id}")
    int resolve(Long id);
}
