package dev.reboot.service;

import dev.reboot.entity.DeviceData;
import dev.reboot.mapper.DeviceDataMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * DeviceData 业务逻辑层。
 *
 * @author hula0710
 * @since 2026-07-26
 */
@Service
public class DeviceDataService {

    private final DeviceDataMapper deviceDataMapper;

    public DeviceDataService(DeviceDataMapper deviceDataMapper) {
        this.deviceDataMapper = deviceDataMapper;
    }

    /** 按设备 ID 查询所有数据记录。 */
    public List<DeviceData> listByDevice(Long deviceId) {
        return deviceDataMapper.findByDeviceId(deviceId);
    }

    /** 获取设备最新一条数据。 */
    public DeviceData getLatest(Long deviceId, String dataType) {
        return deviceDataMapper.findLatest(deviceId, dataType);
    }
}
