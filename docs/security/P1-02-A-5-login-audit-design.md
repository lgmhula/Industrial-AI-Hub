# P1-02-A-5 Login Audit Design Audit（登录审计设计审计）

> 分支：`main` @ `42dd8703d9837457f501d2d6c7874de7e70c690c`（P1-02-A-4 已合并）
> 类型：只读设计审计（未修改代码/migration/branch/commit/push；本文件未进入 git）
> 日期：2026-08-23

---

## 1. 当前状态

### 1.1 现有登录审计能力
| 项 | 现状 | 证据 |
|---|---|---|
| 登录成功记录 | ⚠️ operation_log 有 LOGIN 行，但 **user_id 恒 NULL**（登录请求无 JWT，aspect 取不到 userId） | AuthController @OperationLog(LOGIN) + OperationLogAspect 读 request attr userId |
| 登录失败记录 | ⚠️ 同（[失败] 前缀），**不含 username**（description 静态「用户登录」） | — |
| username | ❌ 不记录 | — |
| userId | ❌ 登录事件恒 NULL | — |
| IP | ✅ operation_log.ip_address（XFF/RemoteAddr） | OperationLogAspect.getClientIp |
| User-Agent | ❌ 不记录 | — |
| failure reason | ❌ 不记录（A-1/A-2 内部区分 密码错/禁用/锁定/限流，但不落库） | — |
| timestamp | ✅ created_at | — |

### 1.2 既有阶段对审计的影响
| 阶段 | 影响 |
|---|---|
| A-1 入口加固 | 引入失败分类（IP 限流 429 / 统一 401）；`AuthRateLimitService` 有 Redis 计数（非持久审计） |
| A-2 用户状态 | DB `failed_attempts` 仅计数，无原因/来源；锁定/禁用语义可映射为 reason 枚举 |
| A-3 注册治理 | 不影响登录审计 |
| A-4 JWT 生命周期 | 登出/撤销不改变登录审计需求 |

**结论**：登录安全事件当前几乎不可溯源（仅 IP）；A-1/A-2 的失败分类可作为审计 reason 的事实来源。

## 2. 风险分析
| 风险 | 等级 |
|---|---|
| 暴力破解无法溯源（无 username/reason/UA 持久记录） | **P1** |
| 失败原因与来源无法聚合（僵尸账号、撞库、代理池识别） | P1 |
| 登录成功无审计（合规/异常登录时间不可查） | P2 |
| Redis 计数非持久（重启即失，历史攻击不可复盘） | P2 |

## 3. V6 Schema 设计（`V6__add_login_audit.sql`，append-only）

```sql
CREATE TABLE IF NOT EXISTS `login_audit` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT       DEFAULT NULL            COMMENT '用户 ID（登录成功/存在用户时；不存在=NULL）',
    `username`    VARCHAR(64)  NOT NULL                COMMENT '尝试登录的用户名（输入值）',
    `success`     TINYINT      NOT NULL DEFAULT 0      COMMENT '1=成功 0=失败',
    `ip_address`  VARCHAR(64)  DEFAULT NULL            COMMENT '客户端 IP（XFF/RemoteAddr）',
    `user_agent`  VARCHAR(512) DEFAULT NULL            COMMENT 'User-Agent',
    `reason`      VARCHAR(128) NOT NULL                COMMENT '结果原因（见 §4）',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '审计时间',
    PRIMARY KEY (`id`),
    KEY `idx_login_audit_user_time` (`user_id`, `created_at`),
    KEY `idx_login_audit_created_time` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录审计表';
```

**索引说明**：
- `idx_login_audit_user_time(user_id, created_at)`：按用户回溯登录历史（异常登录复核、锁定后追查）；
- `idx_login_audit_created_time(created_at)`：按时间窗扫描（暴力破解窗口分析、攻击溯源）。
两索引互补：用户维度（细查） + 时间维度（扫描）。

**约束**：不修改 V1-V5；migration integrity guard 放行（纯新增）。

