# Industrial AI Hub — Phase 3 工程质量审计报告

> **分支**：`fix/phase3-quality-audit`（基于 `main` @ `6095401`）
> **审计日期**：2026-08-23
> **审计范围**：数据库 / 后端架构 / 安全链 / 前端工程化 / 测试体系 / CI/CD / 可观测性 / 文档
> **对标参考**：ThingsBoard / JetLinks / youlai-boot / FastBee

---

## 目录

1. [审计总览](#1-审计总览)
2. [数据库设计缺陷（18 项）](#2-数据库设计缺陷18-项)
3. [RBAC 权限体系缺陷（8 项）](#3-rbac-权限体系缺陷8-项)
4. [后端架构缺陷（10 项）](#4-后端架构缺陷10-项)
5. [前端工程化缺陷（9 项）](#5-前端工程化缺陷9-项)
6. [测试体系缺陷（7 项）](#6-测试体系缺陷7-项)
7. [CI/CD 与可观测性缺陷（5 项）](#7-cicd-与可观测性缺陷5-项)
8. [修复优先级矩阵](#8-修复优先级矩阵)
9. [Phase 3 执行计划（4 周）](#9-phase-3-执行计划4-周)

---

## 1. 审计总览

### 1.1 当前代码基线

main 分支已合并以下安全治理工作：

| 模块 | 状态 | 关键文件 |
|------|------|----------|
| 站点资源授权（BOLA 修复） | ✅ 已合并 | `SiteAccessService` / V4 迁移 / ADR 0020 |
| 用户安全状态 | ✅ 已合并 | V5 迁移（failed_attempts / locked_until / password_changed_at） |
| JWT 生命周期 | ✅ 已合并 | `TokenBlacklistService` / jti / 黑名单 / 用户撤销 / fail-close |
| 登录审计 | ✅ 已合并 | V6 迁移 / `LoginAuditService` / 异步写入 |
| 登录限流 | ✅ 已合并 | `AuthRateLimitService` / IP 限流 + 账户锁定 |
| 注册治理 | ✅ 已合并 | 注册开关 + 邀请码 + 每日配额 |
| 种子数据隔离 | ✅ 已合并 | V2 退役 / `db/seed/dev/` / ADR 0019 |

### 1.2 综合评分

| 维度 | 评分 | 对标 GitHub 优秀项目差距 |
|------|:----:|--------------------------|
| 数据库设计 | 5.5/10 | 缺审计字段 / 缺分区 / 索引冗余 / H2 漂移 |
| RBAC 权限 | 4.0/10 | 角色硬编码 / 无菜单权限 / 无角色 CRUD |
| 后端架构 | 6.5/10 | 异常体系基本可用 / Controller 偏厚 / 无 DTO 分层规范 |
| 前端工程化 | 3.5/10 | 无 ESLint / 无 Pinia / 无测试 / 图标全量导入 |
| 测试体系 | 5.0/10 | 21 文件 180+ 测试 / 无 Testcontainers / 无契约测试 |
| CI/CD | 5.0/10 | CI 完备 / CD 完全缺失 |
| 可观测性 | 2.0/10 | 仅 Health 端点 / 无 Prometheus / 无 Grafana |
| 文档 | 6.0/10 | ADR 齐全 / 无开源级 README / 无架构图导出 |

---

## 2. 数据库设计缺陷（18 项）

### 2.0 当前迁移链

| 版本 | 文件 | 内容 |
|------|------|------|
| V1 | `V1__baseline.sql` | 7 表 + 3 角色 + admin 用户 |
| V3 | `V3__operation_log_check_types.sql` | operation_type CHECK 扩展 |
| V4 | `V4__add_site_scoping.sql` | site / user_site 表 + device.site_id |
| V5 | `V5__add_user_security_status.sql` | user 安全状态字段 |
| V6 | `V6__add_login_audit.sql` | login_audit 表 |

### 2.1 缺陷清单

| # | 缺陷 | 位置 | 严重度 | 修复方案 |
|---|------|------|:------:|----------|
| DB-01 | alarm 表缺 `acknowledged_at` / `acknowledged_by` / `resolved_by` / `updated_at` | V1 alarm 表 | 🔴 P0 | V7 迁移补字段 |
| DB-02 | 唯一约束 `uk_device_code` 与软删除冲突（删除后无法复用编码） | V1 device 表 | 🔴 P0 | V7 修改约束为 `(device_code, is_deleted)` |
| DB-03 | role 表缺 `status` / `is_deleted` / `updated_at`（角色无法禁用/删除） | V1 role 表 | 🔴 P0 | V7 补字段 |
| DB-04 | H2 schema 的 operation_log CHECK 仍为 5 值（V3 已扩展为 7 值） | `schema-h2.sql` | 🟡 P1 | 同步 H2 schema |
| DB-05 | H2 schema 缺 V5 变更（`user.failed_attempts` / `locked_until` / `password_changed_at`） | `schema-h2.sql` | 🟡 P1 | 同步 H2 schema |
| DB-06 | H2 schema 缺 V6 变更（`login_audit` 表） | `schema-h2.sql` | 🟡 P1 | 同步 H2 schema |
| DB-07 | H2 schema 缺 V4 的 `device.idx_device_site_id` 索引 | `schema-h2.sql` | 🟡 P1 | 同步 H2 schema |
| DB-08 | alarm / device_data 无 `site_id`（站点过滤依赖 JOIN device，性能差） | V4 迁移 | 🟡 P1 | V8 迁移补 `site_id` + 冗余维护 |
| DB-09 | operation_log 无 `site_id`（操作日志不归属站点） | V1 operation_log | 🟡 P1 | V8 迁移补 `site_id` |
| DB-10 | 全表无外键约束（依赖应用层维护关联完整性） | V1 全部表 | 🟢 P2 | 评估后决定是否加 FK |
| DB-11 | 全表缺 `created_by` / `updated_by` 审计字段 | V1 全部表 | 🟢 P2 | V9 迁移补审计字段 |
| DB-12 | device_data 无分区 / 无 TTL（数据增长后查询变慢） | V1 device_data | 🟢 P2 | 评估按 `recorded_at` 分区 |
| DB-13 | 冗余索引：`idx_user_id`（被 `uk_user_role` 最左前缀覆盖） | V1 user_role 表 | 🟢 P2 | 删除冗余索引 |
| DB-14 | 低选择性索引：`idx_is_deleted`（仅 0/1 两个值） | V1 user / device | 🟢 P2 | 删除或改为复合索引 |
| DB-15 | `user_site` 缺 `role_id` 单独索引 | V4 user_site | 🟢 P2 | 补索引 |
| DB-16 | alarm 表无 `alarm_type` CHECK 约束（仅注释约束） | V1 alarm 表 | 🟢 P2 | 补 CHECK 或改用字典表 |
| DB-17 | device_data.value 仅 DECIMAL（不支持字符串/布尔类型数据） | V1 device_data | 🟢 P2 | 评估多类型值列方案 |
| DB-18 | seed_demo_data.sql 中 operation_log 使用 LOGIN 类型，但 H2 CHECK 不含该值 | seed + H2 schema | 🟡 P1 | 同步 H2 CHECK |

### 2.2 V7 迁移脚本（草案）

```sql
-- V7__alarm_role_audit_fields.sql

-- 1. alarm 表补审计字段
ALTER TABLE `alarm` ADD COLUMN `acknowledged_at` DATETIME NULL COMMENT '确认时间';
ALTER TABLE `alarm` ADD COLUMN `acknowledged_by` BIGINT NULL COMMENT '确认人 ID';
ALTER TABLE `alarm` ADD COLUMN `resolved_by` BIGINT NULL COMMENT '解决人 ID';
ALTER TABLE `alarm` ADD COLUMN `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';
ALTER TABLE `alarm` ADD INDEX `idx_alarm_acknowledged_by` (`acknowledged_by`);

-- 2. device 唯一约束修复（支持软删除后复用编码）
ALTER TABLE `device` DROP INDEX `uk_device_code`;
ALTER TABLE `device` ADD UNIQUE KEY `uk_device_code_deleted` (`device_code`, `is_deleted`);

-- 3. role 表补管理字段
ALTER TABLE `role` ADD COLUMN `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-启用 0-禁用';
ALTER TABLE `role` ADD COLUMN `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除';
ALTER TABLE `role` ADD COLUMN `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';
ALTER TABLE `role` ADD INDEX `idx_role_status` (`status`);
```

---

## 3. RBAC 权限体系缺陷（8 项）

### 3.1 当前 RBAC 实现

```
用户 → user_role → role（3 个硬编码角色）
                    ↓
              RoleEnum（ADMIN=1, OPERATOR=2, VIEWER=3）
                    ↓
              @RequireRole 注解 + AuthInterceptor
```

**已具备**：站点级资源授权（`SiteAccessService`）、接口级角色校验（`@RequireRole`）。

**缺失**：角色管理 CRUD、菜单权限、按钮权限、数据权限。

### 3.2 缺陷清单

| # | 缺陷 | 严重度 | 修复方案 | 参考项目 |
|---|------|:------:|----------|----------|
| RBAC-01 | 角色硬编码在 `RoleEnum`，无法动态新增角色 | 🔴 P0 | 角色管理 API（CRUD） | youlai-boot |
| RBAC-02 | 无角色 CRUD 接口（`RoleMapper` 只有 findAll/findById/findByCode） | 🔴 P0 | `RoleController` + `RoleService` | youlai-boot |
| RBAC-03 | 无用户角色分配/取消 API（`UserRoleMapper` 只有 insert/findByUserId/deleteByUserId） | 🔴 P0 | `UserController` 补角色分配接口 | youlai-boot |
| RBAC-04 | 无菜单权限表（前端路由/按钮无法按角色控制） | 🟡 P1 | `sys_menu` 表 + 后端路由下发 | youlai-boot |
| RBAC-05 | 无按钮级权限标识（如 `sys:user:create`） | 🟡 P1 | `perm` 字段 + `@HasPermission` 注解 | youlai-boot |
| RBAC-06 | 无数据权限（`data_scope`，用户只能看自己的数据） | 🟡 P1 | 5 级 data_scope | youlai-boot |
| RBAC-07 | 管理员创建用户接口缺失（注册在 AuthController，非后台功能） | 🟡 P1 | `UserController` 补 create 接口 | — |
| RBAC-08 | 密码重置接口缺失（changePassword 依赖旧密码） | 🟡 P1 | `UserController` 补 admin-reset-password | — |

### 3.3 角色管理 API 设计（草案）

```
POST   /api/roles              创建角色（ADMIN）
PUT    /api/roles/{id}          更新角色
DELETE /api/roles/{id}          删除角色（逻辑删除）
GET    /api/roles               角色列表
GET    /api/roles/{id}          角色详情

POST   /api/users/{id}/roles    分配角色
DELETE /api/users/{id}/roles/{roleId}  取消角色
GET    /api/users/{id}/roles    用户角色列表

PUT    /api/users/{id}/password 管理员重置密码
POST   /api/users               管理员创建用户
```

---

## 4. 后端架构缺陷（10 项）

### 4.1 异常体系

**当前状态**：`GlobalExceptionHandler` 已正确映射 HTTP 状态码（200/400/401/403/404/409/429/500），`BusinessException` 携带 `ErrorCode`，`ApiResponse` 统一响应格式。

| # | 缺陷 | 严重度 | 说明 |
|---|------|:------:|------|
| ARCH-01 | `ApiResponse.ok()` 返回 HTTP 200 + body.code=200，但 `ApiResponse.error()` 在非 BusinessException 场景下 HTTP 状态码可能不一致 | 🟢 P2 | 当前 GlobalExceptionHandler 已用 `ResponseEntity.status()` 映射，但 `ApiResponse.error()` 静态方法在 Controller 内直接调用时（如 UserController.lock/unlock）仍返回 HTTP 200 + body.code=404 |
| ARCH-02 | `UserController.lock/unlock` 用 `ApiResponse.error(404, ...)` 返回 HTTP 200 — 应抛 `BusinessException` | 🟡 P1 | 改为 `throw new BusinessException(ErrorCode.NOT_FOUND)` |

### 4.2 Controller 层

**当前状态**：Controller 层较薄，仅做参数提取和 Service 调用。`currentUserId()` 辅助方法重复在多个 Controller 中。

| # | 缺陷 | 严重度 | 说明 |
|---|------|:------:|------|
| ARCH-03 | `currentUserId(HttpServletRequest)` 在 DeviceController / AlarmController / DeviceDataController 中重复定义 | 🟡 P1 | 提取为 `BaseController` 或 `@CurrentUserId` 参数解析器 |
| ARCH-04 | `AuthController.logout` 含业务逻辑（计算 TTL） | 🟢 P2 | TTL 计算移入 `AuthService.logout` |

### 4.3 DTO 规范

| # | 缺陷 | 严重度 | 说明 |
|---|------|:------:|------|
| ARCH-05 | 无 DTO 分层规范（Request / Response / VO 混用） | 🟡 P1 | 建立 Request → DTO → Entity → VO 分层 |

### 4.4 Service 层

| # | 缺陷 | 严重度 | 说明 |
|---|------|:------:|------|
| ARCH-06 | `DeviceService.resolveCreateSiteId` 含较复杂的站点解析逻辑（4 个分支） | 🟢 P2 | 可接受，但建议补单测覆盖 |
| ARCH-07 | 缓存 key 含 `userId`（正确），但写操作用 `allEntries=true` 全量失效（性能损耗） | 🟢 P2 | 当前数据量可接受；后续可优化为精准失效 |

### 4.5 安全链

| # | 缺陷 | 严重度 | 说明 |
|---|------|:------:|------|
| ARCH-08 | `JwtAuthFilter` 的 `rejectIfNotPublicAuth` 硬编码路径 `/api/auth/login` 和 `/api/auth/register` | 🟢 P2 | 提取为配置或常量 |
| ARCH-09 | `AuthInterceptor` 从 request attribute 读 `roles` 为 `List<String>`，未做空值防御 | 🟡 P1 | 补 null 检查 |

### 4.6 Entity

| # | 缺陷 | 严重度 | 说明 |
|---|------|:------:|------|
| ARCH-10 | `RoleEnum.getRoleId()` 使用 `ordinal() + 1` 硬绑定数据库自增 ID — 数据库角色 ID 变化将导致权限崩溃 | 🟡 P1 | 改为查 `role` 表获取 ID |

---

## 5. 前端工程化缺陷（9 项）

### 5.1 当前前端状态

```
frontend/src/
├── api/index.js          Axios 封装（含拦截器）
├── components/            2 个通用组件
├── router/index.js        Hash 路由 + 登录守卫
├── views/                 6 个页面
├── App.vue                主布局
├── main.js                入口（图标全量导入）
└── style.css              全局样式 + CSS Token
```

### 5.2 缺陷清单

| # | 缺陷 | 严重度 | 说明 |
|---|------|:------:|------|
| FE-01 | `main.js` 全量导入 `@element-plus/icons-vue`（~80 个图标，仅用 10 个） | 🔴 P0 | 改为按需导入 |
| FE-02 | 无 ESLint 配置 | 🟡 P1 | 添加 `eslint-plugin-vue` |
| FE-03 | 无 Prettier 配置 | 🟡 P1 | 添加 `.prettierrc` |
| FE-04 | 无状态管理（Pinia）— token/username 存 localStorage，无响应式 | 🟡 P1 | 引入 Pinia + auth store |
| FE-05 | 无环境变量文件（`.env.development` / `.env.production`） | 🟡 P1 | 补充环境变量文件 |
| FE-06 | 无前端测试 | 🟡 P1 | 引入 Vitest |
| FE-07 | 无全局错误处理（ElMessage 提示缺失） | 🟡 P1 | 封装全局 ErrorHandler |
| FE-08 | `package.json` 无 lint / test / format 脚本 | 🟡 P1 | 补充脚本 |
| FE-09 | 路由守卫仅检查 token 存在，不检查 token 有效性和角色 | 🟡 P1 | 增加角色校验 |

---

## 6. 测试体系缺陷（7 项）

### 6.1 当前测试状态

```
backend/src/test/
├── ApplicationContextLoadTest.java       上下文冒烟测试
├── config/CacheSerializationTest.java    缓存序列化
├── db/                                   数据库迁移 IT
│   ├── DevSeedDemoDataTest.java
│   ├── FlywayProductionSeedIsolationTest.java
│   ├── MySqlMigrationV4IT.java
│   ├── MySqlMigrationV5IT.java
│   ├── MySqlMigrationV6IT.java
│   └── MySqlSeedIsolationIT.java
├── security/JwtAuthFilterTest.java       JWT 过滤器
├── service/                              Service 单测（15 个）
└── util/JwtUtilsTest.java                工具类
```

共 21 个测试文件，180+ 测试用例。

### 6.2 缺陷清单

| # | 缺陷 | 严重度 | 说明 |
|---|------|:------:|------|
| TEST-01 | 无 Testcontainers — MySQL IT 需手动环境（`RUN_MYSQL_IT=true`） | 🔴 P0 | 引入 `testcontainers-mysql` + `testcontainers-redis` |
| TEST-02 | 测试目录未分层（unit / integration / security / contract 混在 `dev/reboot/` 下） | 🟡 P1 | 按 `unit/` `integration/` `security/` 分目录 |
| TEST-03 | 无 REST 契约测试（API 层未验证 HTTP 语义） | 🟡 P1 | 引入 `MockMvc` 或 `WebMvcTest` |
| TEST-04 | 无前端测试 | 🟡 P1 | 引入 Vitest |
| TEST-05 | H2 schema 与正式迁移漂移（DB-04~07） | 🟡 P1 | 同步 H2 schema |
| TEST-06 | 无 CI 中的 IT 执行（MySQL IT 需手动触发） | 🟡 P1 | CI 中用 Testcontainers 自动执行 |
| TEST-07 | `RoleEnum.getRoleId()` 使用 `ordinal()+1`，测试中角色 ID 硬编码 | 🟢 P2 | 改为查表后更新测试 |

---

## 7. CI/CD 与可观测性缺陷（5 项）

### 7.1 CI/CD

| # | 缺陷 | 严重度 | 说明 |
|---|------|:------:|------|
| CICD-01 | 无 CD 流水线（部署完全手动 `deploy.sh`） | 🟡 P1 | 增加 GitHub Actions deploy job |
| CICD-02 | CI 中无前端 lint / test | 🟡 P1 | CI 增加 `npm run lint` + `npm run test` |
| CICD-03 | 无 Docker 镜像构建 CI | 🟢 P2 | CI 增加 `docker build` 验证 |

### 7.2 可观测性

| # | 缺陷 | 严重度 | 说明 |
|---|------|:------:|------|
| OBS-01 | 仅暴露 `/actuator/health`，无 Prometheus 指标 | 🟡 P1 | 引入 `micrometer-registry-prometheus` + 暴露 `/actuator/prometheus` |
| OBS-02 | compose.yml 无 Prometheus / Grafana 服务 | 🟢 P2 | 增加 Prometheus + Grafana 容器 |

---

## 8. 修复优先级矩阵

### 8.1 P0 — 必须先做（阻塞后续开发）

| # | 缺陷 ID | 任务 | 预估 | 依赖 |
|---|---------|------|------|------|
| 1 | DB-01~03 | V7 迁移：alarm 审计字段 + 唯一约束修复 + role 管理字段 | 2h | — |
| 2 | DB-04~07,18 | H2 schema 同步 V3/V4/V5/V6 变更 | 1h | — |
| 3 | RBAC-01~03 | 角色 CRUD API + 用户角色分配 API | 8h | DB-03 |
| 4 | FE-01 | 前端图标按需导入 | 0.5h | — |
| 5 | TEST-01 | 引入 Testcontainers | 4h | — |

### 8.2 P1 — 尽快做（工程质量基本盘）

| # | 缺陷 ID | 任务 | 预估 | 依赖 |
|---|---------|------|------|------|
| 6 | DB-08~09 | V8 迁移：alarm/device_data/operation_log 补 site_id | 3h | — |
| 7 | DB-18 | seed operation_log CHECK 同步 | 0.5h | DB-04 |
| 8 | RBAC-04~05 | 菜单权限表 + 按钮权限指令 | 8h | RBAC-01~03 |
| 9 | RBAC-07~08 | 管理员创建用户 + 密码重置 | 3h | — |
| 10 | ARCH-02 | UserController.lock/unlock 改用 BusinessException | 0.5h | — |
| 11 | ARCH-03 | 提取 currentUserId 到 BaseController | 1h | — |
| 12 | ARCH-05 | DTO 分层规范 | 2h | — |
| 13 | ARCH-09 | AuthInterceptor 空值防御 | 0.5h | — |
| 14 | ARCH-10 | RoleEnum.getRoleId() 改为查表 | 1h | — |
| 15 | FE-02~03 | ESLint + Prettier | 2h | — |
| 16 | FE-04 | Pinia 状态管理 | 4h | — |
| 17 | FE-05~07 | 环境变量 + 全局错误处理 + 路由守卫 | 3h | — |
| 18 | TEST-02 | 测试目录分层 | 2h | — |
| 19 | TEST-03 | REST 契约测试 | 4h | — |
| 20 | TEST-06 | CI 集成 Testcontainers | 2h | TEST-01 |
| 21 | CICD-01 | CD 流水线 | 4h | — |
| 22 | CICD-02 | CI 前端 lint/test | 1h | FE-02 |
| 23 | OBS-01 | Prometheus 指标暴露 | 2h | — |

### 8.3 P2 — 计划做（长期完善）

| # | 缺陷 ID | 任务 | 预估 |
|---|---------|------|------|
| 24 | DB-10~17 | 外键 / 审计字段 / 分区 / 冗余索引清理 | 8h |
| 25 | RBAC-06 | 5 级数据权限 data_scope | 8h |
| 26 | ARCH-04,06~08 | Service/Filter 代码优化 | 4h |
| 27 | FE-08~09 | package.json 脚本 + 路由守卫增强 | 2h |
| 28 | TEST-04,07 | 前端测试 + RoleEnum 测试更新 | 4h |
| 29 | CICD-03 | Docker 镜像构建 CI | 2h |
| 30 | OBS-02 | Grafana 面板 | 4h |

---

## 9. Phase 3 执行计划（4 周）

### Week 1：数据库止血 + Testcontainers + RBAC 核心

| Day | 任务 | 缺陷 ID | 交付物 |
|-----|------|---------|--------|
| 1 上午 | V7 迁移脚本（alarm 审计 + 唯一约束 + role 管理） | DB-01~03 | `V7__alarm_role_audit_fields.sql` |
| 1 下午 | H2 schema 全量同步 | DB-04~07,18 | `schema-h2.sql` 更新 |
| 2 | Testcontainers 引入 + MySQL/Redis 容器化测试 | TEST-01 | `pom.xml` 依赖 + `BaseIntegrationTest` |
| 3 | 角色管理 API（RoleController + RoleService + RoleMapper CRUD） | RBAC-01~02 | 角色 CRUD 接口 |
| 4 | 用户角色分配 API + 管理员创建用户 + 密码重置 | RBAC-03,07~08 | 用户角色管理接口 |
| 5 | 前端图标修复 + 测试目录分层 + CI Testcontainers 集成 | FE-01,TEST-02,06 | 前端修复 + CI 更新 |

### Week 2：后端架构治理 + RBAC 进阶

| Day | 任务 | 缺陷 ID | 交付物 |
|-----|------|---------|--------|
| 6 | V8 迁移（alarm/device_data/operation_log 补 site_id） | DB-08~09 | `V8__add_site_id_to_resources.sql` |
| 7 | 菜单权限表 + 按钮权限注解 | RBAC-04~05 | `sys_menu` 表 + `@HasPermission` |
| 8 | 后端架构修复（UserController / BaseController / AuthInterceptor） | ARCH-02~03,09~10 | 代码修复 |
| 9 | DTO 分层规范 + REST 契约测试 | ARCH-05,TEST-03 | DTO 规范 + MockMvc 测试 |
| 10 | 测试补全 + 全量回归验证 | — | `./mvnw verify` 全绿 |

### Week 3：前端工程化 + 可观测性

| Day | 任务 | 缺陷 ID | 交付物 |
|-----|------|---------|--------|
| 11 | ESLint + Prettier + package.json 脚本 | FE-02~03,08 | 前端工程化配置 |
| 12 | Pinia 状态管理 + 全局错误处理 | FE-04,07 | auth store + ErrorHandler |
| 13 | 环境变量 + 路由守卫增强 | FE-05,09 | `.env.*` + 角色路由 |
| 14 | Prometheus 指标暴露 + Actuator 完善 | OBS-01 | `/actuator/prometheus` |
| 15 | 前端测试框架引入 | FE-06,TEST-04 | Vitest 配置 |

### Week 4：CI/CD + 文档 + 收尾

| Day | 任务 | 缺陷 ID | 交付物 |
|-----|------|---------|--------|
| 16 | CD 流水线 + Docker 镜像构建 CI | CICD-01,03 | GitHub Actions deploy |
| 17 | CI 前端 lint/test 集成 | CICD-02 | CI 更新 |
| 18 | 开源级 README + 架构图更新 | — | `README.md` |
| 19 | ADR 0021~0023 + 文档同步 | — | ADR 新增 |
| 20 | 全量回归 + Phase 3 验收 | — | 验收报告 |

---

## 附录 A：正面发现（已具备的良好实践）

| # | 实践 | 评价 |
|---|------|------|
| 1 | Flyway 迁移链只增不删 + CI 守卫 | ✅ 优秀 |
| 2 | 种子数据与生产迁移隔离（ADR 0019） | ✅ 优秀 |
| 3 | 站点授权模型设计（ADR 0020） | ✅ 优秀 |
| 4 | JWT jti + 黑名单 + fail-close | ✅ 优秀 |
| 5 | 登录限流 + 账户锁定 + 登录审计 | ✅ 优秀 |
| 6 | 注册治理（开关 + 邀请码 + 配额） | ✅ 优秀 |
| 7 | 构造器注入（无 @Autowired 字段注入） | ✅ 良好 |
| 8 | 统一 ApiResponse + ErrorCode + GlobalExceptionHandler | ✅ 良好 |
| 9 | 缓存 key 含 userId（避免越权缓存命中） | ✅ 良好 |
| 10 | 前端路由懒加载 + Axios 拦截器 + CSS Token | ✅ 良好 |

---

## 附录 B：涉及文件清单

| 类别 | 文件 |
|------|------|
| 迁移脚本 | V1 / V3 / V4 / V5 / V6 + 待新增 V7 / V8 |
| 实体类 | Device / Alarm / Role / User / UserRole / UserSite / Site / DeviceData / OperationLog / LoginAudit |
| Mapper | DeviceMapper / AlarmMapper / RoleMapper / UserRoleMapper / UserSiteMapper / SiteMapper / UserMapper / DeviceDataMapper / OperationLogMapper / LoginAuditMapper |
| Controller | AuthController / DeviceController / AlarmController / DeviceDataController / UserController / SiteController |
| Service | AuthService / DeviceService / AlarmService / DeviceDataService / UserService / SiteAccessService / SiteService / TokenBlacklistService / AuthRateLimitService / LoginAuditService |
| 安全链 | JwtAuthFilter / AuthInterceptor |
| 配置 | application.yml / CorsConfig / CacheConfig / AsyncConfig |
| 测试 | 21 个测试文件 + schema-h2.sql |
| 前端 | 6 页面 + api/index.js + router + main.js + App.vue + style.css |
| CI/CD | ci.yml + qodana_code_quality.yml |

---

> **审计结论**：项目安全治理已到位（P0/P1 安全缺陷全部修复），但工程质量距 GitHub 优秀开源项目仍有差距。核心短板集中在 RBAC 权限体系（4.0/10）、前端工程化（3.5/10）和可观测性（2.0/10）。建议按 4 周计划推进，P0 缺陷在 Week 1 内全部修复。
