# Day 37 — 2026-07-30

## 今日目标
- [x] 接口限流 — Guava RateLimiter 实现
- [x] SQL 索引审计 — 7 张表索引覆盖率检查
- [x] Postman 集合更新 — 补充设备搜索测试用例
- [x] 全量测试回归 — 35 tests, 0 failures
- [ ] Git commit & push

---

## 任务一：SQL 索引审计

### 现状分析

| 表 | 现有索引 | 常驻 WHERE | 评估 |
|----|---------|-----------|:---:|
| `user` | PK(id), uk_username, uk_email, idx_is_deleted | is_deleted=0, id=?, username=? | ✅ 全覆盖 |
| `role` | PK(id), uk_role_code | id=?, role_code=? | ✅ 全覆盖 |
| `user_role` | PK(id), uk_user_role, idx_user_id, idx_role_id | user_id=?, role_id=? | ✅ 全覆盖 |
| `device` | PK(id), uk_device_code, idx_device_type, idx_status, idx_is_deleted | is_deleted=0, device_code=?, device_type=?, status=? | ✅ 全覆盖 |
| `device_data` | PK(id), idx_device_id, idx_recorded_at, idx_device_type_time(device_id, data_type, recorded_at) | device_id=?, recorded_at range, data_type=? | ✅ 覆盖 time-range 查询 |
| `alarm` | PK(id), idx_device_id, idx_alarm_level, idx_status, idx_triggered_at | device_id=?, status=?, triggered_at ORDER BY | ✅ 覆盖 |
| `operation_log` | PK(id), idx_user_id, idx_operation_type, idx_created_at | user_id=?, created_at ORDER BY | ✅ 覆盖 |

### 结论

**7 张表索引布局合理，无需新增索引。** 复合索引 `idx_device_type_time` 覆盖了最频繁的 time-range + data_type 组合查询。当前阶段数据量小，全表扫描也不成问题。

---

## 任务二：接口限流

### 实现方案

- **Guava RateLimiter** — `com.google.guava:guava:33.4.0-jre`（Day 37 新增依赖）
- **RateLimitInterceptor** — `dev.reboot.security.RateLimitInterceptor`
- **按 URI 路径独立限流**：参数化路径（如 `/api/devices/{id}`）共用同一令牌桶，避免 ID 变化导致限流失效
- **默认 50 req/s**，可通过 `-Drate.limit.permits=100` 配置
- **返回 HTTP 429** + `ApiResponse.error(429, "请求过于频繁")`
- **执行顺序**：JWT Filter → RateLimit(0) → AuthInterceptor(1) → Controller

### 新增文件

| 文件 | 说明 |
|------|------|
| `security/RateLimitInterceptor.java` | 限流拦截器（含 Guava RateLimiter） |
| `config/WebMvcConfig.java` | 新增 order(0) 注册 |

---

## 任务三：Postman 更新

### 03-Device 新增 5 个测试用例

| 测试名称 | 参数 |
|---------|------|
| 搜索 - 按关键字（泵） | `keyword=泵` |
| 搜索 - 按设备类型（SENSOR） | `deviceType=SENSOR` |
| 搜索 - 按状态（在线） | `status=1` |
| 搜索 - 复合条件 | `keyword=泵&deviceType=泵&status=1` |
| 搜索 - 关键字无匹配 | `keyword=X_NOT_EXIST` |

---

## 项目当前状态

| 维度 | Day 36 | Day 37 |
|------|:---:|:---:|
| 后端 Java | 55 files | 57 files (+2) |
| 测试用例 | 35 | 35 (回归) |
| Postman | 42 cases | 47 cases (+5) |
| 中间件依赖 | 7 | 8 (+Guava) |

---

## 明日计划
- Day 38: 完善前端 — 表单校验、loading 状态、错误提示
