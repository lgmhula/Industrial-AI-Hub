# Day 32 — 2026-07-28

## 今日目标
- [x] 报警规则定义（8 条默认规则：温度/压力/转速/湿度上下限）
- [x] 数据上报时自动检测是否触发报警（AlarmDetector 引擎）
- [x] 报警记录生成（触发后自动持久化）
- [x] 报警列表分页查询（全部 / 按设备 / 按状态）
- [x] 报警处理：确认 / 解决
- [x] mvn compile 通过（109 files）
- [ ] Git commit

## 新增文件

```
dev/reboot/rule/
├── AlarmRule.java           # 报警规则 POJO: dataType/operator/threshold/level/type/message
└── AlarmRuleConfig.java     # @Configuration: 8 条默认规则

dev/reboot/service/
└── AlarmDetector.java       # 规则检测引擎: check(deviceId, type, value) → List<AlarmVO>
```

## 修改文件

| 文件 | 变更 |
|------|------|
| `DeviceDataService.java` | report() 注入 AlarmDetector，数据上报后自动检测 |
| `AlarmMapper.java` | +5 分页查询方法 + 3 计数方法 |
| `AlarmService.java` | +3 分页查询方法（listAllPaged/listByDevicePaged/listByStatusPaged） |
| `AlarmController.java` | 默认端点改为分页，新增 /all 兼容旧调用，acknowledge/resolve 增加失败处理 |

## 报警规则表

| # | 数据类型 | 条件 | 阈值 | 等级 | 告警类型 |
|:---:|------|:---:|------|:---:|------|
| 1 | TEMPERATURE | GT > | 40.0°C | 2 重要 | OVER_TEMP |
| 2 | TEMPERATURE | LT < | 0.0°C | 2 重要 | UNDER_TEMP |
| 3 | PRESSURE | GT > | 110.0kPa | 1 一般 | OVER_PRESSURE |
| 4 | PRESSURE | LT < | 90.0kPa | 2 重要 | UNDER_PRESSURE |
| 5 | SPEED | GT > | 3000RPM | 3 紧急 | OVER_SPEED |
| 6 | SPEED | LT < | 100RPM | 2 重要 | UNDER_SPEED |
| 7 | HUMIDITY | GT > | 90% | 1 一般 | OVER_HUMIDITY |
| 8 | HUMIDITY | LT < | 10% | 1 一般 | UNDER_HUMIDITY |

## 架构流程

```
POST /api/device-data/device/{id}
  → DeviceDataService.report()
    → deviceDataMapper.insert(data)     // 持久化
    → alarmDetector.check(id, type, val) // 规则评估
      → for each AlarmRule:
          match dataType → compare value vs threshold
          └─ matched → alarmService.createAlarm() → INSERT alarm
```

## API 端点

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|:---:|
| GET | /api/alarms?page=1&size=10 | 分页查询全部 | VIEWER+ |
| GET | /api/alarms/device/{id}?page=1&size=10 | 按设备分页 | VIEWER+ |
| GET | /api/alarms/status/{status}?page=1&size=10 | 按状态分页 | VIEWER+ |
| GET | /api/alarms/all | 全量（不分页） | VIEWER+ |
| PUT | /api/alarms/{id}/acknowledge | 确认 | OPERATOR+ |
| PUT | /api/alarms/{id}/resolve | 解决 | OPERATOR+ |

## 验证方法

```bash
# 上报触发报警的温度数据
curl -X POST http://localhost:8080/api/device-data/device/1 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"dataType":"TEMPERATURE","dataValue":42.5,"unit":"°C"}'

# 查看触发的报警
curl http://localhost:8080/api/alarms?page=1&size=10 \
  -H "Authorization: Bearer <token>"
```

## 代码统计
- 新增：3 files
- 修改：4 files
- 编译：109 files

---

## 明日计划
- Day 33: 操作日志模块 — AOP 切面自动记录操作日志
