# P1-02-A-4 JWT Lifecycle Design Audit（JWT 生命周期设计审计）

> 分支：`main` @ `649e43f379d8fc97cc38ada229820b42f7072794`（P1-02-A-3 已合并）
> 类型：只读设计审计（未修改任何代码/配置/migration；本文件未进入版本控制）
> 约束：不改 V1-V5 migration / 不改 P1-01 授权模型 / 不改 RBAC / **Redis 实现 jti blacklist** / **使用 V5 `password_changed_at`** / 每阶段独立 PR

---

## 1. Git 状态
- branch：`main`；HEAD：`649e43f379d8fc97cc38ada229820b42f7072794`（= origin/main）
- workspace：clean（未创建 branch/commit/push/merge）

## 2. 现状与缺陷
| 项 | 现状 |
|---|---|
| payload | `subject(username)`、`userId`、`roles`、`iat`、`exp(24h)` —— **无 `jti`** |
| 请求验证 | `JwtAuthFilter` 仅验签 + 过期 |
| logout | **无** |
| 禁用账号 | 仅登录时校验 status；**存量 token 有效至 exp** |
| 修改密码 | 仅更新 password 列；**不写 `password_changed_at`、不失效旧 token** |
| 角色变化 | 全局角色内嵌 token（需重登）；站点角色运行时查询（P1-01 已即时） |

## 3. 设计目标（A-4 覆盖）
1. 登出 → 单 token 立即失效（jti blacklist）
2. 禁用账号 → 该用户全部存量 token 立即失效
3. 修改密码 → 全部存量 token 立即失效（复用 V5 `password_changed_at`）
4. 不引入 V6 migration；不改变 JWT 签名机制；不改变 RBAC/P1-01

## 4. 设计方案

### 4.1 JWT payload 增加 `jti`（UUID）
`JwtUtils.generateToken`：`.id(UUID.randomUUID().toString())`（JJWT 的 `jti` 标准 claim）。
签名机制（HS256、密钥 SSOT）不变——仅 payload 增字段。

### 4.2 Redis key 设计（全部带 TTL，多实例一致）
| Key | 用途 | TTL |
|---|---|---|
| `token:blacklist:{jti}` | 登出单 token 黑名单 | 剩余有效期（exp − now） |
| `revoke:user:{userId}` | 用户级撤销标记（禁用/改密），value=撤销时刻（epoch 秒） | 24h（覆盖最长在途 token 剩余寿命） |

### 4.3 校验流程（JwtAuthFilter 增补，签体验证不变）
```
验签 + 过期（现有 JwtUtils.validateToken）
  → 校验 jti 存在（无 jti 的旧 token → 拒绝，强制重登）
  → token:blacklist:{jti} 命中 → 拒绝
  → revoke:user:{userId} 存在 且 token.iat < 标记 → 拒绝
  → 通过 → 注入 userId/username/roles/jti/iat 到 request attributes
```
Redis 双读（无 DB 命中）；jti/iat 同时存入 request attrs 供 logout 使用。

### 4.4 撤销写入点
| 事件 | 写入 |
|---|---|
| 登出 `POST /api/auth/logout`（需登录） | `token:blacklist:{jti}`（TTL=剩余 exp）——从 request attr 取 jti |
| 禁用 `UserService.toggleStatus` → 0 | Redis `revoke:user:{userId}` = now |
| 修改密码 `UserService.changePassword` | DB `password_changed_at = now`（V5 列，**持久事实源**）+ Redis `revoke:user:{userId}` = now（运行时快速失效） |
| 解锁/启用（toggleStatus → 1） | 不清理 `revoke:user:{userId}`（旧 token 保持失效，符合安全预期；新登录签发新 token） |

> `password_changed_at` 的定位：**持久记录 + 审计**（跨 Redis 重启仍可追溯/复核）；运行时撤销由 Redis 标记承担（每请求 2 次 Redis 读、0 次 DB 读）。Redis flush 残余风险：撤销丢失，旧 token 有效至 exp（≤24h）——记录为已知残余，运维可重发撤销。

