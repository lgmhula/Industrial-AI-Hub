# Phase 3 收官漂移审计报告

> **日期**：2026-08-16（Day 63 完成，Phase 3 收官）
> **范围**：文档 ↔ 代码 ↔ 路线图 三向一致性扫描，6 个维度
> **目的**：进入 Phase 4（AI 集成，Day 64）前识别并阻断漂移
> **状态**：扫描完成，待修复项见 §4
> **勘误（2026-08-16 复核）**：§2.1/§2.3/§4 中「端点 25→31」为误计，已更正——实际 25 个方法级映射准确（"31" 是把 6 个类级 `@RequestMapping` 误算为端点）。

---

## 1. 结论速览

**代码层面健康**（无依赖漂移、无 AI 渗透、Git 干净、Phase 3 全交付）；
**文档层面落后 1~3 个阶段**，是本次漂移的主要来源；
**Phase 4 漂移门尚未搭建**，若明天直接进 Day 64，将重蹈 Phase 3 的文档漂移覆辙。

| 维度 | 结论 | 严重度 |
|------|------|:--:|
| 状态文档同步 | AGENTS.md §3 ✅ / README ❌ 滞后 ~3 周 | 中 |
| Phase 3 交付完整性 | ✅ 全部交付（日志+提交+检查点） | — |
| 依赖漂移 | ✅ 无冲突、无 AI 渗透 / ⚠️ 锁定表不全 | 低 |
| 文档-代码一致性 | ❌ 架构文档冻结在 Day 42，演进路线错位 | **高** |
| Phase 4 就绪度 | ❌ 漂移门未搭建（锁定表/ADR/基线/.env） | **高** |
| Git 卫生 | ✅ 干净、无密钥、无大文件 | — |

---

## 2. 详细发现

### 2.1 P0 — 架构文档严重错位（最高优先级）

**`docs/Architecture/Application-Architecture.md` 冻结在 Day 42 / Baseline V2.1（2026-08-03）**，与现行 `DAILY_ROADMAP.md` 冲突：

- **§6 演进路线错位**：写的是
  - `Phase 4 (Day 57-70): 微服务 Spring Cloud` ← **幻影阶段，现行路线图已不存在**
  - `Phase 5 (Day 71-84): AI 集成` ← 错位
  - 实际：`Phase 4 = AI 集成（Day 64-91）`、`Phase 5 = PLC + 完整系统（Day 92-112）`
  - → 这份"governs 所有应用层决策"的文档在**错误地指引**接下来的方向。
- **§3 模块清单标注 "(Day 42)"**：未包含 Phase 3 新增的 `mq/`（RabbitMQ 生产者/消费者）、`rule/`（报警规则）、Redis 缓存整合。
- **§4 端点 "25 个"**：复核确认准确——6 个 controller 共 25 个方法级映射（此前「31 处」为误计，把 6 个类级 `@RequestMapping` 也算成端点）。

> 影响：进入 AI 阶段时若按此文档走，会把方向带偏到已被废弃的"微服务 Spring Cloud"。**必须在 Day 64 前修正。**

### 2.2 P0 — Phase 4 漂移门未搭建

对照 Phase 4 即将引入的全新依赖类别（Spring AI / 向量库 / LLM / MCP），当前四项防护全部缺失：

| 防护项 | 现状 | 要求 |
|--------|------|------|
| AGENTS.md §5 技术栈锁定表 | 只覆盖到中间件，**无 AI 段** | 补 Spring AI 版本 / 向量库 / embedding 模型 / LLM provider+base-url / MCP SDK |
| AI 决策 ADR | **未写**；编号已占用到 0013（knife4j） | 从 **0014** 起：`0014-ai-stack`、`0015-llm-provider`、`0016-vector-db` |
| `.env.example` AI 变量 | 仅基础设施 key，**无 LLM_*/VECTOR_*** | 补 `LLM_API_KEY` / `LLM_BASE_URL` / `LLM_MODEL` / `VECTOR_DB_URL` |
| AI 改造前基线冻结 | 无（tag 只有 v1.0-alpha / v2.1-baseline / v2.1.0） | 切 `v3.0-ai-prep` 作为回滚点 |

> 注意：ADR 下一个可用编号是 **0014**（0013 已被 `0013-api-docs-knife4j.md` 占用）。

### 2.3 P1 — 状态文档滞后

- **README.md**：仍写 `当前进度：Day 42 完成，Baseline V2.1 Hotfix 中`，badge `v1.0-alpha`，`最后更新 2026-08-01`。实际 Day 63 完成、v2.1.0 已冻结、Phase 3 收官。**滞后约 3 周**（此前已标记为 P2 技术债，至今未修，且越来越严重）。
- **AGENTS.md §2**：ADR 范围写 `0001~0012`，实际已有 `0013`。
- **AGENTS.md §3 待实现**：仍含 `Phase 3-B Redis 缓存`（应为 Phase 4 AI 相关）。

### 2.4 P1 — Phase 3 设计决策未沉淀 ADR

