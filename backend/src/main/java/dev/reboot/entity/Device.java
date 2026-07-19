package dev.reboot.entity;

/**
 * 设备实体 —— 对应 MySQL reboot.device 表。
 *
 * <p>字段名采用驼峰命名，由 MyBatis mapUnderscoreToCamelCase 自动映射。</p>
 *
 * @author hula0710
 * @since 2026-07-19
 */
public class Device {

    private Long id;
    private String name;
    private String type;
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @Override
    public String toString() {
        return String.format("Device{id=%d, name='%s', type='%s', status='%s'}",
                id, name, type, status);
    }
}