### 4.5 过渡期策略
存量 token（无 `jti`）→ **拒绝并要求重新登录**（学习项目可接受；保证新机制立即生效、无旧 token 绕过面）。文档标注升级即全员重登。

## 5. 修改文件范围（A-4 实施阶段，本审计不编码）
| 文件 | 变更 |
|---|---|
| `util/JwtUtils.java` | generateToken 增加 `jti`（签名机制不变） |
| `security/JwtAuthFilter.java` | 增补 jti 存在性 + 黑名单 + revoke 标记校验；注入 jti/iat attrs |
| `service/TokenBlacklistService.java` | **新增**：`revokeToken(jti,ttl)` / `revokeUser(userId)` / `isTokenBlacklisted(jti)` / `isUserRevoked(userId, iat)`（StringRedisTemplate） |
| `controller/AuthController.java` | +`POST /api/auth/logout` |
| `service/AuthService.java` | +`logout(jti)` |
| `service/UserService.java` | toggleStatus(禁用) 撤销；changePassword 写 password_changed_at + 撤销 |
| `mapper/UserMapper.java` | +`updatePasswordChangedAt(id, now)` |
| 测试 | `TokenBlacklistServiceTest`、`AuthServiceTest.logout`、`UserServiceTest` 撤销用例、JwtUtils jti 断言 |
| `docs/security/P1-02-A-4-jwt-lifecycle-design.md` | 本文件 |

**明确不改**：V1-V5 migration（无 V6）、`RoleEnum`/`RequireRole`/`AuthInterceptor`（RBAC）、`SiteAccessService`/user_site（P1-01）、`AuthRateLimitService`（A-1）、Redis 既有 key 设计。

## 6. Migration 判断
**A-4 不需要新 migration（无 V6）**：
- `jti` = JWT payload 字段（非 DB）
- 黑名单/撤销标记 = Redis
- `password_changed_at` = **V5 已建列**，本次仅补写入逻辑（`updatePasswordChangedAt`）

## 7. 测试计划
| 层 | 用例 |
|---|---|
| Unit | JwtUtils 生成含 jti；TokenBlacklistService（mock Redis）：revokeToken/isTokenBlacklisted、revokeUser/isUserRevoked（iat 比较）、TTL 设置；AuthService.logout（黑名单 + TTL）；UserService.toggleStatus 禁用→revokeUser、changePassword→updatePasswordChangedAt + revokeUser；无 jti token → 拒绝 |
| Security | 登出后旧 token 401；禁用后旧 token 401；改密后旧 token 401；新 token 正常；Redis 重启残余（文档记录） |
| Regression | `./mvnw clean verify`（≥147）+ MySQL IT 8/8（A-4 无 DB 变更，回归为主）+ frontend build |

## 8. 边界与风险
| 项 | 说明 |
|---|---|
| Redis 依赖 | 校验依赖 Redis（每请求 2 读）；Redis 不可用 → 黑名单/撤销校验失效（fail-open）；A-2 DB 锁定不受影响。可后续加 fail-closed 开关 |
| Redis flush | 撤销标记丢失（≤24h 残余）；`password_changed_at` 持久可复核 |
| 每请求开销 | +2 次 Redis 读（可接受；多实例安全） |
| 与 A-2 协同 | A-2 是「登录口」锁定；A-4 是「存量 token」失效——互补不重叠 |

## 9. 实施顺序（每阶段独立 PR）
```
P1-02-A-4 单阶段独立 PR：
  commit 1：JwtUtils(jti) + TokenBlacklistService + JwtAuthFilter 校验 + 过渡期（无 jti 拒绝）
  commit 2：logout 接口 + toggleStatus/changePassword 撤销写入 + updatePasswordChangedAt
  （或合并为单 commit：feat(auth): JWT lifecycle revocation）
```
门禁：mvnw clean verify + MySQL IT + frontend build + migration integrity；经监理批准后实施。

---

*只读设计审计（docs/security/P1-02-A-4-jwt-lifecycle-design.md），未修改任何文件，未进入版本控制。等待监理批准后进入实现。*
