# Phase 3-A Plan Audit — Round 2

**审计角色**: Infrastructure Architect (只读审计)  
**审计日期**: 2026-08-03 22:48  
**审计对象**: `docs/plans/phase3-a-infrastructure-stabilization.md` (commit `cbb1f47`)  
**前置审计**: Round 1 (`9b72a17` — `docs/reports/phase3-a-plan-audit.md`)  
**冻结基线**: `v2.1.0` (tag object `732c91f`, commit `ec9a158`)  
**审计模式**: 只读 — 不修改代码/配置，不移除 Git Tag，不调整冻结状态

---

## 最终裁决: **CONDITIONAL PASS** (有条件通过 — 残留 1 CRITICAL)

> Round 1 的 5 项阻断问题中 3 项已修复、1 项部分修复、1 项未修复。  
> CRITICAL-1 (.gitignore 阻断 rabbitmq.conf) 在修订中遗漏，必须在 T1 执行前修正。

---

## 一、Round 1 问题修复追踪

| # | Round 1 问题 | 严重性 | 修复状态 | 验证详情 |
|---|------|:---:|:---:|------|
| R1-C1 | T1 新建 `rabbitmq/rabbitmq.conf` 被 `.gitignore` 阻断 | CRITICAL | **未修复** | `git check-ignore -v rabbitmq/rabbitmq.conf` 仍返回 `.gitignore:26:rabbitmq/` → exit 0。计划 T1 未提及 `.gitignore` negation 规则 |
| R1-C2 | T1 遗漏 `git rm --cached` 步骤 | CRITICAL | **已修复** | T1 Step 1 显式包含 `git rm --cached -r rabbitmq/.erlang.cookie rabbitmq/mnesia/`，并新增 Round 0 执行步骤 |
| R1-H1 | G3 混淆 bind-mount / named volume 的 `down -v` 行为 | HIGH | **部分修复** | T1 验证新增 `down -v && up` 测试 named volume 重建；但 G3 标准本身未拆分，bind-mount 服务（MySQL/Redis/ES 等）的 `down -v` 行为仍未覆盖 |
| R1-H2 | T5 新增 springdoc 依赖未计划 ADR | HIGH | **已修复** | T5 新增"前置条件: 需先创建 ADR 0013"，ADR 内容详细（候选方案/选型理由/版本锁定/profile 策略），执行拓扑中 ADR-0013 先于 T5 |
| R1-H3 | T4 涉及冻结代码修改，未明确分支策略 | HIGH | **已修复** | §5 新增完整分支策略：从 `v2.1.0` tag 切出 `codex/phase-3a-infra-stabilization`，禁止 force push/rebase/tag 修改，G7 验证 tag 不可变性 |

**修复得分**: 3/5 完全修复，1/5 部分修复，1/5 未修复。

---

## 二、六大重点验证

### 1. RabbitMQ named volume 迁移方案完整性 — **不完整 (CRITICAL 残留)**

**已覆盖**:
- 三层修复架构清晰（git 跟踪层 → 磁盘清理层 → 权限管理层）
- named volume 声明 + 配置文件挂载 + healthcheck start_period 完整
- 验证方式包含 `down -v → up → healthy` 循环
- 设计原则文档化（仅 RabbitMQ 使用 named volume，其他保留 bind-mount）

**未覆盖 (CRITICAL)**:
- `rabbitmq/rabbitmq.conf` 创建后无法被 git 跟踪 — `.gitignore` 第 26 行 `rabbitmq/` 覆盖整个目录
- `git check-ignore -v rabbitmq/rabbitmq.conf` 确认：文件被忽略，exit 0
- T1 修改范围列表和 T2.2 `.gitignore` 复核均未提及此问题
- **影响链**: `git clone` → `rabbitmq.conf` 不存在 → compose 挂载 `./rabbitmq/rabbitmq.conf` → Docker 创建目录而非文件 → RabbitMQ 启动失败

**修复建议** (必须在 T1 执行前嵌入计划):
```gitignore
# .gitignore 修改 — 将 rabbitmq/ 改为
rabbitmq/*
!rabbitmq/rabbitmq.conf
```

### 2. T1 执行顺序运行态竞争风险 — **存在 MODERATE 风险**

