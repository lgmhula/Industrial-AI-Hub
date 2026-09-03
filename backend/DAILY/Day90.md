# Day 90 — AI 模块集成 Runbook（SETUP 级 6 步打通，Phase 4 文档日）

> **状态**：Day 90 完成（`docs/ai/phase4-integration-guide.md` 6 步 runbook 交付 + AGENTS §2/§3 同步）
> **关联**：DAILY_ROADMAP Day 90 "写 AI 模块集成文档" / Day 89 §5 候选 / AGENTS §4.3 执行后清单
> **测试**：无代码变更（纯文档日）；Day 89 后端 343/343 全绿 + 前端 build 954ms 0 errors 基线延续

***

## 1. 今日产出

### 1.1 集成 Runbook 交付（核心产物）

[docs/ai/phase4-integration-guide.md](../docs/ai/phase4-integration-guide.md)

对齐 [SETUP.md](../docs/SETUP.md) 的 step-by-step + curl + 表格风格，把 Phase 4 Day 66-89 全部 AI 能力串成一条「从默认关闭到全链路打通」的可复现 runbook。

**6 步结构**（每步含 curl 命令 + 期望响应 + 现象/原因/解法表）：

| Step | 主题 | 核心端点 | 验证目标 |
|------|------|----------|----------|
| 0 | 前置条件 | `/actuator/health` + admin 登录 | 四容器 Up + TOKEN 就绪 |
| 1 | 启用 DeepSeek + MCP | `POST /api/ai/chat` | `.env` 设 `DEEPSEEK_ENABLED=true` + Key → 重启 → 200 + answer/totalTokens |
| 2 | RAG 入库 + 问答 | `POST /api/rag/documents`（multipart）+ `POST /api/rag/ask` | chunkCount>0 + DB knowledge_chunk ≥1 行 + citations 非空 |
| 3 | 巡检 + 业务闭环 | `POST /api/ai/agents/inspection-report`（ADMIN） | detectedIssues + alarm 表新增 + 二次调幂等（autoAlarmCount 不翻倍） |
| 4 | SSE 推送 | `GET /api/push/inspection?token=`（VIEWER+） | 200 + text/event-stream + `event: inspection-report` + 30min timeout + nginx 缓冲关闭 |
| 5 | 限流验证 | 连续 3 次 `/api/ai/chat`（VIEWER）+ 6 次（ADMIN） | VIEWER 200/200/429；ADMIN 6 个 200 |
| 6 | 故障速查 + 回滚 | 10 故障表 + 5 回滚策略 + Day 89 兜底工具速查 | 事故时安全网一目了然 |

**附验收矩阵（§7）**：11 项 checklist（端点/角色/期望/☐），Phase 4 AI 模块一键验收。

### 1.2 端点纠正（Day 89 §5 笔误）

