package dev.reboot.mapper;

import dev.reboot.entity.OperationLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * OperationLog 表 Mapper — 分页由 PageHelper 自动拦截。
 *
 * @author hula0710
 * @since 2026-07-20
 */
@Mapper
public interface OperationLogMapper {

    @Select("<script>SELECT ol.*, u.username FROM operation_log ol "
            + "LEFT JOIN user u ON ol.user_id = u.id"
            + "<where>"
            + "<if test='keyword != null and keyword != &quot;&quot;'> AND ol.description LIKE CONCAT('%',#{keyword},'%')</if>"
            + "<if test='operationType != null and operationType != &quot;&quot;'> AND ol.operation_type = #{operationType}</if>"
            + "</where> ORDER BY ol.created_at DESC</script>")
    List<OperationLog> findAll(@Param("keyword") String keyword, @Param("operationType") String operationType);

    @Select("SELECT ol.*, u.username FROM operation_log ol LEFT JOIN user u ON ol.user_id = u.id ORDER BY ol.created_at DESC LIMIT 100")
    List<OperationLog> findRecent();

    @Select("SELECT ol.*, u.username FROM operation_log ol LEFT JOIN user u ON ol.user_id = u.id WHERE ol.user_id = #{userId} ORDER BY ol.created_at DESC")
    List<OperationLog> findByUserId(@Param("userId") Long userId);

    @Insert("INSERT INTO operation_log(user_id, operation_type, target_type, target_id, description, ip_address) "
          + "VALUES(#{userId}, #{operationType}, #{targetType}, #{targetId}, #{description}, #{ipAddress})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(OperationLog log);
}
