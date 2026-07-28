package dev.reboot.service;

import dev.reboot.entity.OperationLog;
import dev.reboot.mapper.OperationLogMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OperationLog 业务逻辑层 —— 分页查询 + 插入。
 *
 * @author hula0710
 * @since 2026-07-26
 */
@Service
public class OperationLogService {

    private final OperationLogMapper operationLogMapper;

    public OperationLogService(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    /** 查询最近 100 条操作日志。 */
    public List<OperationLog> listRecent() {
        return operationLogMapper.findRecent();
    }

    /** 分页查询全部日志。 */
    public Map<String, Object> listPaged(int page, int size) {
        int offset = (page - 1) * size;
        List<OperationLog> records = operationLogMapper.findPaged(offset, size);
        long total = operationLogMapper.count();
        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", size);
        return result;
    }

    /** 按用户 ID 分页查询。 */
    public Map<String, Object> listByUserId(Long userId, int page, int size) {
        int offset = (page - 1) * size;
        List<OperationLog> records = operationLogMapper.findByUserIdPaged(userId, offset, size);
        long total = operationLogMapper.countByUserId(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", size);
        return result;
    }
}