**当前执行顺序**:
```
Round 0: T1 Step 1 (git rm --cached)     ← 仅 git index，无运行态影响
Round 1: T1 Step 2 (rm -rf mnesia/cookie) ← 磁盘删除
        T1 Step 3 (创建 rabbitmq.conf)
        T1 Step 4 (修改 compose.yml)
        T1 Step 5 (healthcheck)
```

**风险**: T1 Step 2 删除 `rabbitmq/mnesia/` 和 `.erlang.cookie` 时，若 RabbitMQ 容器正在运行（即便在重启循环中），Erlang 可能：
- 尝试读取已删除的 mnesia → 崩溃
- 自动创建新的 `.erlang.cookie`（随机内容，权限 0644）→ 与 Step 3 的 named volume 方案冲突
- mnesia 目录被部分重建 → 残留脏数据

**当前环境验证**: `docker compose ps` 显示仅 MySQL 运行，RabbitMQ 未运行 — 当前无竞争。但计划作为执行文档，必须覆盖未来执行者可能遇到的全服务运行场景。

**修复建议**: T1 Step 2 前新增 Step 1.5:
```bash
docker compose stop rabbitmq   # 或 docker compose down
# 确认容器停止后再执行磁盘清理
docker compose ps rabbitmq     # 确认 STATUS 为空或 Exited
```

### 3. MinIO 固定版本策略数据兼容风险 — **无风险**

**验证结果**:
- 当前 `minio/minio:latest` 本地缓存解析为 `RELEASE.2025-09-07T16-13-09Z`（2025-09-08 构建）
- 计划 T2.3 锁定为 `minio/minio:RELEASE.2025-09-07T16-13-09Z` — 与当前版本一致，无版本切换
- T2.1 同时清理 `minio/.minio.sys/` 元数据残留 — 元数据将重新生成，无格式冲突
- MinIO 对象数据（buckets/files）在版本间保持向后兼容

**结论**: 版本固定策略安全，无数据兼容风险。仅需注意未来升级时需同步更新标签。

### 4. Phase 3-A 分支策略对 v2.1.0 的保护 — **充分**

**验证结果**:

| 检查项 | 结果 |
|--------|:---:|
| v2.1.0 tag 类型 | annotated tag（tag object `732c91f` → commit `ec9a158`） |
| v2.1.0 tag 指向 | `ec9a158f0f87d8b38bd4cbd3686196581356172e` ✅ |
| 分支策略 §5 存在 | ✅ — 从 `v2.1.0` tag 切出 `codex/phase-3a-infra-stabilization` |
| 禁止 force push/rebase | ✅ — §5.2 明确列出 |
| 禁止修改 v2.1.0 tag | ✅ — §5.2 明确列出 |
| G7 验证 tag 不可变性 | ✅ — `git tag -l 'v2.1.0'` 仍指向 `ec9a158` |
| G8 验证合入无冲突 | ✅ — `git merge --no-ff` dry-run |
| ec9a158 → HEAD diff | 仅 6 个文档文件变更，无代码/配置修改 ✅ |

**结论**: 分支策略设计完善，v2.1.0 冻结基线得到充分保护。

**次要观察**: 分支从 `v2.1.0` 切出，意味着 Phase 3-A 计划文档本身（commit `e8165b2`/`cbb1f47`）不在分支上。这是可接受的 — 计划文档是指导性文件，不需要在执行分支上存在。但执行者需在 main 分支查看计划。

### 5. G9 `:latest` 扫描范围合理性 — **范围过宽 (HIGH)**

**验证结果**:

G9 定义: `rg ':latest' compose.yml` → 0 匹配

实际扫描 `compose.yml` 中 `:latest` 出现:

| 行号 | 内容 | 类型 | 是否需修复 |
|:---:|------|------|:---:|
| 117 | `image: minio/minio:latest` | 外部拉取镜像 | 是 — T2.3 修复 |
| 287 | `image: industrial-ai-hub-backend:latest` | **本地构建镜像**（有 `build:` 段） | **否** — 本地构建，`:latest` 是惯例标签 |

