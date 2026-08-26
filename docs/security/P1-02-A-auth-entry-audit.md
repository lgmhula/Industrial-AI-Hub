# P1-02-A Authentication Entry Governance Audit（认证入口治理审计）

> 分支：`main` @ `1eac44d48b626ccf2422d074f59e37ed216aa59b`（PR #6 合并后）
> 类型：只读安全审计（未修改任何既有文件；本报告为新建交付物，未 commit）
> 日期：2026-08-23

---

## 1. 当前认证架构

```
POST /api/auth/login（公开，/api/auth/** 排除 RateLimit 与 Auth 拦截器）
  → AuthController.login
  → AuthService.login
      → UserMapper.findByUsername(username)（is_deleted=0）
          ├─ 用户不存在 → 401「用户名或密码错误」（通用文案）
          ├─ status==0 → 403「账户已禁用，请联系管理员」⚠️
          └─ BCrypt.matches 失败 → 401「用户名或密码错误」（通用文案）
      → UserRoleMapper.findRoleCodesByUserId → 组装 roles
      → JwtUtils.generateToken(userId, username, roles)   [HS256, exp=24h]
  → JwtAuthFilter：每次请求验签/过期 → request attrs(userId/username/roles)
  → AuthInterceptor：@RequireRole 角色判定 → Controller → Service（站点作用域，P1-01）
```

**入口面现状**：
- 登录/注册**无任何限流**（`WebMvcConfig` 将 `/api/auth/**` 排除出 `RateLimitInterceptor`；且该拦截器为进程内 per-URI Guava 令牌桶，非 IP/用户名维度）
- 失败计数：无；锁定：无；验证码：无；邀请制：无

## 2. 登录风险

| # | 风险 | 证据 | 等级 |
|---|---|---|---|
| L1 | **账户枚举（用户存在性泄露）** | `AuthService.login`：用户不存在/密码错误 → 统一 401「用户名或密码错误」✓；但 **`status==0` 分支返回 403「账户已禁用」** → 攻击者可区分「不存在/密码错(401)」与「存在且被禁用(403)」 | **P1** |
| L2 | **无登录失败计数/锁定** | `AuthService.login` 无失败次数记录、无锁定时间；`user` 表无 `failed_attempts`/`locked_until`/`locked` 字段 | **P1** |
| L3 | **登录无限流（IP/用户名维度）** | `/api/auth/**` 被排除出 `RateLimitInterceptor`；现有拦截器为 per-URI 进程内令牌桶（默认 50 req/s，重启即重置，多实例不共享），非 IP/账号维度 → 可暴力/撞库/分布式爆破 | **P1** |
| L4 | 密码策略宽松 | `RegisterRequest` 仅 `@Size(min=6)`，无复杂度要求；无历史密码/复用控制 | P2 |
| L5 | 无登录审计事件 | 仅 `@OperationLog(LOGIN)` 落库（成功/失败均记），但无失败来源（IP/UA/账号）聚合分析 | P2 |

## 3. 注册风险

| # | 风险 | 证据 | 等级 |
|---|---|---|---|
| R1 | **公开无门槛注册 + 无限流** | `/api/auth/register` 公开；无验证码、无邀请、无限流 → 可批量注册僵尸账号 | **P1**（结合 L3 放大） |
| R2 | 默认 VIEWER 角色 | `AuthService.register` 分配 `user_role=VIEWER` | 低（P1-01 后 VIEWER 无站点=无资源访问） |
| R3 | 默认**不**加入任何站点 | register 不插入 `user_site` → 新用户零资源访问权（安全红利：注册即空壳，需管理员分配站点） | 低（安全侧 OK，功能侧待站点管理接口） |
| R4 | 无注册审计/配额 | 无每日注册上限、无邮箱/手机验证 | P2 |

> P1-01 已把 R2/R3 的危害收窄为「注册即空壳」；剩余核心是 **R1 的批量注册面 + L3 的爆破面**。

## 4. JWT 生命周期缺陷

