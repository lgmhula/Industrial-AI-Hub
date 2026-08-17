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
| 迁移目录 | `backend/src/main/resources/db/migration/`：`V1__baseline.sql`（=原 init.sql：schema+角色+admin）、`V2__seed_test_data.sql`（=原 seed_test_data.sql：演示数据） |
| 执行时机 | 应用启动时自动迁移（`spring.flyway.enabled=true`）；prod 容器内 backend 启动即迁移 |
| 既有库策略 | `baseline-on-migrate: true` + `baseline-version: 2`：非空且无 flyway_schema_history 的库基线到 V2（跳过 V1/V2 重放）；全新空库从 V1 正常执行 |
| compose 职责收敛 | mysql 容器仅建空库（`MYSQL_DATABASE=reboot`），**不再加载任何 SQL**；删除 `mysql/init/01-init-db.sh` 与 `/init-sql` 挂载 |
| 测试隔离 | `application-test.yml` 关闭 Flyway（`spring.flyway.enabled=false`），测试不触发迁移 |
| charset | 迁移经 **JDBC**（Connector/J 9 默认 UTF-8）执行，中文种子数据无双重编码——**取代 ADR 0016 的 CLI 包装脚本**（该问题根源在 CLI 无 charset 参数，JDBC 路径无此问题） |

## 3. 备选方案（未采纳）

| 方案 | 未采纳原因 |
|------|------------|
| 维持 init.sql 全量 + archive 手工迁移 | 本次要解决的问题本身 |
| Liquibase | Flyway 更轻、与 Spring Boot 集成默认、学习成本低；项目规模无需 Liquibase 的 XML/YAML 灵活性 |
| 继续 ADR 0016 CLI 包装脚本 | 只解决"全新初始化"；无法覆盖既有库增量升级 |

## 4. 影响与验证

- **增量能力**：未来 schema 变更 = 新增 `V3__xxx.sql`，所有环境的库自动升级，杜绝漂移；
- **回滚/审计**：`flyway_schema_history` 表记录每次迁移，可审计；
- **既有库迁移**：无需 `down -v`（基线到 V2 自动跳过）；但演示数据如已乱码（ADR 0016 前初始化），仍建议 `down -v` 重灌一次；
- **E2E 验证**：本机无 Docker，迁移逻辑经构建 + 89 测试（test profile 关 Flyway）验证；
  **真实 MySQL 端到端迁移需在副本/部署机验证**（全新 `down -v` 首启 → 7 表 + 21 用户 + 中文可读）。
