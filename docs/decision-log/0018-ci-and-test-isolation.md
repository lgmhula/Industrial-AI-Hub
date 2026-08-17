# Decision 0018: CI 门禁 + 测试 DB 隔离（H2）

| 属性 | 值 |
|------|-----|
| **状态** | ✅ 已采纳 |
| **决策日期** | 2026-08-17 |
| **决策者** | hula0710 + AI 助手 |
| **关联** | `.github/workflows/ci.yml` / `application-test.yml` / `frontend/.nvmrc` / ADR 0017 |

---

## 1. 背景

团队协作模拟下出现两个"只在本机成立"的缺口：

1. **无 CI 门禁**：`.github/workflows` 仅有 Qodana，无 build+test 流水线——PR 合并没有自动验证，
   "我机器上能跑"无法转化为"任意机器都能跑"；
2. **测试依赖本机约定**：`ApplicationContextLoadTest` 注释声明"DataSource 仍需可达的 MySQL"，
   测试通过依赖 HikariCP 懒连接"碰巧不连库"，在无 MySQL 的机器（如 CI runner）上是隐性风险。

## 2. 决策

| 项 | 决策 |
|----|------|
| CI 流水线 | GitHub Actions `ci.yml`：`push main` / `pull_request` / `workflow_dispatch` 触发；backend（Temurin 25 + `./mvnw -B test`）与 frontend（Node 22 + `npm ci && npm run build`）两个并行 job；配合 ADR 0017 分支保护形成"全绿才合并"门禁 |
| 测试 DB 隔离 | 引入 **H2**（`<scope>test</scope>`，版本由 Spring Boot 3.5 BOM 管理）：`application-test.yml` 覆写 `datasource.url = jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1` + `org.h2.Driver`；测试从此**零外部依赖**（Redis/RabbitMQ 已 autoconfigure 排除，MySQL 由 H2 替代） |
| Node 版本锁定 | `frontend/.nvmrc = 22`（与副本 Node 22.20 对齐），杜绝 Node 版本漂移 |
| 协作产物 | `.github/PULL_REQUEST_TEMPLATE.md`（自测清单）+ `CONTRIBUTING.md`（参与流程） |

## 3. 备选方案（未采纳）

| 方案 | 未采纳原因 |
|------|------------|
| Testcontainers 起真实 MySQL | 本机无 Docker 运行时会破坏本地测试；CI 增加启动开销；收益小于成本 |
| 继续"无 DB 碰巧通过" | 隐性风险，CI runner 上不可控 |
| Flyway 提前到本 ADR | 涉及 compose/SQL 组织方式重构，独立决策（见 ADR 0019） |

## 4. 影响与验证

- 验证：本机 `./mvnw test`（H2 生效）→ 89/89 全绿，且明确不再依赖本机 MySQL；
- CI 首次运行需在 PR 合并后触发；`workflow_dispatch` 支持手动重跑；
- 依赖变更：新增 `com.h2database:h2`（test scope），已按 AGENTS.md §5 在本 ADR 记录理由。
