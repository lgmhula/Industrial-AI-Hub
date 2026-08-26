# P1-02-A-3~A-5 Security Governance Design Audit Report

> 分支：`main` @ `60ef2cdee662fa4a1a12090a3080185db2ac8399`（P1-02-A-2 已合并）
> 类型：只读设计审计（未修改任何代码/配置/migration；唯一产物为本文件，未进入版本控制）
> 日期：2026-08-23

---

## 1. Git 状态
- branch：`main`；HEAD：`60ef2cdee662fa4a1a12090a3080185db2ac8399`（= origin/main）
- workspace：clean（未创建 branch/commit/push/merge）

## 2. 当前认证架构（基线）
```
login/register（公开，/api/auth/** 排除拦截器）
  → AuthService（P1-02-A-1：IP 滑动窗口限流 + 失败计数 + 统一 401；A-2：DB 持久锁定）
  → JwtUtils.generateToken（HS256；payload: subject=username, userId, roles, iat, exp=24h）
  → JwtAuthFilter（每次请求验签/过期 → request attrs）
  → AuthInterceptor（@RequireRole）→ Controller → Service（P1-01 站点作用域）
```
已完成的防护：账户枚举（统一 401）、Redis 快速熔断 + DB 持久锁定、IP 登录/注册限流、站点资源作用域。

## 3. P1-02-A-3 注册治理审计

### 3.1 当前注册入口
| 项 | 现状 | 证据 |
|---|---|---|
| 公开注册 | ✅ 是（无邀请/验证码） | `/api/auth/register` 公开 |
| 验证码 | ❌ 无 | — |
| 邀请码 | ❌ 无 | — |
| 注册限流 | ⚠️ 仅 IP 维度 10 次/10min（A-1） | `register:attempt:ip:{ip}` |
| IP 配额/全局配额 | ⚠️ 仅单 IP 窗口，无全局日配额 | — |
| 邮箱验证 | ❌ 无（email 非必填） | RegisterRequest 无 email |

### 3.2 默认权限
- 注册 → `user_role=VIEWER`（全局角色）；**不自动加入 site**（无 `user_site` 写入）→ **零资源访问权**（P1-01 已保证「注册即空壳」）。

### 3.3 风险分析
| 风险 | 等级 | 现状 |
|---|---|---|
| 批量注册（僵尸账号） | **P1** | 单 IP 10/10min 可被分布式 IP 绕过；无全局配额 → 可批量注册空壳账号 |
| 用户名枚举 | **P1（低危）** | register 重复用户名返回 **409「用户名已存在」** → 可枚举存量用户名（login 已统一 401，register 仍泄露） |
| 资源污染 | 低 | 注册用户无站点 → 无资源访问/写入面（A-1/A-2 已收窄） |
| 存储/DB 膨胀 | 低 | 空壳账号仅占 user/user_role 行 |

### 3.4 方案比较
| 方案 | 安全 | 复杂度 | 当前阶段适配 |
|---|---|---|---|
| A. 关闭公开注册，仅 ADMIN 创建 | 最高 | 低（删/关 register 入口） | 中（牺牲注册学习面） |
| B. 邀请码注册（.env 可配静态邀请码） | 高 | 低（1 个配置项 + 校验） | **高** |
| C. 公开 + Redis 全局配额 + 邮箱验证 | 中 | 中-高（配额可做，邮箱验证需邮件服务） | 低（邮件服务过度） |

### 3.5 推荐
**方案 B（邀请码注册）**：register 需携带邀请码，邀请码来自 `.env`（`REGISTER_INVITE_CODE`，可选配置：缺省=关闭注册 → ADMIN 创建路径）；同时把重复用户名的 409 改为通用文案（消除枚举差异）。不需要 migration。

## 4. P1-02-A-4 JWT 生命周期审计

### 4.1 当前 token payload
`subject=username`、`userId`、`roles`、`iat`、`exp(24h)`。**无 `jti`、无 `tokenVersion`、无 refresh token**。

