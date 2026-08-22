# Decision 0019: Flyway 版本化数据库迁移

| 属性 | 值 |
|------|-----|
| **状态** | ✅ 已采纳 |
| **决策日期** | 2026-08-17 |
| **决策者** | hula0710 + AI 助手 |
| **关联** | `backend/src/main/resources/db/migration/` / `compose.yml` / `application.yml` / 取代 ADR 0016 包装脚本 |

---

## 1. 背景

团队协作下，schema 长期靠 `init.sql`「全量初始化 + 仅空卷首次执行」：

- 队友 pull 新代码后，既有库**不自动获得 schema 变更**（无增量迁移）→ 两人数据库漂移；
- `archive/migrate_v1.1.sql` 靠「手工执行 + README 警告」维持，不可审计、不可自动；
- ADR 0016 的 charset-safe 初始化靠「mysql CLI 包装脚本」在 compose 首启时灌数据，
  机制正确但只能覆盖「全新初始化」一种场景。

## 2. 决策

| 项 | 决策 |
|----|------|
| 迁移框架 | **Flyway**（`flyway-core` + `flyway-mysql`，版本由 Spring Boot 3.5 BOM 管理） |
| 迁移目录 | `backend/src/main/resources/db/migration/`：`V1__baseline.sql`（=原 init.sql：schema+角色+admin）、`V2__seed_test_data.sql`（=原 seed_test_data.sql：演示数据）【2026-08-18 修订：V2 已退役，演示种子移出迁移链，见 §5】 |
| 执行时机 | 应用启动时自动迁移（`spring.flyway.enabled=true`）；prod 容器内 backend 启动即迁移 |
| 既有库策略 | `baseline-on-migrate: true` + `baseline-version: 2`：非空且无 flyway_schema_history 的库基线到 V2（跳过 V1/V2 重放）；全新空库从 V1 正常执行 |
| compose 职责收敛 | mysql 容器仅建空库（`MYSQL_DATABASE=reboot`），**不再加载任何 SQL**；删除 `mysql/init/01-init-db.sh` 与 `/init-sql` 挂载 |
| 测试隔离 | `application-test.yml` 关闭 Flyway（`spring.flyway.enabled=false`），测试不触发迁移 |
| charset | 迁移经 **JDBC**（Connector/J 9 默认 UTF-8）执行，中文种子数据无双重编码——**取代 ADR 0016 的 CLI 包装脚本**（该问题根源在 CLI 无 charset 参数，JDBC 路径无此问题） |

## 5. 修订（2026-08-18）：演示种子与生产 Flyway 隔离（P0）

### 5.1 Context

`V2__seed_test_data.sql` 位于 `classpath:db/migration`，任何**全新空库**（含全新生产库）启动时
Flyway 都会按 V1 → V2 → V3 顺序执行，自动灌入 20 个测试用户、50 台测试设备、12 条测试告警等演示数据。
`baseline-version: 2` 只对「非空且无 flyway_schema_history」的既有库生效（基线到 2 跳过重放），
对全新库不生效——因此生产环境存在被自动灌入 Demo 数据的部署安全边界问题（P0）。

### 5.2 Problem

> 全新生产数据库可能被 Flyway 自动灌入演示/测试数据（测试用户/设备/告警）。

### 5.3 Decision

