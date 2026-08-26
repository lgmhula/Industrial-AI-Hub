# Phase 3-A Plan Audit — Infrastructure Architect Review

**审计角色**: Infrastructure Architect (只读审计)  
**审计日期**: 2026-08-03 22:31  
**审计对象**: `docs/plans/phase3-a-infrastructure-stabilization.md`  
**冻结基线**: `v2.1.0` (commit `ec9a158`)  
**审计模式**: 只读 — 不修改代码/配置，不移除 Git Tag，不调整冻结状态

---

## 最终裁决: **CONDITIONAL PASS** (有条件通过)

> 计划方向正确，任务分解合理，但存在 **2 项 CRITICAL** 和 **3 项 HIGH** 问题必须在执行前修正。  
> 未修正前禁止启动 T1 执行 — 否则将引入比 V2.1.0 更严重的新回归。

---

## 一、审计结论总览

| 维度 | 结论 | 说明 |
|------|:---:|------|
| 任务优先级 | **需调整** | T4(JWT P0) 可提前并行；可重复部署优先方向正确 |
| 基础设施风险覆盖 | **不充分** | 遗漏 .gitignore 阻断、MinIO 版本锁定、ES 锁文件循环处理 |
| 验收标准 | **需强化** | G3 混淆 bind-mount/named volume 行为；缺少端到端冒烟测试 |
| 冻结基线安全 | **有风险** | T5 缺 ADR；T4 缺分支策略；计划需明确"在 v2.1.0 之上叠加"语义 |

---

## 二、高风险项清单（附修复建议）

### CRITICAL-1: T1 新建 `rabbitmq/rabbitmq.conf` 被 `.gitignore` 阻断

**严重性**: CRITICAL — 将导致新鲜 clone 无法启动 RabbitMQ

**现象**:
- `.gitignore` 第 26 行规则 `rabbitmq/` 覆盖整个目录
- `git check-ignore -v rabbitmq/rabbitmq.conf` 确认：文件被忽略，exit 0
- T1 计划在 `rabbitmq/` 目录下新建 `rabbitmq.conf`，但该文件无法进入版本控制

**影响链**:
```
git clone → rabbitmq.conf 不存在 → compose 挂载 ./rabbitmq/rabbitmq.conf → 
Docker 将挂载点创建为目录（而非文件）→ RabbitMQ 读取 /etc/rabbitmq/rabbitmq.conf 失败 →
容器启动失败 → G1 验收无法通过
```

**修复建议**:
```gitignore
# .gitignore 修改
rabbitmq/*
!rabbitmq/rabbitmq.conf
```
或将配置文件移至不受 `.gitignore` 覆盖的目录（如 `config/rabbitmq.conf`）。

**计划修改位置**: T1 方案步骤 2 之后，新增步骤："更新 `.gitignore`，为 `rabbitmq/rabbitmq.conf` 添加 negation 规则"。

---

### CRITICAL-2: T1 遗漏 `git rm --cached` 步骤

**严重性**: CRITICAL — 38 个运行时数据文件仍被 git 跟踪

**现象**:
- `git ls-files rabbitmq/` 显示 38 个文件被跟踪（`.erlang.cookie` + `mnesia/` 下 37 个文件）
- V2.1.0 审计报告明确将 "RabbitMQ mnesia 数据 38 文件残留" 列为延后处理项
- T1 方案仅提到"删除宿主机残留"，未明确 `git rm --cached` 步骤

**影响**:
- 执行 T1 的 `rm -rf rabbitmq/mnesia/ rabbitmq/.erlang.cookie` 后，git 将显示 38 个文件被删除
- 若直接 `git add -A && git commit`，文件从 git 历史中移除（正确行为）
- 但计划未显式说明这一步骤，执行者可能遗漏或误操作

**修复建议**: 在 T1 方案中显式添加：
```bash
git rm --cached -r rabbitmq/mnesia/ rabbitmq/.erlang.cookie
# 然后物理删除
rm -rf rabbitmq/mnesia/ rabbitmq/.erlang.cookie
```

---

### HIGH-1: G3 验收标准混淆 bind-mount 与 named volume 的 `down -v` 行为

**严重性**: HIGH — 验收标准无法证明真正的"干净状态可恢复性"

