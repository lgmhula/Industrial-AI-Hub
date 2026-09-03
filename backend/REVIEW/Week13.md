# Week 13 复盘 — 推送链路收官 + AI→业务闭环 + 前端 AI 工业化（Phase 4 第 13 周）

> 日期：2026-09-03 | 覆盖：Day 85 \~ Day 87
> 基线演进：316 → 330 tests（+14，Day 85 25 新增已计入 308 → 311 → 316；Day 86 AiAlarmAutoCreator 10 + Agent 扩 3 + FlywayV15 1 = +14 → 330）；Flyway V14 / V15 新增；ADR 0031（架构边界冻结）
> 前端：Day 85 基线 744ms → Day 87 698ms，4 AI 页 15+ 缺口工业化打磨

***

## 一、本周目标 vs 实际

| 目标                                                         | 实际                                                                                                                                                                                |  状态 |
| ---------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :-: |
| Day 85: AI 生成的日报自动通过 RabbitMQ 推送到前端（Phase 1-7）         | 7 阶段全链路收官：投递侧 DTO/Producer → Agent 接入 → Consumer 幂等(+Redis SETNX DLQ) → PushGateway(Site路由+泄漏防护) → PushController(JWT+SSE+@Profile) → 前端 EventSource订阅+nginx反代；ADR 0031 边界冻结；单测 4/4 + 3/3 + 6/6 + 25 + 3/3 + 5 JWT，316/316 全绿 |  ✅  |
| Day 86: AI 巡检异常自动生成报警——AI 与业务闭环                    | AiInspectionDetectedIssue 结构化 + AiAlarmAutoCreator(Redis幂等 ai-alarm:{d}:{t}:{date} 24h + 6级降级 + @OperationLog(AUTO_ALARM/ALARM)) + Agent接入(toResult→autoCreate→dispatch 顺序) + FlywayV15 + 13 单测，330/330 全绿                       |  ✅  |
| Day 87: 前端 AI 功能完善：日报展示、巡检结果、知识库搜索              | 4 页面 15+ 缺口全量修复：InspectionReport(折叠卡+徽章+字段+XSS) / RagAssistant(时间戳+retry+引文结构化+快捷问+转义) / DeviceDetail(双retry+ts+severity图标+L2徽章+复制+转义+空态) / AlarmList Dialog(范围切换+shimmer骨架+retry+copy+双空态+priority图标)；build 698ms 0 errors |  ✅  |
| Day 88: 全链路联调 / Week13复盘 / 推送链路架构图                        | 本复盘 + Application-Architecture.md 推送链路完整图（见 §四 & Day88.md）                                                                                                                                              |  🔨  |

***

## 二、关键收获

### 2.1 ADR 0031 的价值：架构边界冻结 = 责任线画清楚

Day 85 启动前是"Agent 跑完 → 日报结果"孤岛：要让前端看到巡检结果，所有人都会自然地"把日报直接从 Agent Controller 返回前端做轮询"——那就失去了 RabbitMQ 基础设施投入的意义。ADR 0031 没有做技术决策，而是做了**责任拆分决策**：

| 层 | 责任 | 不做 |
|----|------|------|
| Agent (McpInspectionAgentService) | 生成日报 + **可选**投 MQ（@Nullable Producer） | 感知不到 PushGateway / SSE / JWT |
| MQ (inspection.exchange/queue) | 解耦投递与消费：投递失败不阻塞 Agent，消费失败入 DLQ | 不接触站点权限、不推送浏览器 |
| Consumer | 幂等（Redis SETNX，跨实例 24h）+ 调 PushGateway | 不构造 SseEmitter、不读 JWT |
| PushGateway | siteIds 路由 + sendSafely 单点失败不阻塞 + 移除失效会话 | 不关心消息从哪来、不解析 JWT/站点权限 |
| PushController | JWT 解析 userId → accessibleSiteIds → 构造 SseEmitter → 注册 | 不消费 MQ、不做业务逻辑 |
| Nginx /api/push/ | 反代 SSE（proxy_buffering off + read_timeout 3600s）+ 关日志防 token 泄漏 | 不解析消息体 |
| 浏览器 EventSource | `?token=` query fallback → addEventListener('inspection-report') → reportDate 去重 → readyState=2 手动重试 | 不保存 token 到 localStorage（SSE 只发 query 一次） |

