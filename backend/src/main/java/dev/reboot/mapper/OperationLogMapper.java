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

    @Select("SELECT * FROM operation_log ORDER BY created_at DESC")
    List<OperationLog> findAll();

    @Select("SELECT * FROM operation_log ORDER BY created_at DESC LIMIT 100")
    List<OperationLog> findRecent();

    @Select("SELECT * FROM operation_log WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<OperationLog> findByUserId(@Param("userId") Long userId);

    @Insert("INSERT INTO operation_log(user_id, operation_type, target_type, target_id, description, ip_address) "
          + "VALUES(#{userId}, #{operationType}, #{targetType}, #{targetId}, #{description}, #{ipAddress})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(OperationLog log);
}