**现象**:
- T1 将 RabbitMQ 从 bind-mount 迁移到 named volume (`rabbitmq-data`)
- 其余 12 个服务仍使用 bind-mount（T2.3 明确"Phase 3-A 保持 bind-mount"）
- `docker compose down -v` **仅删除 named volume，不删除 bind-mount 数据**

**影响**:
- G3 验证 "down -v 后数据目录可删除重建" — 但 `down -v` 不会自动删除 bind-mount 目录
- MySQL (`./mysql/data`)、Redis (`./redis/data`)、ES (`./elasticsearch`) 等数据在 `down -v` 后仍然存在
- "第二次 up 行为一致" 可能因为旧数据残留而假通过 — 旧数据掩盖了首次初始化的问题

**修复建议**: 将 G3 拆分为两个标准：
```
G3a (named volume): docker compose down -v 后，named volume (rabbitmq-data) 被清除，
    重新 up 后 RabbitMQ 从零初始化并 healthy
G3b (bind-mount clean): docker compose down 后，手动 rm -rf 所有 bind-mount 数据目录，
    重新 up 后全部服务从零初始化并 healthy（验证首次 init 脚本可重复执行）
```

---

### HIGH-2: T5 新增 springdoc 依赖未计划 ADR

**严重性**: HIGH — 违反 V2.1.0 冻结基线治理规则

**现象**:
- V2.1.0 Release Note 明确规定："未经讨论，不得升级任何依赖版本。新增依赖必须在 ADR 中记录理由。"
- T5 方案在 `pom.xml` 中添加 `springdoc-openapi-starter-webmvc-ui:2.7.0`，但未提及创建 ADR

**修复建议**: T5 方案中新增前置步骤：
1. 创建 `docs/Architecture/ADR/ADR-0013-springdoc-openapi.md`
2. 记录：引入理由（替代人工维护的 API-Reference.md）、版本选择依据、与 Spring Boot 3.5 兼容性验证

---

### HIGH-3: T4 涉及 V2.1.0 冻结代码修改，未明确分支策略

**严重性**: HIGH — 存在直接污染冻结基线的风险

**现象**:
- T4 修改 `JwtUtils.java`、`AuthService.java`、`JwtAuthFilter.java`、`application*.yml` — 全部是 V2.1.0 冻结文件
- 计划未指定在哪个分支执行、如何保证 v2.1.0 tag 不被移动
- 计划未说明 Phase 3-A 完成后如何标记新基线（v2.1.1? v2.2.0?）

**修复建议**: 计划中新增"分支策略"章节：
```
1. 在 v2.1.0 tag 之上创建分支 phase-3-a
2. 所有 T1-T6 在该分支执行，禁止直接提交到 main
3. Phase 3-A 验收通过后，合并到 main 并打 tag v2.2.0
4. v2.1.0 tag 永不移动
```

---

## 三、验收标准强化建议

### 现有标准评估

| 标准 | 评估 | 问题 |
|:---:|:---:|------|
| G1 | **需调整** | 120s 超时在冷启动场景可能不足（Nacos `start_period: 120s`） |
| G2 | 通过 | 进程/端口残留检测充分 |
| G3 | **需拆分** | 混淆 bind-mount 和 named volume 行为（见 HIGH-1） |
| G4 | 通过 | git status 清洁度检测覆盖全周期 |
| G5 | 通过 | .gitignore 覆盖性检测充分 |

### 建议新增标准

| # | 标准 | 验证方式 | 理由 |
|:---:|------|---------|------|
| G6 | `down`（不带 -v）后重新 `up`，MySQL/Redis 数据持久不丢失 | `down` 前插入测试数据 → `down` → `up` → 查询数据仍存在 | 验证正常重启不丢数据（生产级可恢复性的基本要求） |
| G7 | 端到端冒烟测试通过 | `up` 后调用 `POST /api/auth/login` → 获取 token → `GET /api/devices` → 200 OK | 验证全链路可用，非仅容器 healthy |
| G8 | 干净 clone 可部署 | `git clone` → `cp .env.example .env` → `docker compose up -d` → G1 通过 | 验证从零部署能力（最终可重复部署证明） |
| G9 | ES 非正常关闭后可恢复 | `docker kill elasticsearch` → `docker compose up -d elasticsearch` → healthy | 验证 stale `node.lock` 不阻断恢复 |

### G1 超时调整建议

当前 G1: "全部 13 服务在 120s 内达到 healthy"

