package code.day19;

import java.time.LocalDateTime;

/**
 * 设备 POJO —— MyBatis 注解映射实体。
 *
 * @author Reboot
 * @since 2026-07-18
 */
public class Device {
    private Long id;
    private String name;
    private String type;
    private String location;
    private String status;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return String.format("Device{id=%d, name='%s', type='%s', location='%s', status='%s'}",
                id, name, type, location, status);
    }
}
