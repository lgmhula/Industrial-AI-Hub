# Day 22 — 2026-07-20

## 今日目标
- [x] 设计项目包结构（controller/service/mapper/entity/dto/config）
- [x] 数据库表设计定稿：user, role, user_role, device, device_data, alarm, operation_log
- [x] 编写数据库初始化 SQL 脚本
- [x] 更新 Device entity 匹配新 schema
- [x] 新增 6 个 entity（User/Role/UserRole/DeviceData/Alarm/OperationLog）
- [x] 新增 5 个 Mapper（User/Role/DeviceData/Alarm/OperationLog）
- [x] 新增 3 个 Service（Device/User/Alarm）
- [x] 新增统一响应体 ApiResponse
- [x] 新增 CORS 跨域配置
- [x] 更新 DeviceController（Service 层 + DTO + ApiResponse）
- [x] mvn clean compile 通过（79 files）
- [x] Git commit

## 编码时长
2.5 小时

## 项目包结构
```
dev.reboot
├── IndustrialAiHubApplication      (@SpringBootApplication)
├── controller/
│   └── DeviceController            (/api/devices CRUD)
├── service/
│   ├── DeviceService
│   ├── UserService
│   └── AlarmService
├── mapper/
│   ├── DeviceMapper
│   ├── UserMapper
│   ├── RoleMapper
│   ├── DeviceDataMapper
│   ├── AlarmMapper
│   └── OperationLogMapper
├── entity/
│   ├── Device                      (updated: 10 fields)
│   ├── User
│   ├── Role
│   ├── UserRole
│   ├── DeviceData
│   ├── Alarm
│   └── OperationLog
├── dto/
│   ├── DeviceDTO
│   ├── LoginDTO
│   └── ApiResponse<T>              (统一响应封装)
└── config/
    └── CorsConfig
```

## 数据库表（7 张）
| 表 | 说明 | 关键字段 |
|----|------|---------|
| user | 用户 | username, password(BCrypt), email, status |
| role | 角色 | role_name, role_code(ADMIN/OPERATOR/VIEWER) |
| user_role | 用户-角色关联 | user_id, role_id |
| device | 设备 | device_name, device_code(UNIQUE), device_type, status |
| device_data | 设备数据 | device_id, data_type, data_value, unit, recorded_at |
| alarm | 告警 | device_id, alarm_type, alarm_level(1-3), status |
| operation_log | 操作日志 | user_id, operation_type, target_type:target_id |

## 明日计划
- Day 23: JWT 工具类 + 登录/注册接口 + BCrypt + Postman 测试