**问题**: Nacos healthcheck `start_period: 120s`。虽然 `start_period` 内健康检查仍会运行（通过即标记 healthy），但 Nacos 冷启动（含 Derby DB 初始化）可能需要 60-90s。若叠加首次 health check interval (10s)，实际可达 100s。120s 余量极小。

**建议**: 将 G1 超时调整为 **180s**，或在 G1 中注明"冷启动（首次 up 或 down -v 后）允许 180s，热重启 120s"。

---

## 四、任务顺序调整建议

### 当前顺序

```
Round 1: T1 + T2 并行
Round 2: T3 (依赖 T1)
Round 3: T4 (独立)
Round 4: T5 (独立)
Round 5: T6 (依赖 T3)
```

### 建议调整

```
Round 1: T1 + T2 + T4 并行
Round 2: T3 (依赖 T1)
Round 3: T5 (独立)
Round 4: T6 (依赖 T3)
```

**调整理由**:

| 变更 | 理由 |
|------|------|
| T4 提前到 Round 1 | T4 是 P0 安全项（JWT 静默降级），不应等到 Round 3。T4 与 T1-T3 无文件交叉（T4 改 Java/YAML，T1-T3 改 compose/data），无依赖冲突，可安全并行 |
| T5 维持独立但位置后移 | T5 新增 Maven 依赖，应在 T4 验证通过后执行（避免依赖冲突叠加排查难度） |

**不变项**: T3 仍依赖 T1（RabbitMQ cookie 修复后 healthcheck 才有意义），T6 仍依赖 T3（compose 稳定后才能分 profiles）。

---

## 五、遗漏的基础设施风险

### 1. MinIO `:latest` 标签未锁定 [MODERATE]

**现状**: `compose.yml` 第 117 行 `image: minio/minio:latest`

**风险**: `docker compose pull` 可能拉取不兼容版本。当前拉取到 `RELEASE.2025-09-07T16-13-09Z`，但未来版本可能引入 breaking changes（如已发生的 License 变更、API 弃用）。

**建议**: 在 T2 中新增子任务：将 `minio/minio:latest` 锁定为 `minio/minio:RELEASE.2025-09-07T16-13-09Z`（当前实际版本），与 V2.1.0 已锁定版本的其他服务保持一致。

### 2. Elasticsearch `node.lock` 循环处理缺失 [MODERATE]

**现状**: T2.1 一次性删除 `elasticsearch/node.lock`，但无持久化处理机制。

**风险**: ES 非正常关闭（`docker kill`、OOM、宿主机断电）后，`node.lock` 残留将阻止 ES 重启。每次发生都需要手动 `rm -f`。

**建议**: T2 中新增 ES entrypoint wrapper 脚本，在启动 ES 前检查并清理 stale `node.lock`。或在 G9 验收标准中验证此场景（见上文）。

### 3. RabbitMQ `nodename = rabbit@localhost` 容器环境适配 [LOW]

**现状**: T1 方案在 `rabbitmq.conf` 中设置 `nodename = rabbit@localhost`。

**风险**: Docker 容器内 `localhost` 绑定到 `127.0.0.1`，Erlang 分布式协议可能无法绑定到容器网络接口。虽然单节点模式下通常可行，但非常规配置。

**建议**: 替代方案 — 在 compose.yml 的 RabbitMQ 服务中添加 `hostname: rabbitmq`，让 RabbitMQ 使用默认节点名 `rabbit@rabbitmq`。避免创建 `rabbitmq.conf`，简化配置链。

### 4. Backend `depends_on` 仅依赖 MySQL [LOW — 信息项]

**现状**: compose.yml 中 backend `depends_on` 仅含 `mysql: condition: service_healthy`。

**说明**: V2.1.0 冻结状态下 backend 无 Redis/RabbitMQ/ES 客户端代码，此配置正确。Phase 3-B 接入中间件时需更新 `depends_on`。Phase 3-A 不需要修改，但计划应在 T6 或 Phase 3-B 规划中提及此已知限制。

### 5. MinIO healthcheck curl 依赖 — 已验证非问题 [INFO]

**现状**: 计划 §2.3 标注 "minio: curl 依赖未验证"。

**验证结果**: `minio/minio:latest` 镜像（基于 RHEL 9.6）内置 `curl 8.11.0`，位于 `/usr/bin/curl`。healthcheck `test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]` 正常工作。

**建议**: 从 T3.2 的待验证项中移除 MinIO curl 顾虑。