**问题**: T2.3 修复 MinIO 后，G9 扫描仍会匹配 `industrial-ai-hub-backend:latest`（第 287 行），返回 1 匹配而非 0。G9 的"0 匹配"期望在 Phase 3-A 完成后仍无法满足。

**修复建议**: G9 验证方式调整为:
```bash
# 仅扫描外部拉取镜像（排除有 build: 段的本地构建服务）
rg ':latest' compose.yml | grep -v 'industrial-ai-hub-backend' → 0 匹配
```
或更通用:
```bash
# 扫描所有 image: 行，排除紧邻 build: 段的本地构建镜像
rg 'image:.*:latest' compose.yml → 人工复核每条匹配是否有对应 build: 段
```

### 6. 执行阶段阻断项 — **1 CRITICAL + 1 HIGH**

**仍存在的阻断项**:

| # | 阻断项 | 严重性 | 阻断任务 | 修复方式 |
|---|--------|:---:|:---:|------|
| B1 | `.gitignore` `rabbitmq/` 阻断 `rabbitmq.conf` | CRITICAL | T1 | 添加 `!rabbitmq/rabbitmq.conf` negation 规则 |
| B2 | G9 `:latest` 扫描假阳性 | HIGH | G9 验收 | 排除本地构建镜像 |

**已解除的阻断项** (Round 1 → Round 2):
- ~~CRITICAL: T1 遗漏 `git rm --cached`~~ → 已修复（T1 Step 1）
- ~~HIGH: T5 缺少 ADR~~ → 已修复（ADR-0013 前置）
- ~~HIGH: T4 缺少分支策略~~ → 已修复（§5 分支策略）

---

## 三、新发现项

### N1: G6-G9 文档位置错位 [LOW]

G6-G9 标准追加在文件末尾（第 552-555 行），位于 §8 风险表格和目标声明之后，未整合到 §3.2 验收标准表格中。这导致：
- 验收标准分散在两处，阅读时容易遗漏 G6-G9
- §3.2 表格仍显示 G1-G5，给人"只有 5 条验收标准"的印象

**建议**: 将 G6-G9 移入 §3.2 表格，统一编号。

### N2: Round 1 推荐的 G6-G9 未被采纳 [MODERATE]

Round 1 审计建议的 4 项验收标准未被采纳，计划使用了不同的 G6-G9:

| Round 1 建议 | 用途 | 计划实际 G6-G9 | 用途 |
|------|------|------|------|
| G6: `down`（不带 -v）后数据持久性 | 验证正常重启不丢数据 | G6: RabbitMQ git 跟踪归零 | 验证 git rm --cached 生效 |
| G7: 端到端冒烟测试（登录 API） | 验证全链路可用 | G7: v2.1.0 tag 不可变 | 验证冻结基线安全 |
| G8: 干净 clone 可部署 | 验证从零部署能力 | G8: 分支合入无冲突 | 验证合入可行性 |
| G9: ES 非正常关闭可恢复 | 验证 node.lock 不阻断 | G9: `:latest` 扫描 | 验证版本固定 |

计划的 G6-G9 有价值（git 跟踪、tag 保护、合入验证、版本固定），但 Round 1 建议的 4 项（数据持久性、端到端冒烟、干净 clone、ES 恢复）同样重要，特别是：
- **端到端冒烟测试** — 验收标准仅检查容器 healthy，未验证 API 可用性
- **干净 clone 部署** — 最终的可重复部署证明

**建议**: 将 Round 1 建议的 4 项追加为 G10-G13，与现有 G6-G9 并存。

### N3: `rabbitmq.conf` 中 `cluster_formation.random_node_name` 键未验证 [LOW]

T1 Step 3 的 `rabbitmq.conf` 内容:
```ini
cluster_formation.random_node_name = false
nodename = rabbit@localhost
```

`cluster_formation.random_node_name` 非标准 RabbitMQ 配置键。RabbitMQ 4.0 的 `cluster_formation.*` 键族用于 peer discovery，不含 `random_node_name`。此键可能被忽略（不影响启动），但属于无效配置。

**建议**: 执行 T1 前验证此键是否为 RabbitMQ 4.0 有效配置，或直接移除（`nodename` 已足够锁定节点名）。

### N4: `nodename = rabbit@localhost` 容器适配 [LOW]