### 4.2 生命周期分析
| 事件 | 当前行为 |
|---|---|
| 登录 → 签发 | 签发 24h token |
| 请求验证 | 仅验签 + 过期（JwtAuthFilter） |
| 注销（logout） | **无 logout 接口** |
| 禁用账号 | login 校验 status；**存量 token 有效至 exp**（不即时失效） |
| 修改密码 | **不影响已签发 token**（无失效机制） |
| 角色变化 | 全局角色（user_role）内嵌 token → 需重新登录；站点角色（user_site）运行时查询 → 即时生效（P1-01 红利） |

### 4.3 缺陷
1. **无 logout / 主动失效**（P1）
2. **禁用不即时阻断存量 token**（P1，最长 24h）
3. **改密不影响旧 token**（P1）
4. 无 refresh token（P2，24h 重登可接受）

### 4.4 方案比较
| 方案 | 机制 | 优点 | 缺点 |
|---|---|---|---|
| A. Redis blacklist(jti) | token 加 `jti`(UUID)；登出/禁用时 jti 入 Redis 黑名单（TTL=剩余 exp）；过滤器查黑名单 | 即时、单 token 级、多实例一致、**无需 DB 迁移** | 每请求一次 Redis 查询；老 token 无 jti（过渡期处理） |
| B. user tokenVersion | DB 版本列（或复用 V5 `password_changed_at`），token 携带 iat，请求时比对 | 无需 Redis 也可失效 | 每请求查 DB（或缓存）；禁用需额外状态校验 |
| C. 双方案 | jti 黑名单（登出/禁用）+ `password_changed_at`（改密失效） | 覆盖全部失效场景；**无需 V6** | 组合实现量略增 |

### 4.5 推荐
**方案 C（双方案）**：符合「单体 + Redis 已存在 + 多实例扩展」——Redis 黑名单天然多实例安全；`password_changed_at`（V5 已建列）作为改密失效基准（token.iat < password_changed_at → 401 重登）。禁用即时失效：`UserService.toggleStatus` 禁用时把该用户全部 token 失效（按 userId 黑名单前缀 `jti:user:{userId}:*` 或 tokenVersion 前缀；建议黑名单键 `revoke:user:{userId}` 版本号，token 校验时比对 iat 与版本时间——即把「禁用时刻」当作改密基准统一处理）。

### 4.6 Migration 判断
**A-4 不需要 V6 migration**：`jti` 是 JWT payload 字段（JwtUtils 变更，非 DB）；黑名单在 Redis；改密/禁用基准复用 V5 `password_changed_at`。唯一注意：`JwtUtils.generateToken` 增加 `jti` 后，存量 token（无 jti）过渡期按「未黑名单」放行（宽限）或强制重登（建议宽限 + 文档）。

## 5. P1-02-A-5 登录审计审计

### 5.1 已有审计能力
| 项 | 现状 | 证据 |
|---|---|---|
| 登录成功记录 | ⚠️ operation_log 有 LOGIN 行，但 **user_id 恒为 NULL**（登录请求无 JWT，aspect 取不到 userId） | OperationLogAspect 读 request attr userId |
| 登录失败记录 | ⚠️ 同（[失败] 前缀），且 **不含用户名**（description 为静态「用户登录」，buildDescription 无法从 LoginRequest 提取用户名） | AuthController @OperationLog(description="用户登录") |
| IP 记录 | ✅ operation_log.ip_address（XFF/RemoteAddr） | OperationLogAspect.getClientIp |
| UA 记录 | ❌ 无 | — |
| userId 记录 | ❌ 登录事件恒 NULL | — |
| Redis 失败计数 | ✅ 仅运行态（A-1），非持久审计 | login:fail:user:* |

