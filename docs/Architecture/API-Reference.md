# Industrial AI Hub — API 接口清单

> Base URL: `http://localhost:8080/api` | Auth: `Bearer {token}` | Knife4j: `/doc.html`

## 接口总览

| 分组 | 端点数 | 需要认证 | 权限要求 |
|------|:---:|:---:|------|
| Auth（认证） | 2 | 否 | — |
| User（用户管理） | 5 | 是 | ADMIN |
| Device（设备管理） | 5 | 是 | VIEWER+ |
| DeviceData（设备数据） | 5 | 是 | VIEWER+ |
| Alarm（报警管理） | 6 | 是 | VIEWER+ |
| OperationLog（操作日志） | 3 | 是 | ADMIN |
| **合计** | **26** | | |

---

## 01 — Auth（认证）

| 方法 | 路径 | 说明 | 认证 |
|:---:|------|------|:---:|
| POST | `/api/auth/login` | 登录，返回 JWT Token | 否 |
| POST | `/api/auth/register` | 注册，分配 VIEWER 角色 | 否 |

### POST /api/auth/login
```
Request:  { "username": "admin", "password": "admin123" }
Response: { "code": 200, "message": "登录成功", "data": "eyJ..." }
Errors:   401 用户名或密码错误 | 403 账户已禁用
```

### POST /api/auth/register
```
Request:  { "username": "newuser", "password": "p@ssw0rd" }
Response: { "code": 200, "message": "注册成功", "data": { UserVO } }
Errors:   409 用户名已存在
```

---

## 02 — User（用户管理）— ADMIN only

| 方法 | 路径 | 说明 |
|:---:|------|------|
| GET | `/api/users` | 分页查询 `?page=1&size=10` |
| GET | `/api/users/{id}` | 按 ID 查询 |
| PUT | `/api/users/{id}` | 编辑 email/phone |
| PUT | `/api/users/{id}/status` | 切换启用/禁用 |
| DELETE | `/api/users/{id}` | 逻辑删除 |

---

## 03 — Device（设备管理）

| 方法 | 路径 | 说明 |
|:---:|------|------|
| GET | `/api/devices` | 分页搜索 `?keyword=&deviceType=&status=&page=1&size=10` |
| GET | `/api/devices/{id}` | 按 ID 查询 |
| POST | `/api/devices` | 创建设备 (OPERATOR+) |
| PUT | `/api/devices/{id}` | 更新设备 (OPERATOR+) |
| DELETE | `/api/devices/{id}` | 逻辑删除 (ADMIN) |

### POST /api/devices
```
Request:  { "deviceName": "泵A", "deviceCode": "PUMP-001",
            "deviceType": "泵", "status": 1,
            "ipAddress": "192.168.1.100", "port": 8080,
            "location": "1号车间" }
Response: { "code": 200, "data": { DeviceVO } }
Errors:   409 设备编码已存在 | 400 参数校验失败
```

---

## 04 — DeviceData（设备数据）

| 方法 | 路径 | 说明 |
|:---:|------|------|
| POST | `/api/device-data/device/{deviceId}` | 上报数据 (触发报警检测) |
| GET | `/api/device-data/device/{deviceId}` | 查询所有数据 |
| GET | `/api/device-data/device/{deviceId}/range` | 时间范围查询 `?dataType=&startTime=&endTime=` |
| GET | `/api/device-data/device/{deviceId}/latest` | 最新一条 `?dataType=TEMPERATURE` |
| GET | `/api/device-data/device/{deviceId}/stats` | 聚合统计 `?dataType=TEMPERATURE&startTime=&endTime=` |

### POST /api/device-data/device/{deviceId}
```
Request:  { "dataType": "TEMPERATURE", "dataValue": 45.5, "unit": "°C" }
Response: { "code": 200, "data": { DeviceData } }
Side:     AlarmDetector 自动检测 → 触发报警入库
```

---

## 05 — Alarm（报警管理）

| 方法 | 路径 | 说明 |
|:---:|------|------|
| GET | `/api/alarms` | 分页查询 `?page=1&size=10` |
| GET | `/api/alarms/device/{deviceId}` | 按设备查询 |
| GET | `/api/alarms/status/{status}` | 按状态查询 (0/1/2) |
| GET | `/api/alarms/all` | 全部（不分页） |
| PUT | `/api/alarms/{id}/acknowledge` | 确认告警 |
| PUT | `/api/alarms/{id}/resolve` | 解决告警 |

### 报警规则引擎（8 条规则）

| 数据类型 | 条件 | 阈值 | 告警类型 | 等级 |
|---------|------|------|---------|:---:|
| TEMPERATURE | > | 40.0°C | OVER_TEMP | 2 |
| TEMPERATURE | < | 0.0°C | UNDER_TEMP | 2 |
| PRESSURE | > | 110.0 kPa | OVER_PRESSURE | 1 |
| PRESSURE | < | 90.0 kPa | UNDER_PRESSURE | 2 |
| SPEED | > | 3000 RPM | OVER_SPEED | 3 |
| SPEED | < | 100 RPM | UNDER_SPEED | 2 |
| HUMIDITY | > | 90% | OVER_HUMIDITY | 1 |
| HUMIDITY | < | 10% | UNDER_HUMIDITY | 1 |

---

## 06 — OperationLog（操作日志）— ADMIN only

| 方法 | 路径 | 说明 |
|:---:|------|------|
| GET | `/api/operation-logs` | 分页查询 `?page=1&size=20` |
| GET | `/api/operation-logs/user/{userId}` | 按用户查询 |
| GET | `/api/operation-logs/recent` | 最近 20 条 |

---

## 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": { }
}
```

| HTTP Status | code | 说明 |
|:---:|:---:|------|
| 200 | 200 | 成功 |
| 400 | 400 | 参数校验失败 |
| 401 | 401 | 未认证 / 密码错误 |
| 403 | 403 | 权限不足 / 账户禁用 |
| 404 | 404 | 资源不存在 |
| 409 | 409 | 资源冲突（重复） |
| 429 | 429 | 请求过于频繁 |
| 500 | 500 | 服务器内部错误 |

---

## 请求头

```
Authorization: Bearer {jwt_token}
Content-Type: application/json
```

## 分页参数

所有分页接口统一：

| 参数 | 默认值 | 校验 |
|------|:---:|------|
| `page` | 1 | `@Min(1)` |
| `size` | 10/20 | `@Min(1) @Max(100)` |

响应格式：
```json
{
  "code": 200,
  "data": {
    "list": [...],
    "pageNum": 1,
    "pageSize": 10,
    "total": 50,
    "pages": 5
  }
}
```
