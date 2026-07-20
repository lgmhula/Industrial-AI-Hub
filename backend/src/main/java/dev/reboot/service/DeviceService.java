package dev.reboot.service;

import dev.reboot.entity.Device;
import dev.reboot.mapper.DeviceMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DeviceService {

    private final DeviceMapper deviceMapper;

    public DeviceService(DeviceMapper deviceMapper) {
        this.deviceMapper = deviceMapper;
    }

    public List<Device> findAll() {
        return deviceMapper.findAll();
    }

    public Device findById(Long id) {
        Device device = deviceMapper.findById(id);
        if (device == null) {
            throw new RuntimeException("Device not found: " + id);
        }
        return device;
    }

    public Device create(Device device) {
        deviceMapper.insert(device);
        return device;
    }

    public Device update(Long id, Device device) {
        device.setId(id);
        deviceMapper.update(device);
        return device;
    }

    public void delete(Long id) {
        findById(id);
        deviceMapper.deleteById(id);
    }

    public List<Device> findByType(String type) {
        return deviceMapper.findByType(type);
    }
}