Day89.md §5 明日计划里写的 `POST /api/rag/ingest/upload` 是笔误。实际端点是 [POST /api/rag/documents](../backend/src/main/java/dev/reboot/controller/RagController.java#L45)（`consumes=multipart/form-data`，ADMIN 权限）。Runbook §2.1 已显式标注纠正，避免读者照 curl 拿 404。

### 1.3 完整 AI 端点清单（Runbook §0/§1-§5 汇总）

| 端点 | 方法 | 权限 | 用途 |
|------|------|------|------|
| `/api/ai/chat` | POST | VIEWER+ | DeepSeek 文本补全 |
| `/api/ai/alarms/{id}/summary` | POST | VIEWER+ | 告警 AI 摘要 |
| `/api/ai/devices/{id}/diagnose` | POST | VIEWER+ | 设备健康诊断 |
| `/api/ai/agents/device-status` | POST | VIEWER+ | Function Calling 单轮 Agent |
| `/api/ai/agents/device-analysis` | POST | VIEWER+ | 多步推理 Agent |
| `/api/ai/agents/inspection-report` | POST | **ADMIN** | 巡检日报（触发 MQ + SSE + autoAlarm） |
| `/api/rag/documents` | POST | **ADMIN** | PDF 上传入库（multipart） |
| `/api/rag/ask` | POST | VIEWER+ | RAG 问答（带 citations） |
| `/api/push/inspection` | GET | VIEWER+ | SSE 订阅（30min timeout） |
| `/api/mcp/smoke` | POST | **ADMIN** | MCP 客户端冒烟 |

### 1.4 关键安全/幂等约束文档化（Runbook §3.3 / §4 / §6.3）

把散落在 ADR 0026-0031 + Day 86-89 代码 Javadoc 里的约束提炼成 runbook 速查：

| 约束 | 值 | 出处 |
|------|-----|------|
| 巡检 Agent 硬限 | 6 轮（超限 forceFinalize 截断） | ADR 0026 |
| 自动报警幂等键 | `ai-alarm:{deviceId}:{alarmType}:{yyyy-MM-dd}` TTL 24h SETNX | AiAlarmAutoCreator §65-67 |
| Consumer 幂等键 | `inspection:{reportDate}:{siteId}/all` TTL 24h | ADR 0031 §5.1 |
| SSE 端点 token query fallback | 仅 `/api/push/` 前缀；REST 端点不读 query 防 token 泄漏 | JwtAuthFilter §28/§44/§136 |
| AI JSON 硬上限 | 2MB（防 AI 超长 payload OOM） | AiJsonFallbackUtil §42 |
| AI 限流速率 | VIEWER 2/s · ADMIN 5/s · 匿名 IP 2/s | AiRateLimitInterceptor §68-73 |
| 限流拦截顺序 | order -1 AI → order 0 通用 → order 1 Auth | WebMvcConfig §64 |
| 非 ADMIN 无站点 SSE | 403 拒绝（防跨站点泄漏 P0） | InspectionPushController §115 |

***

## 2. 设计决策（Day 90 文档特有）

### 2.1 Runbook 风格对齐 SETUP.md 而非新造章节结构

Day 89 §5 候选里列了 6 步骨架，Day 90 落地时严格对齐 [SETUP.md](../docs/SETUP.md) 的「§0 全景 → §1 前置 → §N 启动+验证 curl → §N+1 故障速查表」风格，而不是新造一套。理由：① SETUP.md 是已验证的「clone 后无需猜」基线，读者已熟悉这个节奏；② 工程文档一致性 > 创新性；③ AGENTS §4.2「遵循分层架构」的文档版延伸。

### 2.2 故障速查表 10 条覆盖 Day 66-89 全部高频坑位

Runbook §6.1 的 10 条故障不是凭空列举，是从 Day 66-89 的 DAILY 日志 + ADR 0021-0031 + AGENTS §8.4 排查速查里提炼的真实踩过的坑：
- 故障 1/2（DeepSeek 503）= Day 66 + AGENTS §8.4
- 故障 3（Redis SETNX 降级）= Day 86 AiAlarmAutoCreator 6 级降级矩阵
- 故障 4（非 ADMIN 无站点 403）= Day 85 Phase 6 P0 安全防护
- 故障 6（nginx 缓冲 SSE 卡死）= Day 85 Phase 7 nginx.conf
- 故障 7（MCP 握手超时）= Day 82 McpInspectionSession AutoCloseable
- 故障 8（JSON fence 前端解析失败）= Day 89 AiJsonFallbackUtil + escapeHtml.js 双兜底

### 2.3 回滚策略表 5 条 = Phase 4 模块化降级路径

Runbook §6.2 的 5 条回滚不是「全关 AI」一刀切，而是分层：
1. DeepSeek 整体不可用 → `.env` 改 false（AI 全 503，业务 CRUD 不受影响）
2. 单接口降级（如巡检卡）→ nginx 网关 return 423（其他 AI 接口正常）
3. SSE Consumer 死锁 → restart backend（RabbitMQ 不重启，消息在队列等）
4. 演示数据误灌生产 → ADR 0019 §5 隔离机制
5. AI 限流误伤 ADMIN → yml 改 adminPermits 重启

每条都标注了**影响范围**，运维不用读代码就能判断「这个回滚动不动业务」。

### 2.4 没有写「第 7 步：Day 91 复盘」

Day 89 §5 候选只到 Step 6。Day 90 不超前实现 Day 91（Week14 + tag v2.0-ai），严格遵循 AGENTS §4.2「不做未要求的事」。

***

## 3. 文档同步

- [AGENTS.md §2](../AGENTS.md)：文档索引新增 ★★☆ 行「AI 模块集成 Runbook」紧邻 SETUP.md
- [AGENTS.md §3](../AGENTS.md)：当前状态补 Day 90；下一步更新为「Day 91 Phase 4 复盘 + Git tag v2.0-ai」
- 本文件（Day90.md）：完整交付细节 + 端点清单 + 8 条约束文档化 + 4 条设计决策
- [phase4-integration-guide.md](../docs/ai/phase4-integration-guide.md)：核心产物，6 步 + 验收矩阵 + 10 故障 + 5 回滚

***

## 4. 遗留 & 后续

1. **rate.limit.ai.* 未写入 application.yml**（Day 89 §4.5 遗留延续）——Runbook §5.1 已显式标注「靠代码默认值，如需覆盖在 application-dev.yml 加」，但 `application.yml` 本身仍无注释化默认值。Day 91 复盘时决定是否补 yml 注释（不阻塞 v2.0-ai tag）；
2. **Runbook 未覆盖 ELK 日志查询路径**——Day 101（可选）才做 ELK，当前故障定位靠 `docker logs backend` + grep。Phase 5 起接入 ELK 后回补 Runbook §6 故障定位段；
3. **AlarmList.vue copySummary 手动 forEach 未用 safeJoin**（Day 89 §4.1 遗留延续）——Runbook §6.3 故障 8 提到 escapeHtml.js 但未改 copySummary 调用点，留给 Day 91 或 Phase 5 空闲日；
4. **SSE 端到端联调未实跑**（Day 88 §五遗留 4 延续）——Runbook §4 给了 curl + 浏览器两条验证路径，但当前会话是纯文档日，没有起容器实跑。Day 91 tag v2.0-ai 前建议手动过一遍 §7 验收矩阵 11 项。

***

## 5. 明日计划（Day 91，严格对齐 DAILY_ROADMAP L555）

DAILY_ROADMAP Day 91 = **第四阶段复盘 + Git tag: v2.0-ai**。

1. **Week14.md 周复盘**（对齐 Week13.md 6 大章节结构）：
   - §一 本周目标 vs 实际（Day 88 文档 + Day 89 重构 + Day 90 集成文档）
   - §二 关键收获（Phase 4 28 天 Day 66-91 全链路闭环 + AI 不再是 demo）
   - §三 演进全景（DeepSeek→Spring AI→Function Calling→RAG→Agent→MCP→MQ→SSE→autoAlarm→限流→集成文档）
   - §四 关键指标（后端 343 tests / 前端 build 954ms / Flyway V15 / ADR 0031 / 9 ADR 0021-0031）
   - §五 遗留 & 风险（Day 90 §4 三条 + Phase 5 前瞻）
   - §六 Phase 5 计划（PLC + MQTT + 完整系统上线）
2. **Git tag v2.0-ai**（Phase 4 收官检查点）：
   - 打 annotated tag `v2.0-ai`（描述：Phase 4 AI 集成收官，Day 66-91）
   - 归档 release note（`docs/reports/v2.0-ai-release-note.md`）
   - AGENTS §3 基线 bump 到 v2.0-ai
3. **手动过 Runbook §7 验收矩阵**（如果时间允许，给 v2.0-ai tag 加实测背书）

***

> 完成时间：2026-09-03（Asia/Shanghai）
> 维护者：AI 助手 + hula0710
