# Architecture Consistency Report V1.2

**Audit Date:** 2026-07-26  
**Commit Range:** a8d36b5 → HEAD  
**Result:** ⚠️ 0 Critical · 3 Major · 4 Moderate · 2 Low  
**mvn compile:** BUILD SUCCESS (94 source files)

---

## 1. 总体结论

| 维度 | 评估 | 说明 |
|------|:---:|------|
| 认证链路 | ✅ PASS | JwtAuthFilter → AuthInterceptor → Controller，分离清晰 |
| 异常处理框架 | ⚠️ WARNING | BusinessException + ErrorCode + GlobalExceptionHandler 基础设施完备，但未被 Service 层调用 |
| 实体-数据库映射 | ✅ PASS | 7 Entity 全部与 DB 列完全对应，BigDecimal/DateTime 类型正确 |
| 软删除一致性 | ✅ PASS | Device + User 均使用 is_deleted |
| API 响应格式 | ✅ PASS | 所有端点统一返回 ApiResponse<T> |
| CHECK 约束 | ✅ PASS | 8 个 CHECK 覆盖 device_type/status/alarm_level/alarm_status/data_type/operation_type/target_type/user_status |
| Controller 覆盖率 | ⚠️ WARNING | 3 个 Controller（Auth/Device/User），Alarm/DeviceData/OperationLog 无 API 端点 |

---

## 2. 缺陷清单

### P1 — Major (3)

| # | 位置 | 缺陷 | 影响 |
|---|------|------|------|
| A1 | Service 层全局 | **BusinessException 基础设施未接入** — ErrorCode 枚举 + BusinessException 类 + GlobalExceptionHandler 完整存在，但 AuthService/DeviceService/UserService/AlarmService 无一个方法抛出 BusinessException。所有 Service 仍然用 null 返回表示失败，Controller 手动写 `ApiResponse.error()` | 异常处理框架形同虚设；每个 Controller 手工重复错误码和消息 |
| A2 | DeviceService.java L3 | **未使用的 import**: `import dev.reboot.dto.ApiResponse;` | 编译无影响，代码清洁度下降 |
| A3 | AlarmMapper.java L23-26 | **`insert()` 无调用方** — Mapper 定义了 insert，但 AlarmService 没有 create 方法 | 死代码 |

### P2 — Moderate (4)

| # | 位置 | 缺陷 | 影响 |
|---|------|------|------|
| A4 | AlarmService | **无 AlarmController** — AlarmService 已实现 listAll/listByDevice/listByStatus/acknowledge/resolve，但没有任何 REST 端点暴露 | 告警数据无法通过 API 访问 |
| A5 | DeviceDataMapper | **无 DeviceDataController/Service** — device_data 表有 Mapper 但无 Service 和 Controller | 设备数据无法通过 API 访问 |
| A6 | OperationLogMapper | **无 OperationLogController/Service** — operation_log 表有 Mapper 但无 Service 和 Controller | 操作日志无法通过 API 访问 |
| A7 | LoginDTO.java | **LoginDTO 复用登录和注册** — `@Size(min=6)` 对登录也生效。虽不致命但语义不清晰 | /api/auth/login 接受密码校验与注册相同 |

### P3 — Low (2)

| # | 位置 | 缺陷 | 影响 |
|---|------|------|------|
| A8 | UserMapper.java L32 | **`updatePassword()` 无调用方** — UserService 无密码修改方法，仅 SecurityConfig 入口可变更密码 | 死代码 |
| A9 | DeviceMapper.java L37 | **`findByType()` 无调用方** — DeviceService 未暴露按类型查询方法 | 死代码 |

---

## 3. 通过项（无需修改）

### 认证链路
```
HTTP Request
  → JwtAuthFilter (解析 Bearer token → request.setAttribute)
  → AuthInterceptor (读 @RequireRole → 放行/401/403)
  → Controller
```
- /api/auth/** 正确排除在拦截器外
- JwtAuthFilter 不阻断未登录请求（由 Interceptor 判断）
- @RequireRole 方法级优先于类级

### 实体-DB 映射验证
7 张表 vs 7 个 Entity，全部字段一一对应：

| 表 | Entity | 特殊类型 |
|----|--------|---------|
| user | User | Integer isDeleted ✅ |
| role | Role | ✅ |
| user_role | UserRole | ✅ |
| device | Device | Integer isDeleted ✅ |
| device_data | DeviceData | BigDecimal dataValue → DECIMAL(18,6) ✅ |
| alarm | Alarm | Integer alarmLevel → TINYINT ✅ |
| operation_log | OperationLog | ✅ |

### 数据库约束（8 CHECK）
```
chk_alarm_level     alarm_level IN (1,2,3)
chk_alarm_status    status IN (0,1,2)
chk_data_type       data_type IN (TEMPERATURE,PRESSURE,SPEED,HUMIDITY,CURRENT)
chk_device_status   status IN (0,1,2)
chk_device_type     device_type IN (PLC,SENSOR,CAMERA,ROBOT,OTHER)
chk_operation_type  operation_type IN (CREATE,UPDATE,DELETE,LOGIN,EXPORT)
chk_target_type     target_type IN (USER,DEVICE,ALARM,ROLE)
chk_user_status     status IN (0,1)
```

### API 端点现状

| 方法 | 路径 | 鉴权 | 状态 |
|------|------|------|:--:|
| POST | /api/auth/login | 公开 | ✅ |
| POST | /api/auth/register | 公开 | ✅ |
| GET | /api/devices | VIEWER+ | ✅ |
| GET | /api/devices/{id} | VIEWER+ | ✅ |
| POST | /api/devices | OPERATOR+ | ✅ |
| PUT | /api/devices/{id} | OPERATOR+ | ✅ |
| DELETE | /api/devices/{id} | ADMIN | ✅ |
| GET | /api/users | ADMIN | ✅ |
| GET | /api/users/{id} | ADMIN | ✅ |
| PUT | /api/users/{id} | ADMIN | ✅ |
| PUT | /api/users/{id}/status | ADMIN | ✅ |
| DELETE | /api/users/{id} | ADMIN | ✅ |
| — | /api/alarms/* | — | ❌ 不存在 |
| — | /api/device-data/* | — | ❌ 不存在 |
| — | /api/operation-logs/* | — | ❌ 不存在 |

---

## 4. 建议优先级

1. **A1 (P1)**: Service 层接入 BusinessException — AuthService/DeviceService/UserService 改 null→throw，Controller 删除手工 error 处理
2. **A2 (P1)**: 删除 DeviceService 未使用的 ApiResponse import
3. **A4/A5/A6 (P2)**: 补充 AlarmController / DeviceDataController / OperationLogController — 这 3 张表已有完整数据层，只缺 REST 暴露
4. **A3/A8/A9 (P3)**: 清理死代码或补全功能（insert→create / updatePassword→changePassword / findByType→Service 暴露）

---

> **对比 V1.0 审计**：V1.0 发现的配置漂移、文档滞后、空三层架构等问题已在 V1.1 全部修复。本次 V1.2 审计发现的新问题集中在**异常处理框架未接入**和**API 层覆盖不全**，属于架构生长过程中的正常 gap。
