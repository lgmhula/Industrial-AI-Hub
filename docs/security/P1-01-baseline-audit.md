# P1-01 Baseline Audit — Horizontal Authorization 实施前基线

> 分支：`feat/p1-site-authorization-model`（基于 `main` @ `1fbfb8c`）
> 日期：2026-08-23 | 类型：只读基线扫描（本文件为 Phase 1 产物）

---

## 1. 当前数据库（Flyway）版本

| 项 | 现状 |
|---|---|
| 迁移目录 | `backend/src/main/resources/db/migration/` |
| 现有迁移 | `V1__baseline.sql`（7 表 + 默认角色 + admin）、`V3__operation_log_check_types.sql`（operation_type CHECK 扩展） |
| 已退役 | `V2__seed_test_data.sql`（Phase 3 P0 按 ADR 0019 §5 退役，不在迁移链） |
| 当前最高版本 | **V3**（下一个正式迁移为 **V4**） |
| 迁移链规则 | 只增不删；migration-integrity CI guard 拦截未治理的 D/R（唯一允许的退役已登记 `APPROVED_RETIRED_MIGRATIONS`） |
| 兼容配置 | `baseline-on-migrate: true` + `baseline-version: 2` + `ignore-migration-patterns: "*:missing"` |

## 2. 当前资源模型（无归属模型）

```
User ── user_role ── Role (ADMIN/OPERATOR/VIEWER，硬编码枚举，无 permission 表)
        │
        └─ 无 Owned Resource（无 owner_id / site_id / tenant_id）

Device ── device_id ── Alarm
       └─ device_id ── DeviceData
```

- `device` 表：无 `owner_id` / `site_id`（全局共享资源）。
- `alarm` / `device_data`：经 `device_id` 关联 device（软外键，无 FK 约束——与 V1 全表无 FK 的既有约定一致）。
- `Site` / `Tenant`：不存在。
- 鉴权：`JwtAuthFilter` 解析 JWT → request attribute（`userId`/`username`/`roles`）→ `AuthInterceptor` 按 `@RequireRole` 做角色判定（`RoleEnum.isAtLeast`）。**无资源归属维度**（P1-01 审计结论：系统性 BOLA/IDOR 缺口）。

## 3. 需要修改文件清单（本阶段 + 后续授权逻辑阶段）

### 本阶段（P1-01 实施 — migration-only）
| 文件 | 动作 |
|---|---|
| `backend/src/main/resources/db/migration/V4__add_site_scoping.sql` | **新增**：site / user_site 表 + 默认站点 + device.site_id 回填 |
| `backend/src/test/java/dev/reboot/db/FlywayProductionSeedIsolationTest.java` | **微调**：`migrationDirectory_containsOnlyFormalMigrations` 的期望列表加入 V4（V4 为正式迁移，属契约演进，非 seed） |
| `backend/src/test/java/dev/reboot/db/MySqlMigrationV4IT.java` | **新增**（门控）：真实 MySQL 验证「全新库 V1-V4」「已有 V1-V3 history 升级 V4」 |
| `docs/p1-01-baseline-audit.md` | **新增**（本文件） |

### 下一阶段（授权逻辑，待指令，不在本阶段实施）
| 文件 | 动作 |
|---|---|
| `backend/src/main/java/dev/reboot/entity/Site.java` / `UserSite.java` | 新增实体 |
| `backend/src/main/java/dev/reboot/mapper/SiteMapper.java` / `UserSiteMapper.java` | 新增 Mapper |
| `DeviceService` / `AlarmService` / `DeviceDataService` | 站点断言 + 列表 SQL 过滤 |
| `DeviceController` / `AlarmController` / `DeviceDataController` | 传递当前 userId |
| 对应 Mapper SQL（Device/Alarm/DeviceData） | 追加站点条件 |
| `db/seed/dev/seed_demo_data.sql` | 默认站点 user_site 分配（幂等） |
| `docs/decision-log/0020-*.md` + AGENTS.md + sql/README | 文档同步（ADR） |

## 4. 潜在冲突点

1. **P0 契约测试的迁移列表断言**：`FlywayProductionSeedIsolationTest.migrationDirectory_containsOnlyFormalMigrations` 断言 `db/migration` 恰好为 `[V1, V3]`。新增 V4 会使该断言失败 → 需同步期望列表（V4 是正式迁移，属契约演进，不改变「无 seed」的契约本质）。
2. **checksum**：V4 为全新版本，不触碰 V1/V3，不改变任何已执行迁移的 checksum。
3. **`device.site_id` NOT NULL 回填**：既有库（如本机 `reboot` 库、Windows 副本）已有设备数据 → V4 必须先建默认站点、回填全部设备、再收紧 NOT NULL；顺序颠倒会导致迁移失败。**实现决议**：NOT NULL 追加 `DEFAULT 1`（DEFAULT 站点由 V4 在空 site 表上首次插入，id 恒为 1），使现有 dev seed 等未携带 site_id 的 INSERT 路径无需修改即可自动归属默认站点——迁移自身兼容，避免在 migration-only 阶段改 seed。
4. **Flyway 单次执行语义**：MySQL DDL 无 `IF NOT EXISTS`（ADD COLUMN/ADD INDEX），依赖 Flyway「每个版本只执行一次」保证幂等；表/默认站点用 `CREATE TABLE IF NOT EXISTS` + `ON DUPLICATE KEY UPDATE` 防御性兜底。
5. **`baseline-version: 2` 既有库**：本机 dev 库（无 flyway history）将在下次启动时 baseline@2 → 执行 V3 → V4，路径与全新库一致（V4 增量可执行）。
6. **H2 测试**：always-on 测试不执行 Flyway（test profile 关闭），H2 夹具 schema 独立于迁移目录 → V4 不影响 H2 测试运行；但 `MySqlSeedIsolationIT` 的旧 V2 模拟链（临时目录 V1+V2-proxy+V3）不受影响，仍需 4/4 通过。

## 5. 结论

当前基线干净、版本链确定（V1/V3 → V4），资源模型为「纯 RBAC、零归属」。V4 迁移是本阶段唯一业务变更点；业务代码（Service/Controller/Mapper/JWT/前端/seed/ADR）一律不修改，等待下一阶段授权逻辑指令。
