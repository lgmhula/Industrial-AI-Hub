package dev.reboot.controller;

import dev.reboot.entity.Device;
import dev.reboot.mapper.DeviceMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Device REST 控制器 —— 工业 AI Hub 首个 API 端点。
 *
 * <p>提供设备的 CRUD 操作，采用标准 RESTful 风格。</p>
 *
 * @author hula0710
 * @since 2026-07-19
 */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceMapper deviceMapper;

    /** 构造器注入（Spring 推荐方式） */
    public DeviceController(DeviceMapper deviceMapper) {
        this.deviceMapper = deviceMapper;
    }

    /** GET /api/devices —— 查询全部设备 */
    @GetMapping
    public List<Device> list() {
        return deviceMapper.findAll();
    }

    /** GET /api/devices/{id} —— 按 ID 查询 */
    @GetMapping("/{id}")
    public Device getById(@PathVariable Long id) {
        return deviceMapper.findById(id);
    }

    /** GET /api/devices/type/{type} —— 按类型查询 */
    @GetMapping("/type/{type}")
    public List<Device> getByType(@PathVariable String type) {
        return deviceMapper.findByType(type);
    }

    /** POST /api/devices —— 新增设备 */
    @PostMapping
    public Device create(@RequestBody Device device) {
        deviceMapper.insert(device);
        return device;
    }

    /** PUT /api/devices/{id} —— 更新设备 */
    @PutMapping("/{id}")
    public Device update(@PathVariable Long id, @RequestBody Device device) {
        device.setId(id);
        deviceMapper.update(device);
        return device;
    }

    /** DELETE /api/devices/{id} —— 删除设备 */
    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        deviceMapper.deleteById(id);
        return "deleted: " + id;
    }
}
