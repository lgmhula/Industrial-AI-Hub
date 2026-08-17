# Contributing — Industrial AI Hub

> 本项目的协作规则唯一权威来源是 [`AGENTS.md`](AGENTS.md)（§4 行为约束 + §4.4 Git 工作流纪律）与本文件。
> 快速索引：`AGENTS.md`（对齐锚点）→ `docs/SETUP.md`（本地跑起来）→ `docs/decision-log/`（决策理由）→ `docs/reports/deploy-runbook-new-device.md`（部署坑位）。

## 参与流程（ADR 0017）

1. **拉取并验证环境**：`git clone` → 按 `docs/SETUP.md` 起本地环境；
2. **建分支**：`git checkout -b feat/<主题>`（或 `fix/*` / `docs/*` / `chore/*` / `ai/*`）——**禁止直推 main**；
3. **自测**：后端 `./mvnw test`（89/89，H2 隔离无需本机 MySQL）+ 前端 `npm ci && npm run build`；
4. **提交**：描述格式 `Day XXX: 简要说明`；禁止夹带明文密钥、AI/IDE 产物、本地 override；
5. **PR**：push 分支 → 开 PR（模板自带自测清单）→ CI 全绿 → 维护者审核 → Squash merge → 删分支；
6. **发布**：验收态由维护者打 `v2.x.y` tag，部署方锁定 tag。

## 工程约束（摘要，全文见 AGENTS.md）

- JDK 25 LTS / Maven Wrapper 3.9.6 / Spring Boot 3.5.0 / MyBatis 3.0.5（技术栈锁定表，未经讨论不升级）
- 分层：Controller → Service → Mapper，不跳层；构造器注入；统一 `ApiResponse<T>`
- 密钥：`.env` 唯一事实源；`application*.yml` 禁止硬编码密钥；敏感变量 fail-fast（ADR 0015）
- 数据库：schema SSOT 为 `backend/src/main/resources/sql/init.sql`（变更同步 `docs/decision-log/0012`）；禁止手工改库
- 决策记录：任何架构级变更必须落 `docs/decision-log/00XX-*.md`
- 换行符：`.gitattributes` 已强制 LF，不要手工改行尾
