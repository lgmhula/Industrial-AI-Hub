package dev.reboot.service;

import dev.reboot.dto.AlarmVO;
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

    public List<AlarmVO> listAll() {
        return alarmMapper.findAll().stream()
                .map(AlarmVO::from)
                .toList();
    }

    public List<AlarmVO> listByDevice(Long deviceId) {
        return alarmMapper.findByDeviceId(deviceId).stream()
                .map(AlarmVO::from)
                .toList();
    }

    public List<AlarmVO> listByStatus(Integer status) {
        return alarmMapper.findByStatus(status).stream()
                .map(AlarmVO::from)
                .toList();
    }

    public boolean acknowledge(Long id) {
        return alarmMapper.acknowledge(id) > 0;
    }

    public boolean resolve(Long id) {
        return alarmMapper.resolve(id) > 0;
    }
}