没有这张边界表，Day 85 7 个子任务的 4+3+6+25+3+5 个单测会在同一个地方反复"抄实现"——Consumer 读 SSE、Controller 调 Redis，造成不可维护的耦合。ADR 0031 让每个组件只做一件事，测试面聚焦，失败语义清晰。

### 2.2 幂等三键合流：Consumer 日报幂等 vs AutoCreator 报警幂等

**两个独立的跨实例幂等键，但语义完全对齐（同一天同维度不重复）：**

```
日报 Consumer:  inspection:{reportDate}:{siteId}/all          TTL 24h   → 防止同站点同天两次巡检生成两次日报卡片（SSE 去重第二道防线是前端 reportDate Set）
报警 AutoCreator: ai-alarm:{deviceId}:{alarmType}:{yyyy-MM-dd}  TTL 24h   → 同设备同类型异常当天只落一条 alarm（即使 Agent 连续巡检 10 次）
```

设计要点：
- 两个键都把"hash/description"去掉，跟"内容"无关（见 Day86 §2.2）；
- 两个键**Redis 失败时都做"宁愿重复不丢"降级**（ADR 0031 §6 降级语义），而不是 fail-closed；
- 前端 reportDate Set 是第三道防线（后端幂等偶发重复时前端自动去重，最多 50 条限制内存）。

三重幂等让"每天自动巡检"可以用 cron 随便撞，不需要"只有一个节点能跑 cron"的分布式锁调度器。

### 2.3 AI→业务闭环的接入点选 Agent.generate()，而不是 Consumer

Day86 §1.4/§2.3 详细比较过：
1. **时效性**：毫秒级落 alarm vs MQ→Consumer 秒级；
2. **幂等对齐**：Consumer 自身 `inspection:{date}:siteId` 幂等会 skip 日报 → alarm 不落；
3. **失败隔离**：三通道（Alarm/Redis/MQ）任意一环故障互不影响——Day 86 10 个降级单测已经证明；
4. **test profile 兼容**：@Nullable 注入让 test profile 不装配 Consumer 时 AiAlarmAutoCreator 仍可被 AgentTest 直接使用。

更深层的一点：**Alarm 是"状态"，SSE 日报是"通知"——状态生成和通知投递应该解耦，但状态必须和状态生成者（Agent）同一个事务上下文，不应该挂在通知链路上。**

### 2.4 前端 AI 页面不是"搭 demo"，是有安全/可观测性等级的产品界面

Day 87 的 15+ 缺口里，占比最大的三类是：
- **XSS 安全**：6 处（report/description/summary/issues/answer/sources.content）——AI 自由文本不受信任，和用户输入同等级别处理；
- **失败可恢复**：4 页都有 retry 按钮 + Loading 状态/骨架屏（消除"卡了"误判）；
- **信息可读性**：徽章/图标/时间戳/引文结构化——工业操作员不是 LLM 评审，需要 0.5s 内看懂 severity/source/scope。

Day 69 DESIGN.md 定位是"不是后台管理系统，是工业 AI 控制中心"，Day 87 是把这句话从"色板 CSS 层面"落实到"每个 AI 交互细节"——告警摘要 shimmer 骨架屏对齐 Bloomberg，不是 Element Plus 的 v-loading 遮罩。

***

## 三、Week 13 演进全景（Phase 1-7 + 闭环 + 前端工业化）

