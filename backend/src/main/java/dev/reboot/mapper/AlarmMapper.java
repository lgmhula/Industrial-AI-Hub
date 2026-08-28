package dev.reboot.mapper;

import dev.reboot.dto.AlarmSiteVO;
import dev.reboot.entity.Alarm;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Alarm 表 Mapper — 分页由 PageHelper 自动拦截，无需手动 LIMIT。
 *
 * <p>P1-01：列表查询按设备所属站点过滤（JOIN device；siteIds=null 表示不过滤=全局管理员）。</p>
 *
 * @author hula0710
 * @since 2026-07-20
 */
@Mapper
public interface AlarmMapper {

    @Select("<script>SELECT a.* FROM alarm a JOIN device d ON a.device_id = d.id"
            + "<where>"
            + "<if test='siteIds != null'> AND d.site_id IN "
            + "<foreach collection='siteIds' item='sid' open='(' separator=',' close=')'>#{sid}</foreach>"
            + "</if>"
            + "<if test='keyword != null and keyword != &quot;&quot;'> AND a.alarm_message LIKE CONCAT('%',#{keyword},'%')</if>"
            + "<if test='alarmLevel != null'> AND a.alarm_level = #{alarmLevel}</if>"
            + "</where> ORDER BY a.triggered_at DESC</script>")
    List<Alarm> findAll(@Param("siteIds") List<Long> siteIds,
                        @Param("keyword") String keyword,
                        @Param("alarmLevel") Integer alarmLevel);

    @Select("SELECT * FROM alarm WHERE device_id = #{deviceId} ORDER BY triggered_at DESC")
    List<Alarm> findByDeviceId(@Param("deviceId") Long deviceId);

    /** 站点活动告警（status=0 未处理，仅未删除设备），按触发时间倒序，供 AI 工具查询。 */
    @Select("SELECT a.id, a.device_id, a.alarm_type, a.alarm_level, a.alarm_message, a.status, a.triggered_at,"
            + " d.device_name"
            + " FROM alarm a JOIN device d ON a.device_id = d.id"
            + " WHERE a.status = 0 AND d.site_id = #{siteId} AND d.is_deleted = 0"
            + " ORDER BY a.triggered_at DESC LIMIT #{limit}")
    List<AlarmSiteVO> findActiveBySiteId(@Param("siteId") Long siteId, @Param("limit") int limit);

    @Select("<script>SELECT a.* FROM alarm a JOIN device d ON a.device_id = d.id"
            + " WHERE a.status = #{status}"
            + "<if test='siteIds != null'> AND d.site_id IN "
            + "<foreach collection='siteIds' item='sid' open='(' separator=',' close=')'>#{sid}</foreach>"
            + "</if>"
            + "<if test='keyword != null and keyword != &quot;&quot;'> AND a.alarm_message LIKE CONCAT('%',#{keyword},'%')</if>"
            + "<if test='alarmLevel != null'> AND a.alarm_level = #{alarmLevel}</if>"
            + " ORDER BY a.triggered_at DESC</script>")
    List<Alarm> findByStatus(@Param("status") Integer status,
                             @Param("siteIds") List<Long> siteIds,
                             @Param("keyword") String keyword,
                             @Param("alarmLevel") Integer alarmLevel);

    @Select("SELECT * FROM alarm WHERE id = #{id}")
    Alarm findById(@Param("id") Long id);

    @Insert("INSERT INTO alarm(device_id, alarm_type, alarm_level, alarm_message, status, triggered_at) "
          + "VALUES(#{deviceId}, #{alarmType}, #{alarmLevel}, #{alarmMessage}, #{status}, #{triggeredAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Alarm alarm);

    @Update("UPDATE alarm SET status=1, acknowledged_at=NOW(), acknowledged_by=#{userId} WHERE id=#{id}")
    int acknowledge(@Param("id") Long id, @Param("userId") Long userId);

    @Update("UPDATE alarm SET status=2, resolved_at=NOW(), resolved_by=#{userId} WHERE id=#{id}")
    int resolve(@Param("id") Long id, @Param("userId") Long userId);
}