---

## 六、对 V2.1.0 冻结基线的影响评估

| 任务 | 修改冻结文件 | 风险评估 | 缓解措施 |
|:---:|:---:|------|------|
| T1 | compose.yml | 低 — 基础设施配置迭代，不触碰业务逻辑 | 在 phase-3-a 分支执行 |
| T2 | .gitignore (复核) | 低 — 仅确认现有规则 | 无需额外措施 |
| T3 | compose.yml | 低 — healthcheck 参数调整 | 在 phase-3-a 分支执行 |
| T4 | **JwtUtils.java, AuthService.java, JwtAuthFilter.java, application*.yml** | **中 — 安全策略变更** | 分支隔离 + 全量测试 + ADR 记录 |
| T5 | pom.xml | 低 — 新增依赖 | ADR 记录（见 HIGH-2） |
| T6 | compose.yml | 低 — profiles 标记 | 在 phase-3-a 分支执行 |

**关键判断**: T4 是唯一涉及安全策略变更的任务。当前 JWT 在 prod 缺少 `JWT_SECRET` 时静默降级为硬编码测试密钥，T4 将改为 fail-fast。这是安全增强，但也是**行为变更** — 现有依赖 fallback 的环境（如无 `.env` 的 CI）将启动失败。

**建议**: T4 执行前在 `.env.example` 中确认 `JWT_SECRET` 示例值存在且注释明确（"生产环境必须替换为强密钥"），并在 commit message 中标注 `BREAKING: JWT_SECRET is now required in production`。

---

## 七、计划文档准确性验证

| 计划描述 | 实际验证 | 一致性 |
|---------|---------|:---:|
| RabbitMQ 38 文件残留 | `git ls-files rabbitmq/` = 38 | ✅ |
| ES/MinIO/Nacos 0 文件跟踪 | `git ls-files` = 0 | ✅ |
| `.gitignore` 覆盖 `rabbitmq/` | 第 26 行确认 | ✅ |
| RabbitMQ `.erlang.cookie` 0644 | 计划描述，未验证权限（文件在 git 中，实际权限可能已变） | ⚠️ 未验证 |
| MinIO 使用 `:latest` 标签 | compose.yml 第 117 行确认 | ✅ |
| Nacos `start_period: 120s` | compose.yml 第 109 行确认 | ✅ |
| 3 个 Sentinel 无 healthcheck | compose.yml 确认 — 无 healthcheck 块 | ✅ |
| Backend `depends_on` 仅 MySQL | compose.yml 第 302-304 行确认 | ✅ |
| `application-test.yml` JWT_SECRET 为 YAML 属性 | 第 9 行 `JWT_SECRET: test-secret-...` 确认 | ✅ |
| JwtUtils 使用 `System.getenv("JWT_SECRET")` | 第 33 行确认 | ✅ |
| MinIO curl 未验证 | **已验证: curl 8.11.0 可用** | ❌ 计划过虑 |

---

## 八、总结

Phase 3-A 计划在任务分解、依赖拓扑、影响范围分析方面质量较高，核心方向（可重复部署优先）正确。但以下问题必须在执行前修正：

### 必须修正（阻断执行）

1. **CRITICAL-1**: T1 必须增加 `.gitignore` negation 规则，否则 `rabbitmq.conf` 无法版本控制
2. **CRITICAL-2**: T1 必须显式包含 `git rm --cached` 步骤
3. **HIGH-1**: G3 必须拆分为 named volume 和 bind-mount 两个验证路径
4. **HIGH-2**: T5 必须增加 ADR 创建步骤
5. **HIGH-3**: 计划必须增加分支策略章节

### 建议修正（不阻断但强烈推荐）

6. G1 超时调整为 180s（冷启动场景）
7. 新增 G6-G9 验收标准（数据持久性、端到端冒烟、干净 clone、ES 恢复）
8. T4 提前到 Round 1 与 T1+T2 并行
9. T2 中锁定 MinIO 版本标签
10. T3 中移除 MinIO curl 顾虑（已验证非问题）

---

> **审计声明**: 本审计为只读操作，未修改任何代码/配置文件，未移动 Git Tag，未调整 V2.1.0 冻结状态。  
> **审计环境**: v2.1.0 tag (`ec9a158`) + 2 个文档 commit (`02f88f6`, `e8165b2`)，工作区 clean。