| 项 | 决策 |
|----|------|
| 迁移链收口 | **删除** `V2__seed_test_data.sql`；`db/migration/` 只保留正式迁移（当前 V1 baseline + V3 operation_log check 扩展；未来变更仍走 `V###__*.sql`） |
| 演示种子唯一事实源 | 移至 `backend/src/main/resources/db/seed/dev/seed_demo_data.sql`——位于 Flyway locations 之外，任何环境启动都不会被扫描执行 |
| 执行方式 | **显式动作**：`./scripts/seed-dev.sh`（读取根目录 `.env` 连接参数，`--default-character-set=utf8mb4` 防乱码）；不做 profile 自动触发（避免 profile 配错导致 Demo 数据重入生产） |
| 幂等 | seed 全部 INSERT 按业务键 `NOT EXISTS` 守卫（用户按 username、设备按 device_code、告警/采集/日志按复合业务键），可安全重复执行；设备外键按 device_code 解析，兼容已有开发库的任意自增 ID |
| 既有库兼容 | 新增 `spring.flyway.ignore-migration-patterns: "*:missing"`：容忍「已执行过旧 V2」的库（flyway_schema_history 含 V2 行但文件已退役）继续启动；仅放行 missing 状态，其余校验保持默认严格 |
| baseline | `baseline-on-migrate: true` + `baseline-version: 2` 维持不变（V2 退役后等价于跳过 V1 重放） |
| 必需初始化 | 默认角色 + admin 账户仍在 `V1__baseline.sql`（正式系统初始化，生产需要），与演示种子严格分离 |

### 5.4 Alternatives（未采纳）

| 方案 | 未采纳原因 |
|------|------------|
| 按 Profile 隔离（审查报告建议 `db/migration/dev/`） | 依赖 `spring.profiles.active` 正确性，配错即 Demo 重入生产，不可审计 |
| 保留 V2 文件但清空内容 / 重命名 | 已执行过 V2 的库将触发 checksum/描述不匹配，比「missing」更严重 |
| 新增 V4 迁移删除 Demo 数据 | 全新生产库仍会先插入再删除（瞬时 Demo 数据），语义不干净 |
| 不配置 `ignore-migration-patterns`，要求既有库重建 | 已有 dev/副本库需手工 `down -v` 重灌，违背「不因本次修改破坏既有库」要求 |

### 5.5 Consequences

- **生产**：全新库只执行 V1+V3，无 Demo 用户/设备/告警；必需角色 + admin 正常初始化；
- **开发**：正式迁移后显式 `./scripts/seed-dev.sh` 生成完整 Demo 数据，可重复执行；
- **已有开发库（无 flyway 历史）**：baseline@2 跳过 V1 重放，V3 正常应用，业务数据零影响；
- **已有执行过旧 V2 的库**：靠 `*:missing` 平滑升级，既有 Demo 数据保留、不重复、不删除；
- **测试**：`FlywayProductionSeedIsolationTest`（H2 源码级契约，零外部依赖）+ `DevSeedDemoDataTest`（H2 验证 seed 加载/幂等）+ `MySqlSeedIsolationIT`（`RUN_MYSQL_IT=true` 对真实 MySQL 四场景端到端）；
- **风险**：`*:missing` 会容忍任何未来被删除的迁移（仅 missing 状态）；对策——迁移退役必须走 ADR 流程并在 `ignore-migration-patterns` 注释中说明。

## 3. 备选方案（未采纳）

| 方案 | 未采纳原因 |
|------|------------|
| 维持 init.sql 全量 + archive 手工迁移 | 本次要解决的问题本身 |
| Liquibase | Flyway 更轻、与 Spring Boot 集成默认、学习成本低；项目规模无需 Liquibase 的 XML/YAML 灵活性 |
| 继续 ADR 0016 CLI 包装脚本 | 只解决"全新初始化"；无法覆盖既有库增量升级 |

## 4. 影响与验证

- **增量能力**：未来 schema 变更 = 新增 `V###__xxx.sql`，所有环境的库自动升级，杜绝漂移；
- **回滚/审计**：`flyway_schema_history` 表记录每次迁移，可审计；
- **既有库迁移**：无需 `down -v`（基线到 V2 自动跳过）；但演示数据如已乱码（ADR 0016 前初始化），仍建议 `down -v` 重灌一次；
- **E2E 验证**：迁移逻辑经 `./mvnw test`（H2 源码级契约测试）与 `MySqlSeedIsolationIT`（`RUN_MYSQL_IT=true` 对真实 MySQL 四场景验证）覆盖；
  **生产库不含演示数据**（全新库仅 V1+V3），演示数据仅经 `scripts/seed-dev.sh` 显式灌入。