| # | 缺陷 | 证据 | 等级 |
|---|---|---|---|
| J1 | **无 jti / tokenVersion** | `JwtUtils.generateToken` payload 仅 `subject(username)` + claims `userId`/`roles` + `iat`/`exp`；无 `jti`、无版本号 | **P1** |
| J2 | **无黑名单 / 登出失效** | 无 logout 接口；无 blacklist 存储；已签发 JWT 到期前持续有效 | **P1** |
| J3 | **禁用不即时失效** | `AuthService.login` 校验 `status==0`，但已签发 JWT 在 `exp` 前仍可通过 `JwtAuthFilter`（后者只验签/过期，不查用户状态）→ 禁用用户 token 最长 24h 有效 | **P1** |
| J4 | 角色变更延迟 | roles 内嵌 JWT，改角色需重新登录（P1-01 已用 user_site 运行时查询规避站点维度；全局角色维度仍存在） | P2 |
| J5 | 24h 长有效期无刷新机制 | `expirationMs` 默认 86400000；无 refresh token、无 sliding expiration | P2 |

## 5. Redis 能力（可用于修复）

| 能力 | 现状 | 可用性 |
|---|---|---|
| `StringRedisTemplate` | 已配置（注释明示「计数器/锁」用途） | ✅ login 失败计数 / IP-账号限流计数 |
| Redisson（分布式锁/限流） | 已集成（Day 46） | ✅ 跨实例计数原子性 / `RRateLimiter` |
| `RedisCacheManager`（dev/prod，TTL 30min） | 已有缓存设施 | ✅ token 状态 / 黑名单 TTL 存储 |
| 既有 Spring Cache | device/user/数据缓存已走 Redis | ✅ 不新增依赖即可实现全部 P1-02-A 修复 |

> 结论：**无需引入新组件**即可实现登录限流（IP/用户名维度计数）、失败锁定、token 黑名单/版本化（Redis TTL 键）。

## 6. 推荐修复顺序（设计建议，未实现）

1. **P1-02-A-1 登录加固（阻断爆破与枚举）**
   - `login`：`status==0` 分支改为通用 `401「用户名或密码错误」`（消除 L1 枚举差异）；或对「存在性」统一 401
   - Redis 计数：`login:fail:{username}`（失败 N 次锁 M 分钟）+ `login:rl:{ip}`（IP 维度令牌桶/滑动窗口）
   - 失败计数在登录成功后清除
   - 将 `/api/auth/**` 纳入限流（或对 login/register 单独配置更严限制）
2. **P1-02-A-2 用户状态模型扩展（锁定字段）**
   - `user` 表新增 `failed_attempts` / `locked_until`（或 `locked`）——V5 迁移（只增不删，走 migration integrity guard）
3. **P1-02-A-3 注册治理**
   - 关闭公开注册或改为邀请码/管理员创建；至少加验证码 + 每日注册配额（Redis 计数）
4. **P1-02-A-4 JWT 生命周期**
   - `generateToken` 增加 `jti`（UUID）+ 可选 `tokenVersion`；`JwtAuthFilter` 每次请求校验 Redis `token:blacklist:{jti}`（登出/禁用时写入，TTL=剩余有效期）
   - `UserService.toggleStatus`（禁用）时按 userId 使会话失效：Redis `user:revoke:{userId}` 版本号，JWT 校验时比对
   - 新增 `POST /api/auth/logout`（将 jti 入黑名单）
5. **P1-02-A-5 审计**：登录失败/来源 IP 聚合（配合 ELK 或 Redis 统计）

## 7. 不应该修改部分（保持）

- **P1-01 站点授权模型**（SiteAccessService / user_site / 列表站点过滤 / 缓存含 userId）——已合并验收，保持
- **JWT 签名/校验机制**（HS256、密钥 SSOT/fail-fast、JwtUtils 解析）——只**增补** jti/校验步骤，不重写
- **BCrypt 密码存储**（保持）
- **RateLimitInterceptor 对既有业务接口的限流**（保持；仅新增认证入口限流层）
- **Flyway 既有迁移（V1/V3/V4）与 migration integrity guard**（新增用 V5，不修改历史）
- **公开 `/api/sites`、admin 用户管理接口**（保持）

---

**结论**：认证入口存在 4 项 P1 级缺口（账户枚举差异、无失败锁定、认证入口无限流、JWT 无吊销/黑名单）；Redis 基础设施完备，可零新增依赖实现全部修复。建议按 §6 顺序进入 P1-02-A 实现（登录加固 → 状态模型 → 注册治理 → JWT 生命周期）。

*本审计未修改任何既有文件；报告为新建交付物（`docs/security/P1-02-A-auth-entry-audit.md`），未创建 commit/branch。*