Docker 容器内 `localhost` 绑定 `127.0.0.1`，Erlang 分布式协议使用此地址。单节点模式下通常可行，但更常规的 Docker 实践是：
```yaml
# compose.yml
services:
  rabbitmq:
    hostname: rabbitmq
```
让 RabbitMQ 使用默认节点名 `rabbit@rabbitmq`，无需 `rabbitmq.conf`。

**建议**: 评估是否可用 `hostname: rabbitmq` 替代 `rabbitmq.conf`，简化配置链（同时解决 .gitignore 阻断问题 — 不再需要在 `rabbitmq/` 目录下创建配置文件）。

---

## 四、验收标准完整性评估

| 标准 | Round 1 评估 | Round 2 评估 | 变化 |
|:---:|:---:|:---:|:---:|
| G1 | 需调整（120s 冷启动不足） | 未调整 | 无变化 |
| G2 | 通过 | 通过 | — |
| G3 | 需拆分 bind-mount/named volume | 部分修复（T1 验证覆盖 named volume，标准未拆分） | ↗ |
| G4 | 通过 | 通过 | — |
| G5 | 通过 | 通过 | — |
| G6 | — | 新增（git 跟踪归零） | 新增 |
| G7 | — | 新增（tag 不可变） | 新增 |
| G8 | — | 新增（合入无冲突） | 新增 |
| G9 | — | 新增（:latest 扫描），但范围过宽 | 新增（需修正） |

**缺失标准** (Round 1 建议，仍未采纳):
- `down`（不带 -v）后数据持久性验证
- 端到端冒烟测试（登录 API → 设备列表）
- 干净 clone 可部署验证
- ES 非正常关闭恢复验证

---

## 五、总结

### 修复进度

```
Round 1 → Round 2 修复轨迹:

CRITICAL ×2 ──→ 1 FIXED (git rm --cached) + 1 NOT FIXED (.gitignore)
HIGH    ×3 ──→ 2 FIXED (ADR + branch) + 1 PARTIAL (G3)
新增    ×2 ──→ 1 HIGH (G9 false positive) + 1 MODERATE (T1 race)
```

### 执行前必须修正的阻断项

| # | 阻断项 | 严重性 | 修正位置 | 修正方式 |
|---|--------|:---:|------|------|
| B1 | `.gitignore` 阻断 `rabbitmq.conf` | CRITICAL | T1 + .gitignore | 添加 `!rabbitmq/rabbitmq.conf` negation 规则，或改用 `hostname: rabbitmq` 替代 `rabbitmq.conf` |
| B2 | G9 `:latest` 假阳性 | HIGH | §3.2 G9 | 排除本地构建镜像 (`industrial-ai-hub-backend`) |

### 建议修正（不阻断但推荐）

| # | 建议项 | 严重性 | 说明 |
|---|--------|:---:|------|
| R1 | T1 Step 2 前新增容器停止步骤 | MODERATE | 防止运行态竞争 |
| R2 | 追加 G10-G13（数据持久/E2E/clone/ES 恢复） | MODERATE | 补全生产级可恢复性验证 |
| R3 | G6-G9 移入 §3.2 表格 | LOW | 文档结构一致性 |
| R4 | G1 超时调整 120s → 180s | LOW | 冷启动余量 |
| R5 | 验证或移除 `cluster_formation.random_node_name` | LOW | 配置有效性 |

### 裁决理由

计划修订质量显著提升 — 分支策略、ADR 前置、git rm --cached 步骤均为实质性改进。但 **CRITICAL-1 (.gitignore 阻断 `rabbitmq.conf`)** 是硬阻断项：不修正则 T1 执行后 `rabbitmq.conf` 不在版本控制中，新鲜 clone 的 `docker compose up` 将失败，直接违反 G1 和 G8 验收标准。

> **建议**: 修正 B1 + B2 后，计划可进入执行阶段。修正 R1-R5 可在执行过程中同步完善。

---

> **审计声明**: 本审计为只读操作，未修改任何代码/配置文件，未移动 Git Tag，未调整 V2.1.0 冻结状态。  
> **验证环境**: v2.1.0 tag → `ec9a158`，HEAD → `cbb1f47`，工作区 clean。
