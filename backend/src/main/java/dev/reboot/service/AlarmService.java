package dev.reboot.service;

import dev.reboot.entity.Alarm;
import dev.reboot.mapper.AlarmMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Alarm 业务逻辑层。
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

    public List<Alarm> listAll() {
        return alarmMapper.findAll();
    }

    public List<Alarm> listByDevice(Long deviceId) {
        return alarmMapper.findByDeviceId(deviceId);
    }

    public List<Alarm> listByStatus(Integer status) {
        return alarmMapper.findByStatus(status);
    }

    public boolean acknowledge(Long id) {
        return alarmMapper.acknowledge(id) > 0;
    }

    public boolean resolve(Long id) {
        return alarmMapper.resolve(id) > 0;
    }
}
