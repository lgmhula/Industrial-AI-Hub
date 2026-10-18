# Industrial AI Hub — 数据库 ER 图

> 数据库：`reboot` | 字符集：utf8mb4 | 引擎：InnoDB
> 版本：V2.3 | 更新：2026-10-18 | 当前 Schema：V16
> 迁移链：V1 基线 → V3 CHECK 扩展 → V4 站点授权 → V5 用户安全状态 → V6 登录审计 → V7 alarm/role 审计字段 → V8 admin 密码更新 → V9-V15 AI/RAG/MCP/巡检/SSE 审计类型扩展 → V16 活动告警聚合

> **说明**：下方 ER Diagram 以当前 V16 主动字段为主；历史迁移与 V16 专属索引见 [§ 迁移演进](#迁移演进) 和 [§ V16 活动告警聚合](#v16-活动告警聚合adr-0038)。

## ER Diagram

```mermaid
erDiagram
    user ||--o{ user_role : has
    role ||--o{ user_role : assigned
    user ||--o{ operation_log : performs
    device ||--o{ device_data : generates
    device ||--o{ alarm : triggers
    site ||--o{ device : contains
    user ||--o{ user_site : authorized
    site ||--o{ user_site : grants
    role ||--o{ user_site : scopes
    user ||--o{ login_audit : attempts

    user {
        BIGINT  id           PK "主键"
        VARCHAR username     UK "用户名"
        VARCHAR password        "BCrypt 加密"
        VARCHAR email        UK "邮箱"
        VARCHAR phone           "手机号"
        TINYINT status          "1-启用 0-禁用"
        TINYINT is_deleted      "逻辑删除"
        INT failed_attempts     "连续登录失败次数"
        DATETIME locked_until   "锁定截止时间"
        DATETIME password_changed_at "最近改密时间"
        DATETIME created_at     "创建时间"
        DATETIME updated_at     "更新时间"
    }

    role {
        BIGINT  id           PK "主键"
        VARCHAR role_name       "角色名称"
        VARCHAR role_code    UK "ADMIN/OPERATOR/VIEWER"
        VARCHAR description     "角色描述"
        TINYINT status          "1-启用 0-禁用"
        TINYINT is_deleted      "逻辑删除"
        DATETIME created_at     "创建时间"
        DATETIME updated_at     "更新时间"
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
        BIGINT site_id          FK "站点 ID"
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
        DATETIME acknowledged_at "确认时间"
        BIGINT acknowledged_by   "确认人 ID"
        DATETIME resolved_at     "解决时间"
        BIGINT resolved_by       "解决人 ID"
        INT repeat_count         "活动周期重复次数"
        DATETIME last_triggered_at "最近触发时间"
        VARCHAR active_alarm_key UK "活动告警生成列，解决后 NULL"
        DATETIME created_at      "创建时间"
        DATETIME updated_at      "更新时间"
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

    site {
        BIGINT  id           PK "主键"
        VARCHAR site_name       "站点名称"
        VARCHAR site_code    UK "站点编码"
        VARCHAR description     "站点描述"
        VARCHAR address         "站点地址"
        DATETIME created_at     "创建时间"
        DATETIME updated_at     "更新时间"
    }

    user_site {
        BIGINT id       PK "主键"
        BIGINT user_id  FK "用户 ID"
        BIGINT site_id  FK "站点 ID"
        BIGINT role_id  FK "站点内角色 ID"
    }

    login_audit {
        BIGINT id           PK "主键"
        BIGINT user_id      FK "用户 ID，可为空"
        VARCHAR username       "尝试登录用户名"
        TINYINT success        "1-成功 0-失败"
        VARCHAR ip_address     "客户端 IP"
        VARCHAR user_agent     "User-Agent"
        VARCHAR reason         "服务端结果原因"
        DATETIME created_at     "审计时间"
    }
```

## 索引设计

| 表 | 索引 | 类型 | 覆盖查询 |
|----|------|:---:|------|
| `user` | `PRIMARY(id)` | 聚簇 | findById |
| | `uk_username` | 唯一 | findByUsername |
| | `uk_email` | 唯一 | — |
| | `idx_is_deleted` | 普通 | is_deleted=0 |
| `role` | `PRIMARY(id)` | 聚簇 | findById |
| | `uk_role_code_deleted` | 唯一 | role_code + is_deleted |
| | `idx_role_status` | 普通 | status=? |
| `user_role` | `PRIMARY(id)` | 聚簇 | findById |
| | `uk_user_role` | 唯一 | user_id + role_id |
| | `idx_user_id` | 普通 | user_id=? |
| | `idx_role_id` | 普通 | role_id=? |
| `site` | `PRIMARY(id)` | 聚簇 | findById |
| | `uk_site_code` | 唯一 | site_code=? |
| `user_site` | `PRIMARY(id)` | 聚簇 | findById |
| | `uk_user_site` | 唯一 | user_id + site_id |
| | `idx_user_id` | 普通 | user_id=? |
| | `idx_site_id` | 普通 | site_id=? |
| `device` | `PRIMARY(id)` | 聚簇 | findById |
| | `uk_device_code_deleted` | 唯一 | device_code + is_deleted |
| | `idx_device_type` | 普通 | findByType |
| | `idx_status` | 普通 | status=? |
| | `idx_is_deleted` | 普通 | is_deleted=0 |
| | `idx_device_site_id` | 普通 | site_id=? |
| `device_data` | `PRIMARY(id)` | 聚簇 | — |
| | `idx_device_id` | 普通 | device_id=? |
| | `idx_recorded_at` | 普通 | ORDER BY recorded_at |
| | `idx_device_type_time` | 复合 | device_id+data_type+recorded_at |
| `alarm` | `PRIMARY(id)` | 聚簇 | — |
| | `idx_device_id` | 普通 | device_id=? |
| | `idx_alarm_level` | 普通 | alarm_level=? |
| | `idx_status` | 普通 | status=? |
| | `idx_triggered_at` | 普通 | ORDER BY triggered_at |
| | `idx_alarm_acknowledged_by` | 普通 | acknowledged_by=? |
| | `uk_alarm_active_device_type` | 唯一 | 活动告警按 device_id+alarm_type 合并 |
| | `idx_alarm_last_triggered_at` | 普通 | 按最近触发时间排序 |
| `operation_log` | `PRIMARY(id)` | 聚簇 | — |
| | `idx_user_id` | 普通 | userId=? |
| | `idx_operation_type` | 普通 | — |
| | `idx_created_at` | 普通 | ORDER BY created_at |
| `login_audit` | `PRIMARY(id)` | 聚簇 | — |
| | `idx_login_audit_user_time` | 复合 | user_id + created_at |
| | `idx_login_audit_created_time` | 普通 | ORDER BY created_at |

## 表规模

| 表 | 列数 | 约束(CHECK) | 索引 |
|----|:---:|:---:|:---:|
| `user` | 12 | 1 (status) | 4 |
| `role` | 8 | 0 | 3 |
| `user_role` | 3 | 0 | 4 |
| `site` | 7 | 0 | 2 |
| `user_site` | 4 | 0 | 4 |
| `device` | 12 | 2 (status, device_type) | 6 |
| `device_data` | 7 | 1 (data_type) | 4 |
| `alarm` | 16 | 2 (level, status) | 8 |
| `operation_log` | 8 | 2 (operation_type, target_type) | 4 |
| `login_audit` | 8 | 0 | 3 |
| **合计** | **85** | **8** | **42** |

## 迁移演进

> 以 `backend/src/main/resources/db/migration/` 为唯一事实源；V2 已退役，当前活动链为 V1 + V3-V16。

### V4 站点授权（[ADR 0020](../decision-log/0020-site-authorization.md)）

| 新增表 | 关键字段 |
|--------|---------|
| `site` | id / site_code / site_name / description / created_at / updated_at |
| `user_site` | id / user_id / site_id / role_id / created_at |

| 表 | 新增字段 |
|----|---------|
| `device` | `site_id` BIGINT（DEFAULT 1，归属默认站点） |

### V5 用户安全状态

| 表 | 新增字段 |
|----|---------|
| `user` | `failed_attempts` INT（连续登录失败计数） |
| `user` | `locked_until` DATETIME（锁定截止时间） |
| `user` | `password_changed_at` DATETIME（最近改密时间） |

### V6 登录审计

| 新增表 | 关键字段 |
|--------|---------|
| `login_audit` | id / user_id / login_time / ip / user_agent / success / failure_reason / created_at |

### V7 alarm/role 审计字段

| 表 | 新增字段 |
|----|---------|
| `alarm` | `acknowledged_at` / `acknowledged_by` / `resolved_by` / `updated_at` |
| `role` | `status` TINYINT / `is_deleted` TINYINT / `updated_at` |
| `device` | 唯一约束由 `uk_device_code` 改为 `uk_device_code_deleted (device_code, is_deleted)`（支持软删后复用编码） |

### V8 admin 密码更新

仅数据变更，无结构变更。

### V9-V15 审计枚举扩展

| 版本 | 结构变更 |
|------|----------|
| V9 | `chk_operation_type` 增加 `CHAT/SUMMARY/DIAGNOSE`，`chk_target_type` 增加 `AI` |
| V10 | `chk_operation_type` 增加 `FUNCTION_CALL` |
| V11 | `chk_operation_type` 增加 `INGEST`，`chk_target_type` 增加 `KNOWLEDGE` |
| V12 | `chk_operation_type` 增加 `MCP_SMOKE`，`chk_target_type` 增加 `MCP` |
| V13 | `chk_operation_type` 增加 `INSPECTION` |
| V14 | `chk_operation_type` 增加 `PUSH`，`chk_target_type` 增加 `SSE` |
| V15 | `chk_operation_type` 增加 `AUTO_ALARM` |

### V16 活动告警聚合（[ADR 0038](../decision-log/0038-active-alarm-aggregation.md)）

| 变更 | 说明 |
|------|------|
| `alarm.repeat_count` | `INT NOT NULL DEFAULT 1`，活动周期内重复触发次数 |
| `alarm.last_triggered_at` | `DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP`，最近触发时间 |
| `alarm.active_alarm_key` | `VARCHAR(96) STORED GENERATED`；`status IN (0,1)` 时为 `device_id:alarm_type`，解决后为 `NULL` |
| `uk_alarm_active_device_type` | `active_alarm_key` 唯一索引，由数据库兜底并发合并 |
| `idx_alarm_last_triggered_at` | 支持活动告警按最近触发时间查询 |

V16 执行时会先归并存量活动重复项：保留最早 ID，其余标记为已解决并保留历史记录，然后创建唯一索引。解决后唯一键变为 `NULL`，同设备同类型再次触发会开启新活动周期。

## 当前总规模

| 维度 | V1 基线 | V16 当前 |
|------|:------:|:-----------:|
| 表数 | 7 | 10 |
| 列数 | 53 | 85 |
| CHECK 约束 | 8 | 8 |
| 索引 | 27 | 42 |
| 活动迁移 | V1 | V1 + V3-V16（15 个；V2 已退役，见 ADR 0019） |
