# P1-02-A-2 User Status Model（用户安全状态模型）

> 分支：`feat/p1-02-a-2-user-status-model` | 日期：2026-08-23
> 关联：P1-02-A-1（Redis 入口加固）/ V5 migration / ADR 0020（站点授权，未改动）

---

## 1. V5 Schema（`V5__add_user_security_status.sql`，append-only）

| 列 | 类型 | 默认 | 用途 |
|---|---|---|---|
| `failed_attempts` | INT NOT NULL | **0** | 连续登录失败次数；成功登录/管理员解锁归零 |
| `locked_until` | DATETIME NULL | **NULL** | 持久锁定截止时间；NULL=未锁定（登录校验 `locked_until > now` → 统一 401） |
| `password_changed_at` | DATETIME NULL | **NULL** | 最近改密时间（P1-02-A-4 旧 token 失效基准，本阶段仅建列） |

约束：不修改 V1/V3/V4；新库 V1→V5 与既有 V1-V4 增量升级均适用；migration integrity guard 放行（纯新增）。

## 2. 用户生命周期（扩展）

```
CREATE（register，VIEWER，无站点）
  → ACTIVE（status=1）
      ├─ 连续登录失败 5 次 → LOCKED（locked_until = now + 15min，DB + Redis 双层）
      ├─ 管理员 lockUser → LOCKED（DB locked_until；登录口 401）
      └─ 解锁：TTL 到期自愈 / 管理员 unlockUser（清 DB + Redis）→ ACTIVE
  → DISABLED（status=0，管理员 toggleStatus；登录口统一 401，不泄露）
  → DELETED（is_deleted=1，管理员软删）
```

管理员接口（`@RequireRole(ADMIN)`，UserController）：
- `PUT /api/users/{id}/lock`（持久锁定 15 分钟）
- `PUT /api/users/{id}/unlock`（清 DB failed_attempts/locked_until + Redis 失败计数）

## 3. Redis + DB 双层模型

| 层 | 机制 | 职责 |
|---|---|---|
| Redis（P1-02-A-1） | `login:fail:user:{username}` TTL 15min；IP 滑动窗口限流 | 快速熔断、跨实例一致、防爆破放大 |
| DB（P1-02-A-2） | `failed_attempts` + `locked_until` | 持久事实源：跨重启、管理员解锁、审计 |

协同规则：
- 登录失败：Redis 计数 + DB `failed_attempts+1`；达 5 次 → DB `locked_until = now+15min`
- 登录成功：Redis 删 key + DB `resetLoginSecurity`（failed_attempts=0, locked_until=NULL）
- 管理员解锁：DB `resetLoginSecurity` + Redis 删 key（`UserService.unlockUser`）
- 登录前置检查：IP 限流（429）→ Redis 锁定（401）→ DB 锁定（401）→ 校验密码

## 4. 测试覆盖

- **Unit（Mockito）**：`AuthServiceTest`——DB 锁定用户 401、失败计数递增、第 5 次持久锁定、成功清 DB+Redis、禁用用户统一 401；`UserServiceTest`——管理员 lockUser/unlockUser（清 DB+Redis）、不存在返回 false
- **Integration（MySQL，RUN_MYSQL_IT 门控）**：`MySqlMigrationV5IT`——全新 V1→V5（列存在 + 默认 0/NULL/NULL）；已有 V1-V4 升级（存量用户默认值回填）
- **Security**：锁定/禁用登录统一 401 不泄露；解锁后可登录（登录成功清计数）

## 5. 边界（与后续阶段）

- 本阶段**不实现**：JWT 吊销/黑名单（P1-02-A-4，仅预置 `password_changed_at` 列）、注册治理（P1-02-A-3，不依赖新字段）、last_login_at/disabled_reason（暂缓）
- P1-01 站点授权模型、P1-02-A-1 入口加固、RBAC、JWT 签名机制：均未改动