## 4. 数据生命周期（reason 枚举）
| 场景 | success | user_id | username | reason |
|---|---|---|---|---|
| 登录成功 | 1 | 实际 userId | 输入值 | `SUCCESS` |
| 用户不存在 | 0 | **NULL** | 输入值 | `INVALID_CREDENTIAL` |
| 密码错误 | 0 | userId | 输入值 | `INVALID_PASSWORD` |
| 账户禁用 | 0 | userId | 输入值 | `ACCOUNT_DISABLED` |
| 账户锁定（Redis/DB） | 0 | userId/NULL | 输入值 | `ACCOUNT_LOCKED` |
| 限流（IP/配额） | 0 | NULL | 输入值 | `RATE_LIMIT` |

**不泄露要求**：reason 仅写入审计表；客户端响应保持 A-1 的统一 401「用户名或密码错误」/ 429 文案——审计细节绝不回传。

## 5. 性能方案
**推荐：异步写入（@Async + 独立线程池）**。
- 理由：登录为热路径且失败攻击流量大；同步写 DB 会被攻击流量放大拖慢认证主流程。
- 设计：`LoginAuditService.record(...)` 标注 `@Async`（独立 executor，核心线程固定）；写入失败仅 `log.error`，不影响认证结果（审计非阻断）。
- 备选（未采纳）：同步写（简单但热路径 DB 压力大）、RabbitMQ（本项目 MQ 用于业务消息，为审计引入队列过重）。
- 补充：`created_at` 由 DB 默认 CURRENT_TIMESTAMP，异步写入时间偏差可接受（秒级）。

## 6. 安全边界
- **禁止保存**：password、token、secret（明文或摘要均不落库）。
- **username 允许保存**：理由——登录尝试的用户名不是机密（攻击者本来就在尝试它；审计需要它做暴力破解归因与账号维度回溯）；不保存则无法回答「谁被尝试爆破」。这与 A-1「登录响应不泄露存在性」不冲突：审计表仅服务端可见，响应仍统一。

## 7. 测试计划（设计）
| 层 | 用例 |
|---|---|
| Migration | 全新 V1→V6（表/索引/默认值）；**V1-V5→V6 增量升级**（append-only，存量无影响） |
| Integration(MySQL) | 成功登录 → success=1 + userId + reason=SUCCESS；失败密码 → INVALID_PASSWORD；不存在用户 → user_id NULL + INVALID_CREDENTIAL；限流 → RATE_LIMIT（IP 超限） |
| Security | 审计表不含 password/token/secret；失败响应仍统一 401/429（不泄露 reason）；异步失败不影响登录 |
| 性能 | 高并发登录下审计异步不阻塞认证（@Async 线程池隔离） |

## 8. 实施范围
| 文件 | 变更 |
|---|---|
| `db/migration/V6__add_login_audit.sql` | **新增**（表 + 2 索引） |
| `entity/LoginAudit.java`、`mapper/LoginAuditMapper.java` | **新增** |
| `service/LoginAuditService.java` | **新增**（@Async 异步写入；`@EnableAsync` 或独立 executor） |
| `service/AuthService.java` | login 各分支（成功/密码错/不存在/禁用/锁定/IP 限流）调 `loginAuditService.record(...)` |
| `controller/AuthController.java` | 传 UA 与 IP（Controller 仅取 `getRemoteAddr()`/`getHeader("User-Agent")`） |
| 测试 | LoginAuditServiceTest + 集成（reason 矩阵）+ MySqlMigrationV6IT |
| 文档 | 本文件 + changelog 同步 |

**明确不改**：认证流程（A-1/A-2）、JWT（A-4）、注册（A-3）、权限模型（P1-01/RBAC）、V1-V5。

## 9. 结论
**PASS**——设计完整、append-only、性能与安全边界明确；审计 reason 枚举与 A-1/A-2 既有失败分类一致；异步写入不引入阻塞；无敏感字段。**允许进入实现阶段**（独立分支 + PR + 门禁：mvnw verify + MySQL IT V6 场景 + frontend build + migration guard），经监理批准后实施。

---

*只读设计审计（docs/security/P1-02-A-5-login-audit-design.md），未进入 git。执行完毕，停止，不进入实现阶段。*