```text
Day 85   Phase 1-7 推送链路收官
  P1 投递侧      InspectionReportMessage/DTO + inspection.exchange|queue|DLX|DLQ|binding + Producer (4/4)
  P2 Agent 接入   McpInspectionAgentService @Nullable Producer + 末尾投递 + AmqpException 降级 (3/3)
  P3 Consumer     @RabbitListener ack=MANUAL + Redis SETNX inspection:{date}:{siteId}/all + DLQ + 幂等 (6/6)
  P4 PushGW       SseEmitterSession + SseEmitterRegistry (30min + 泄漏防护) + InspectionPushGateway siteIds 路由 (25)
  P6 Controller   GET /api/push/inspection SSE + @RequireRole + JWT + 403 无站点拒绝 + FlywayV14 PUSH/SSE (3/3)
  P7 前端+nginx   JwtAuthFilter query token /api/push/ fallback + EventSource + 去重 + retry + nginx 反代 (5 JWT)
                 -> 316/316 全绿，前端 744ms 0 errors
Day 86   AI → ALARM 业务闭环
  结构化 DTO      AiInspectionDetectedIssue(6字段@校验) + AiInspectionReportResult(detectedIssues/autoAlarmCount)
  AiAlarmAutoCreator  Redis SETNX ai-alarm:{d}:{t}:{date} 24h + 6级降级 (10 tests)
  Agent 接入       toResult → autoCreateAlarms → dispatchReport (InOrder 验证, 3 tests)
  Flyway V15       chk_operation_type 扩 AUTO_ALARM -> 330/330 全绿
Day 87   前端 AI 4 页工业化 (15+ 缺口)
  InspectionReport  异常折叠卡 / severity 徽章 / 字段映射 / XSS 双转义
  RagAssistant      时间戳 / retry / 引文 [序号]badge+相似度+片段号 / 快捷提问 / 安全转义
  DeviceDetail      诊断&QA 双 retry / 时间戳 / health 图标徽章 / L2 issue 徽章 / copy / 空态统一
  AlarmList Dialog  page/selected/single 范围切换 / shimmer 骨架屏 / retry / copy全文 / 双空态 / priority图标
                 -> build 698ms 0 errors
```

***

## 四、关键指标

### 4.1 测试矩阵（Phase 4 累计）

| 周 | 范围 | 单测总数 | 失败 | 跳过 |
|----|------|---------|:--:|:--:|
| Week 10 (Day66-70) | DeepSeek / Spring AI / FC / 前端视觉 | ~210 | 0 | 3 |
| Week 11 (Day71-77) | RAG 入库/检索/导入/前端 | ~220 | 0 | 3 |
| Week 12 (Day78-84) | Agent / MCP Server / Client / 联调 | 269 | 0 | 3 |
| **Week 13 (Day85-87)** | 推送链路 7 phase + AI→ALARM + 15前端缺口 | **330** | 0 | 3 |

### 4.2 Flyway 迁移链（Phase 4 新增 V9 → V15）

| 版本 | 内容 | Day |
|------|------|-----|
| V9  | AI 操作日志类型（CHAT/SUMMARY/DIAGNOSE） | Day 67 |
| V10 | FUNCTION_CALL 操作日志类型 | Day 68 |
| V11 | INGEST/KNOWLEDGE 操作日志类型 + RAG 知识块表 | Day 74 |
| V12 | MCP_SMOKE/MCP 操作日志类型 | Day 83 |
| V13 | INSPECTION 操作日志类型 | Day 83 |
| V14 | PUSH/SSE 操作日志类型（chk_operation_type 扩 PUSH） | Day 85 |
| V15 | AUTO_ALARM 操作日志类型 | Day 86 |

### 4.3 前端页面包大小（Day 87 build）

