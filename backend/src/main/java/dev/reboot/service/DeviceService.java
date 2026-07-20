package dev.reboot.service;

import dev.reboot.dto.DeviceDTO;
import dev.reboot.entity.Device;
import dev.reboot.mapper.DeviceMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Device 业务逻辑层。
 *
 * @author hula0710
 * @since 2026-07-20
 */
@Service
public class DeviceService {

    private final DeviceMapper deviceMapper;

    public DeviceService(DeviceMapper deviceMapper) {
        this.deviceMapper = deviceMapper;
    }

    public List<Device> listAll() {
        return deviceMapper.findAll();
    }

    public Device getById(Long id) {
        return deviceMapper.findById(id);
    }

    public Device create(DeviceDTO dto) {
        Device device = new Device();
        device.setDeviceName(dto.getDeviceName());
        device.setDeviceCode(dto.getDeviceCode());
        device.setDeviceType(dto.getDeviceType());
        device.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        device.setIpAddress(dto.getIpAddress());
        device.setPort(dto.getPort());
        device.setLocation(dto.getLocation());
        deviceMapper.insert(device);
        return device;
    }

    public Device update(Long id, DeviceDTO dto) {
        Device device = deviceMapper.findById(id);
        if (device == null) return null;
        device.setDeviceName(dto.getDeviceName());
        device.setDeviceType(dto.getDeviceType());
        if (dto.getStatus() != null) device.setStatus(dto.getStatus());
        device.setIpAddress(dto.getIpAddress());
        device.setPort(dto.getPort());
        device.setLocation(dto.getLocation());
        deviceMapper.update(device);
        return device;
    }

    public boolean delete(Long id) {
        return deviceMapper.deleteById(id) > 0;
    }
}