### 5.2 安全运营需求 → 方案比较
| 方案 | 设计 | 优点 | 缺点 |
|---|---|---|---|
| A. 扩展 operation_log | 加列（username/ua）或动态 description | 无新表 | 登录为安全事件，与业务操作日志混存；列扩展需 V6 |
| B. 新增 login_audit 表 | id/user_id/username/success/ip/ua/reason/created_at | 专用、可查、保留失败原因（锁定/禁用/密码错） | 需 V6 migration |
| C. ELK/日志平台 | 结构化日志 + ELK | 最终形态 | 当前阶段过度（ELK 为 Day 101 可选）；无采集管道 |

### 5.3 推荐
**方案 B（新增 `login_audit` 表，V6 migration）**——登录安全事件与业务操作日志分离，支持安全运营（暴力尝试溯源、锁定复核、UA 指纹）；字段：`id, user_id(NULL 可), username, success TINYINT, ip_address, user_agent VARCHAR(256), reason VARCHAR(64)（SUCCESS/PASSWORD_MISMATCH/USER_NOT_FOUND/DISABLED/LOCKED/IP_LIMITED）, created_at`。写入点：`AuthService.login`（成功与各失败分支，含用户名与原因）。成本低、无外部依赖，符合当前阶段（避免过度 SaaS 化）。

## 6. 方案比较汇总
| 阶段 | 推荐方案 | 备选（未采纳原因） |
|---|---|---|
| A-3 | B 邀请码注册 | A 关闭注册（牺牲学习面）；C 邮箱验证（需邮件服务，过度） |
| A-4 | C jti 黑名单 + password_changed_at | A 纯黑名单（改密失效缺失）；B 纯 tokenVersion（每请求 DB，禁用耦合） |
| A-5 | B login_audit 表 | A 扩展 operation_log（混存）；C ELK（过度） |

## 7. 推荐方案（合并路线）
```
A-3（无 migration）: register 邀请码开关 + 409 枚举文案统一 + 全局注册日配额（Redis）
A-4（无 migration）: JWT +jti；JwtAuthFilter 黑名单/失效校验；logout 接口；
                     toggleStatus/改密 → 失效基准（password_changed_at/revoke 键）
A-5（V6 migration）: login_audit 表 + AuthService 登录事件落库（含失败原因/UA/用户名）
```

## 8. Migration 影响
| 阶段 | Migration | 内容 |
|---|---|---|
| A-3 | **无** | 配置项 + Service 校验（Redis 配额） |
| A-4 | **无** | JWT payload（jti）+ Redis 黑名单 + 复用 V5 `password_changed_at` |
| A-5 | **V6__add_login_audit.sql**（append-only） | `login_audit` 表（CREATE TABLE，纯新增；guard 放行；新库/既有库均可） |

约束：V1/V3/V4/V5 不修改；V6 只新增；migration integrity guard 保持。

## 9. 测试计划
| 阶段 | Unit | Integration(MySQL) | Security |
|---|---|---|---|
| A-3 | register 无/错邀请码 → 400；全局配额超限 → 429；重复用户名 → 通用文案 | — | 枚举探测（同文案） |
| A-4 | generateToken 含 jti；filter 黑名单拒绝；改密后旧 token 401；禁用后旧 token 401；logout 后 401 | — | 存量无 jti token 宽限 |
| A-5 | login 成功/各失败 → login_audit 落库（user_id/username/success/ip/ua/reason）；操作日志不受影响 | V6 全新/既有库升级；login_audit 默认值 | 失败溯源（同 IP 多次失败可查） |

## 10. 实施顺序
```
P1-02-A-3（注册治理，无 migration，风险 P1，独立 PR）
   ↓
P1-02-A-4（JWT 生命周期，无 migration，风险 P1，独立 PR；改密/禁用失效基准）
   ↓
P1-02-A-5（登录审计，V6 migration，风险 P2，独立 PR；依赖 A-4 的 login_audit 落库点位）
```
每阶段独立分支 + PR + 门禁（mvnw verify + MySQL IT + frontend build + migration guard），经监理批准后实施。

---

*本审计为只读设计交付物（docs/security/P1-02-A-3-A-5-design-audit.md），未修改任何代码/配置/migration，未进入版本控制。执行结束，等待监理批准后再进入实现。*
