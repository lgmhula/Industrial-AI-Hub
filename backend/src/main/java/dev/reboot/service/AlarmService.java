package dev.reboot.service;

import dev.reboot.dto.AlarmVO;
import dev.reboot.entity.Alarm;
import dev.reboot.mapper.AlarmMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Alarm 业务逻辑层 —— 报警查询 + 确认/解决 + 规则触发。
 *
 * @author hula0710
 * @since 2026-07-20
 */
@Service
public class AlarmService {

    private final AlarmMapper alarmMapper;

    public AlarmService(AlarmMapper alarmMapper) {
        this.alarmMapper = alarmMapper;
    }

    /** 全量查询。 */
    public List<AlarmVO> listAll() {
        return alarmMapper.findAll().stream()
                .map(AlarmVO::from)
                .toList();
    }

    /**
     * 分页查询全部告警。
     *
     * @return Map 包含 records / total / page / pageSize
     */
    public Map<String, Object> listAllPaged(int page, int size) {
        int offset = (page - 1) * size;
        List<AlarmVO> records = alarmMapper.findAllPaged(offset, size).stream()
                .map(AlarmVO::from)
                .toList();
        long total = alarmMapper.count();
        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", size);
        return result;
    }

    /** 按设备 ID 查询。 */
    public List<AlarmVO> listByDevice(Long deviceId) {
        return alarmMapper.findByDeviceId(deviceId).stream()
                .map(AlarmVO::from)
                .toList();
    }

    /** 按设备 ID 分页查询。 */
    public Map<String, Object> listByDevicePaged(Long deviceId, int page, int size) {
        int offset = (page - 1) * size;
        List<AlarmVO> records = alarmMapper.findByDeviceIdPaged(deviceId, offset, size).stream()
                .map(AlarmVO::from)
                .toList();
        long total = alarmMapper.countByDeviceId(deviceId);
        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", size);
        return result;
    }

    /** 按状态查询。 */
    public List<AlarmVO> listByStatus(Integer status) {
        return alarmMapper.findByStatus(status).stream()
                .map(AlarmVO::from)
                .toList();
    }

    /** 按状态分页查询。 */
    public Map<String, Object> listByStatusPaged(Integer status, int page, int size) {
        int offset = (page - 1) * size;
        List<AlarmVO> records = alarmMapper.findByStatusPaged(status, offset, size).stream()
                .map(AlarmVO::from)
                .toList();
        long total = alarmMapper.countByStatus(status);
        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", size);
        return result;
    }

    /** 确认告警。 */
    public boolean acknowledge(Long id) {
        return alarmMapper.acknowledge(id) > 0;
    }

    /** 解决告警。 */
    public boolean resolve(Long id) {
        return alarmMapper.resolve(id) > 0;
    }

    /** 创建告警记录。 */
    public AlarmVO createAlarm(Long deviceId, String alarmType, Integer alarmLevel,
                               String alarmMessage, LocalDateTime triggeredAt) {
        Alarm alarm = new Alarm();
        alarm.setDeviceId(deviceId);
        alarm.setAlarmType(alarmType);
        alarm.setAlarmLevel(alarmLevel);
        alarm.setAlarmMessage(alarmMessage);
        alarm.setStatus(0);
        alarm.setTriggeredAt(triggeredAt);
        alarmMapper.insert(alarm);
        return AlarmVO.from(alarm);
    }
}