| 页面 | 大小（gzip） | 说明 |
|------|-------------|------|
| RagAssistant | 5.25 kB（2.62 kB） | Day 76 基础 + Day 87 时间戳/retry/引文 (+1.5 kB) |
| InspectionReport | 6.20 kB（2.60 kB） | Day 85 基础 + Day 87 折叠卡/徽章 (+2.49 kB) |
| AlarmList | 13.36 kB（4.84 kB） | 基础列表 + 批量操作 + AI Dialog 骨架屏 |
| DeviceDetail | 83.01 kB（30.82 kB） | ECharts line chart + 诊断卡 + QA 折叠面板 |

***

## 五、遗留 & 风险

### 遗留（进入 Day 88+ 候选）

1. **AlarmList 批量摘要仍复用单条接口**：占位入口已给，后端需要 `POST /api/ai/alarms/summary-batch`（body={alarmIds: number[]}）；
2. **设备诊断 issues 无真实 severity 字段**：`aiDiagnosis.issues[]` 当前是 string[]，前端统一 L2；后端扩 AiDiagnosisIssue DTO（issue + severity + deviceCode）可着色；
3. **巡检 Agent prompt 未约束 detectedIssues 结构化输出**（Day86 §4 风险 1）：AI 把异常写在日报自由文本里但漏写 detectedIssues 列表会导致"用户看到异常但不落 alarm"；
4. **真实 SSE 端到端联调未执行**：compose 启 MySQL/Redis/RabbitMQ + 登录 admin → curl `/api/ai/agents/inspection-report` → 浏览器 InspectionReport 验证 SSE 具名事件 + alarm 表新增 + operation_log 三张审计（INSPECTION / AUTO_ALARM / PUSH）；
5. **Week 13 `v2.4-ai-phase4-final` tag 未打**：Week 13 收尾 + Day 90 集成文档写完后是 Phase 4 最佳打 tag 节点（对齐 DAILY_ROADMAP Day 91：Git tag v2.0-ai）。

### 风险

1. **SSE 长连接在 Spring Boot 8080 + nginx 反代的生产环境连接保活未验证**：当前配置 `proxy_read_timeout 3600s` 理论可扛 1h，但大规模（>100 用户）场景需配合 heartbeat 事件 / Redis pubsub 广播扩展；
2. **AiAlarmAutoCreator severity clamp 未知输入降级为 1（一般）**：AI 报紧急但系统当一般；更保守策略 clamp≥3 一律当紧急；
3. **前端所有 `escapeText` 与 `{{ }}` 双层转义**：如果后续支持 markdown，不能直接放开 v-html，必须用 DOMPurify 白名单过滤；
4. **Phase 4 第 13 周（Week 13）是 AI 模块最后一周，Day 89 重构 + Day 90 文档 + Day 91 第四阶段复盘/tag 需要连续 3 天治理类产出，不能混实现任务**。

***

## 六、下周计划（Week 14 = Day 88-91，Phase 4 收官）

| Day | 任务 | 交付物 |
|-----|------|--------|
| Day 88 | 文档 + 联调 | Week13 复盘（本文件）+ Application-Architecture 推送链路完整图 + 端到端联调记录 |
| Day 89 | 重构 AI 模块 | 抽取公共组件（escapeText 公共 util / ToolCallingAgent 错误语义统一 / AiAlarmAutoCreator 与 AlarmService 边界重构）+ 异常处理 + 限流 |
| Day 90 | 集成文档 | Phase 4 AI 模块集成文档：启用 DeepSeek → 导入 PDF → 触发巡检 → 配置 nginx SSE → 浏览器验证完整走通 |
| Day 91 | 第四阶段复盘 | Week14.md（实际只有 4 天）+ Git tag v2.0-ai + 审计报告 |

> 第四阶段检查点（DAILY_ROADMAP.md）：AI 不再是 demo，而是真正为项目创造业务价值的功能模块。Week 13 收官 = 检查点达成 95%（只差联调实证与治理类产出）。

***

> 维护者：AI 助手 + hula0710