Phase 3 产生了多项关键设计决策，但决策日志（0001~0013）里**没有对应 ADR**，存在"为什么这么设计"的知识断层：

- RabbitMQ 消息拓扑：工作队列 / 发布订阅 / DLQ+手动 ACK / 延迟队列（报警 30s 升级），三条 MQ 管线
- Docker 多阶段镜像 + backend 容器化 + compose 编排
- Nginx 反向代理配置
- （Redis 已有 0002 / 0006 覆盖 ✅）

> 建议补：`0014-rabbitmq-topology`（或在 AI ADR 前先补 Phase 3 的，按时间顺序编号）。

### 2.5 P1 — 锁定表不完整

`pom.xml` 中这些**显式版本**依赖未纳入 §5 锁定表，也无 ADR（防漂移规则覆盖不到它们）：

| 依赖 | 版本 | 是否有 ADR |
|------|------|:--:|
| jedis | 5.2.0 | ❌ |
| redisson-spring-boot-starter | 3.39.0 | ❌ |
| guava | 33.4.0-jre | ❌ |
| pagehelper-spring-boot-starter | 2.1.0 | ❌ |
| knife4j-openapi3 | 4.5.0 | ✅ (0013) |

### 2.6 P2 — 文档整洁度

- `backend/DAILY/Day007.md` **缺失**（Day006 → Day008 跳号）。
- 日志命名不统一：`Day001`（3 位，Day1~21）vs `Day22`（2 位，Day22+）。
- `backend/REVIEW/` 缺 `Week03.md`、`Week06.md`（Phase2-Summary.md 部分覆盖）。

---

## 3. 健康项（无需处理）

- ✅ **无 AI 依赖/代码提前渗透**：`backend/src` 中 `spring.ai/openai/ChatClient/embedding` 匹配为 0。
- ✅ **依赖版本无冲突**：Spring Boot 3.5.0 / MyBatis 3.0.5 / MySQL Connector 9.2.0 与锁定表一致。
- ✅ **Git 干净**：工作区无未跟踪/未提交文件，无密钥入库，无大文件（最大 76K package-lock）。
- ✅ **Phase 3 全交付**：Day 43~63 每日均有提交+日志，第三阶段检查点达成（Redis/RabbitMQ/Docker/Linux，89/89 测试）。
- ✅ **代码结构规范**：分层清晰（controller/service/mapper/dto/config/security/aop/mq/rule），6 controller 与文档一致。

---

## 4. 修复建议（按优先级，Day 64 前完成 P0）

### P0 — 进 Day 64 前必做
1. **修正 `Application-Architecture.md §6 演进路线`**：删除"微服务 Spring Cloud"幻影阶段，对齐 `DAILY_ROADMAP.md`（Phase 4 = AI，Phase 5 = PLC），并补 Phase 3 模块（mq/、rule/、Redis 缓存）。
2. **扩充 AGENTS.md §5 锁定表**，新增 AI 段（Spring AI / 向量库 / embedding / LLM provider+base-url / MCP SDK），明确"未经讨论不得升级 + 必须 ADR"覆盖 AI 依赖。
3. **切 `v3.0-ai-prep` 基线冻结 tag**，作为 AI 改造前回滚点。
4. **`.env.example` 补 AI 变量**（`LLM_API_KEY`/`LLM_BASE_URL`/`LLM_MODEL`/`VECTOR_DB_URL`）。

### P1 — 本周内
5. **同步 README**：进度改 Day 63 / v2.1.0，去 v1.0-alpha badge，更新最后日期（端点数 25 经复核无误，无需改）。
6. **补 Phase 3 ADR**：`0014-rabbitmq-topology`（+ Docker/Nginx 决策），AI ADR 顺移为 `0015-ai-stack`/`0016-llm-provider`/`0017-vector-db`。
7. **锁定表补全**：jedis/redisson/guava/pagehelper 版本入表。
8. **AGENTS.md §2/§3 微调**：ADR 范围改 `0001~0013+`，"待实现"改为 Phase 4 AI。

### P2 — 顺手
9. 补 `Day007.md` 占位或在 Week 复盘中说明；统一日志命名。
10. 补 `Week03.md`/`Week06.md` 复盘（或注明由 Phase2-Summary 覆盖）。

---

## 5. 漂移风险总评

| 风险 | 等级 | 说明 |
|------|:--:|------|
| 明天直接进 Day 64（不设漂移门） | 🔴 高 | AI 全新依赖类别无锁定/无 ADR/无基线，必然重蹈 Phase 3 文档漂移 |
| 架构文档 §6 错误指引 | 🔴 高 | 会把方向带向废弃的"微服务" |
| README/AGENTS.md 滞后 | 🟡 中 | 影响团队协作与自查，不阻断开发 |
| Phase 3 决策无 ADR | 🟡 中 | 知识断层，未来重构缺依据 |
| 依赖/代码/Git | 🟢 低 | 已确认健康 |

> **一句话**：代码没漂移，**文档漂移了 1~3 个阶段**；把 P0 四项做完，Phase 4 就能干净起步。
