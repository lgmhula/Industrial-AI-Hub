package dev.reboot.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import dev.reboot.dto.AlarmVO;
import dev.reboot.entity.Alarm;
import dev.reboot.mapper.AlarmMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Alarm 业务逻辑层 —— 报警查询 + 确认/解决 + 规则触发。
 *
 * <p>分页统一使用 PageHelper + PageInfo&lt;AlarmVO&gt;。</p>
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

    /** 分页查询全部告警。 */
    public PageInfo<AlarmVO> listAllPaged(int page, int size) {
        PageHelper.startPage(page, size);
        List<Alarm> records = alarmMapper.findAll();
        PageInfo<Alarm> raw = new PageInfo<>(records);
        List<AlarmVO> voList = records.stream().map(AlarmVO::from).toList();
        PageInfo<AlarmVO> result = new PageInfo<>();
        result.setList(voList);
        result.setTotal(raw.getTotal());
        result.setPageNum(raw.getPageNum());
        result.setPageSize(raw.getPageSize());
        result.setPages(raw.getPages());
        result.setSize(voList.size());
        return result;
    }

    /** 按设备 ID 分页查询。 */
    public PageInfo<AlarmVO> listByDevicePaged(Long deviceId, int page, int size) {
        PageHelper.startPage(page, size);
        List<Alarm> records = alarmMapper.findByDeviceId(deviceId);
        PageInfo<Alarm> raw = new PageInfo<>(records);
        List<AlarmVO> voList = records.stream().map(AlarmVO::from).toList();
        PageInfo<AlarmVO> result = new PageInfo<>();
        result.setList(voList);
        result.setTotal(raw.getTotal());
        result.setPageNum(raw.getPageNum());
        result.setPageSize(raw.getPageSize());
        result.setPages(raw.getPages());
        result.setSize(voList.size());
        return result;
    }

    /** 按状态分页查询。 */
    public PageInfo<AlarmVO> listByStatusPaged(Integer status, int page, int size) {
        PageHelper.startPage(page, size);
        List<Alarm> records = alarmMapper.findByStatus(status);
        PageInfo<Alarm> raw = new PageInfo<>(records);
        List<AlarmVO> voList = records.stream().map(AlarmVO::from).toList();
        PageInfo<AlarmVO> result = new PageInfo<>();
        result.setList(voList);
        result.setTotal(raw.getTotal());
        result.setPageNum(raw.getPageNum());
        result.setPageSize(raw.getPageSize());
        result.setPages(raw.getPages());
        result.setSize(voList.size());
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
