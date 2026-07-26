# Day 28 — 2026-07-26（第 4 周复盘）

## 今日目标
- [x] 架构审计 V1.2 全部 9 项修复完毕
- [x] A7: LoginDTO → LoginRequest + RegisterRequest 语义分离
- [x] A3: AlarmService.createAlarm() 接入 AlarmMapper.insert()
- [x] A8: UserService.changePassword() 接入 UserMapper.updatePassword()
- [x] A9: DeviceService.listByType() 接入 DeviceMapper.findByType()
- [x] A5+A6: DeviceDataService+Controller + OperationLogService+Controller
- [x] A4: AlarmController（5 个端点）
- [x] Week04.md 周复盘
- [x] mvn clean compile 通过（104 files）
- [x] Git commit

## 审计修复完成

| # | 严重度 | 问题 | 修复 |
|---|:---:|------|------|
| A1 | P1 | Service 无 BusinessException | Day 27 ✅ |
| A2 | P1 | DeviceService unused import | Day 27 ✅ |
| A3 | P1 | AlarmMapper.insert() 死代码 | 接入 AlarmService.createAlarm() |
| A4 | P2 | 无 AlarmController | 新建 5 端点 |
| A5 | P2 | 无 DeviceDataController/Service | 新建 2 端点 |
| A6 | P2 | 无 OperationLogController/Service | 新建 1 端点 |
| A7 | P2 | LoginDTO 复用登录注册 | 拆分为 LoginRequest + RegisterRequest |
| A8 | P3 | UserMapper.updatePassword() 死代码 | 接入 UserService.changePassword() |
| A9 | P3 | DeviceMapper.findByType() 死代码 | 接入 DeviceService.listByType() |

## 新增文件（7 files）
```
dto/
├── LoginRequest.java              # 登录专用（无 @Size）
└── RegisterRequest.java           # 注册专用（含 @Size）

controller/
├── AlarmController.java           # 5 endpoints
├── DeviceDataController.java      # 2 endpoints
└── OperationLogController.java    # 1 endpoint

service/
├── DeviceDataService.java
└── OperationLogService.java

REVIEW/
└── Week04.md                      # 第 4 周复盘
```

## API 端点扩展

| 方法 | 路径 | 鉴权 | 新增 |
|------|------|------|:--:|
| GET | /api/alarms | VIEWER+ | ✅ |
| GET | /api/alarms/device/{id} | VIEWER+ | ✅ |
| GET | /api/alarms/status/{s} | VIEWER+ | ✅ |
| PUT | /api/alarms/{id}/acknowledge | OPERATOR+ | ✅ |
| PUT | /api/alarms/{id}/resolve | OPERATOR+ | ✅ |
| GET | /api/device-data/device/{id} | VIEWER+ | ✅ |
| GET | /api/device-data/device/{id}/latest | VIEWER+ | ✅ |
| GET | /api/operation-logs | ADMIN | ✅ |

> 总端点：12 → 20

---

## 明日计划
- Day 29: 设备数据模块完善（图表 API + 聚合查询）
