# Industrial AI Hub — 数据库 ER 图

> 数据库：`reboot` | 字符集：utf8mb4 | 引擎：InnoDB

## ER Diagram

```mermaid
erDiagram
    user ||--o{ user_role : has
    role ||--o{ user_role : assigned
    user ||--o{ operation_log : performs
    device ||--o{ device_data : generates
    device ||--o{ alarm : triggers

    user {
        BIGINT  id           PK "主键"
        VARCHAR username     UK "用户名"
        VARCHAR password        "BCrypt 加密"
        VARCHAR email        UK "邮箱"
        VARCHAR phone           "手机号"
        TINYINT status          "1-启用 0-禁用"
        TINYINT is_deleted      "逻辑删除"
        DATETIME created_at     "创建时间"
        DATETIME updated_at     "更新时间"
    }

    role {
        BIGINT  id           PK "主键"
        VARCHAR role_name       "角色名称"
        VARCHAR role_code    UK "ADMIN/OPERATOR/VIEWER"
        VARCHAR description     "角色描述"
        DATETIME created_at     "创建时间"
    }

    user_role {
        BIGINT  id           PK "主键"
        BIGINT  user_id      FK "用户 ID"
        BIGINT  role_id      FK "角色 ID"
    }

    device {
        BIGINT  id           PK "主键"
        VARCHAR device_name     "设备名称"
        VARCHAR device_code  UK "设备编码"
        VARCHAR device_type     "PLC/SENSOR/CAMERA/ROBOT/OTHER"
        TINYINT status          "1-在线 0-离线 2-维护中"
        VARCHAR ip_address      "IP 地址"
        INT     port            "端口号"
        VARCHAR location        "安装位置"
        TINYINT is_deleted      "逻辑删除"
        DATETIME created_at     "创建时间"
        DATETIME updated_at     "更新时间"
    }

    device_data {
        BIGINT   id          PK "主键"
        BIGINT   device_id   FK "设备 ID"
        VARCHAR  data_type      "TEMPERATURE/PRESSURE/SPEED/HUMIDITY/CURRENT"
        DECIMAL  data_value     "数据值 (18,6)"
        VARCHAR  unit           "单位"
        DATETIME recorded_at    "采集时间"
        DATETIME created_at     "入库时间"
    }

    alarm {
        BIGINT   id           PK "主键"
        BIGINT   device_id    FK "设备 ID"
        VARCHAR  alarm_type      "OVER_TEMP/UNDER_PRESSURE..."
        TINYINT  alarm_level     "1-一般 2-重要 3-紧急"
        VARCHAR  alarm_message   "告警描述"
        TINYINT  status          "0-未处理 1-已确认 2-已解决"
        DATETIME triggered_at    "触发时间"
        DATETIME resolved_at     "解决时间"
        DATETIME created_at      "创建时间"
    }

    operation_log {
        BIGINT   id              PK "主键"
        BIGINT   user_id         FK "操作用户 ID"
        VARCHAR  operation_type     "CREATE/UPDATE/DELETE/LOGIN/EXPORT"
        VARCHAR  target_type        "USER/DEVICE/ALARM/ROLE"
        BIGINT   target_id          "目标 ID"
        VARCHAR  description        "操作描述"
        VARCHAR  ip_address         "操作 IP"
        DATETIME created_at         "操作时间"
    }
```

## 索引设计

| 表 | 索引 | 类型 | 覆盖查询 |
|----|------|:---:|------|
| `user` | `PRIMARY(id)` | 聚簇 | findById |
| | `uk_username` | 唯一 | findByUsername |
| | `uk_email` | 唯一 | — |
| | `idx_is_deleted` | 普通 | is_deleted=0 |
| `device` | `PRIMARY(id)` | 聚簇 | findById |
| | `uk_device_code` | 唯一 | findByCode |
| | `idx_device_type` | 普通 | findByType |
| | `idx_status` | 普通 | status=? |
| | `idx_is_deleted` | 普通 | is_deleted=0 |
| `device_data` | `PRIMARY(id)` | 聚簇 | — |
| | `idx_device_id` | 普通 | device_id=? |
| | `idx_recorded_at` | 普通 | ORDER BY recorded_at |
| | `idx_device_type_time` | 复合 | device_id+data_type+recorded_at |
| `alarm` | `PRIMARY(id)` | 聚簇 | — |
| | `idx_device_id` | 普通 | device_id=? |
| | `idx_alarm_level` | 普通 | alarm_level=? |
| | `idx_status` | 普通 | status=? |
| | `idx_triggered_at` | 普通 | ORDER BY triggered_at |
| `operation_log` | `PRIMARY(id)` | 聚簇 | — |
| | `idx_user_id` | 普通 | userId=? |
| | `idx_operation_type` | 普通 | — |
| | `idx_created_at` | 普通 | ORDER BY created_at |

## 表规模

| 表 | 列数 | 约束(CHECK) | 索引 |
|----|:---:|:---:|:---:|
| `user` | 9 | 1 (status) | 4 |
| `role` | 5 | 0 | 2 |
| `user_role` | 4 | 0 | 3 |
| `device` | 11 | 2 (status, device_type) | 5 |
| `device_data` | 7 | 1 (data_type) | 4 |
| `alarm` | 9 | 2 (level, status) | 5 |
| `operation_log` | 8 | 2 (operation_type, target_type) | 4 |
| **合计** | **53** | **8** | **27** |
