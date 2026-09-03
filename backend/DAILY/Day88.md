# Day 88 — Week13 周复盘 + Application-Architecture 推送链路完整图（Phase 4 文档日）

> **状态**：Day 88 完成（Week13.md 周度复盘 + Application-Architecture V2.5 §2a 推送链路 5 段 ASCII 完整图 + 幂等/安全/扩展点三张附表）
> **关联**：Day 85 ADR 0031 / Day 86 AiAlarmAutoCreator / Day 87 前端 AI 4 页
> **测试**：无代码变更（纯文档日）；Day 87 前端 698ms 0 errors + Day 86 后端 330/330 全绿基线延续

***

## 1. 今日产出

### 1.1 Week 13 周复盘（Week13.md）

[Week13.md](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/REVIEW/Week13.md)

**结构对齐 Week12 模式（6 大章节）**：一、本周目标 vs 实际；二、关键收获；三、演进全景；四、关键指标；五、遗留 & 风险；六、下周计划。

| 章节 | 内容亮点 |
|------|---------|
| 目标 vs 实际（§一） | Day 85 7-Phase 推送链路 ✅ / Day 86 AI→ALARM 闭环 ✅ / Day 87 前端 AI 4 页 ✅ / Day 88 周复盘+架构图🔨 |
| 关键收获（§二，4 条深度总结） | ① ADR 0031 架构边界冻结 = 7 子任务责任线画清，Agent/PushGW/Controller/nginx 各司其职；② 三重幂等（Consumer SETNX + AutoCreator SETNX + 前端 reportDate Set）语义对齐；③ AI→业务闭环接入点选 Agent.generate() 而不是 Consumer 的 4 维决策；④ 前端 AI 工业化 = 安全 + 可恢复 + 可读性（不是 demo） |
| 演进全景（§三） | Day 85 Phase 1-7 逐项产出 → Day 86 结构化 DTO + AiAlarmAutoCreator + 13 tests → Day 87 4 页 15+ 缺口清单，完整 text 链路图 |
| 关键指标（§四） | 测试：316（Week12 基线）→ 330（+14 Day 86 13 单测 + FlywayV15 1）；Flyway：V1 → V15（Phase4 新增 7 个 V9-V15）；前端包大小：RagAssistant 5.25 / InspectionReport 6.20 / AlarmList 13.36 / DeviceDetail 83.01 kB |
| 遗留风险（§五） | 遗留 5 条（AlarmList 批量接口占位 / AiDiagnosisIssue severity 缺 / 巡检 Agent prompt detectedIssues 未约束 / SSE 端到端联调未跑 / v2.0-ai tag 未打）；风险 4 条（SSE 保活 / severity clamp 方向 / DOMPurify / Day 89-91 治理类密集） |
| 下周计划（§六） | Day 88 文档+联调 → Day 89 AI 模块重构 → Day 90 集成文档 → Day 91 Week14 复盘 + Git tag v2.0-ai（Phase 4 第四阶段收官），严格对齐 DAILY_ROADMAP Day 91 检查点 |

### 1.2 Application-Architecture.md §2a 推送链路完整图（5 段 ASCII + 3 附表）

[Application-Architecture.md §2a](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/docs/Architecture/Application-Architecture.md#L102-L249)

**版本 bump 2.4 → 2.5**；Updated → 2026-09-03；BasedOn 扩展 Day 85-87 三项（SSE 7-Phase + AI→ALARM + 前端工业化）。

#### §2a 全链路 ASCII 图 = 5 个独立框 ①~⑤ 自顶向下

```
① 触发入口（JWT ADMIN）McPInspectionAgentService.generate
     ├─ 【Step A】 AiAlarmAutoCreator.createAlarms (Day 86 接入点, toResult → 先落 alarm)
     │     6 级降级 + Redis SETNX ai-alarm:{d}:{t}:{date} 24h
     └─ 【Step B】 @Nullable Producer.send (AmqpException 降级不返回 5xx)
             │
             ▼ InspectionReportMessage JSON
② RabbitMQ inspection.exchange → queue (DLX/DLQ no TTL)
     InspectionReportConsumer ackMode=MANUAL (@Profile("!test"))
     ├─ Redis SETNX inspection:{reportDate}:{siteId}/all (24h, 多站点部分命中语义)
     ├─ Redis↓ → 降级：直接推送
     ├─ 成功 basicAck / 失败 nack(requeue=false) → DLQ
     └─ InspectionPushGateway.sendToSites (siteIds 路由)
        SseEmitterRegistry (30min + 三重泄漏防护 @PreDestroy)
        sendSafely：IOException → 自动 remove 失效会话
             │
             ▼ text/event-stream SSE
③ PushController GET /api/push/inspection @RequireRole(VIEWER+) @OperationLog(PUSH/SSE)
   JwtAuthFilter 双 token 源：Header Bearer (优先) > ?token= query fallback (仅 /api/push/**)
   SiteAccessService：ADMIN=empty(全站点)；非 ADMIN 空集合=403（P0 安全越权防护）
             │
             ▼ 反代
④ nginx location /api/push/
   proxy_buffering off; proxy_cache off; proxy_read_timeout 3600s
   proxy_http_version 1.1; Connection ""; access_log off (防 token 泄漏)
             │
             ▼ ?token=  EventSource
⑤ 浏览器 InspectionReport.vue
   onopen(connected) / onerror readyState=2(手动 3s 重试)
   addEventListener('inspection-report')：reportDate Set 去重（第三道幂等）
   ≤ 50 条卡片；XSS 双转义；异常折叠卡 + severity 徽章 + autoAlarmCount
```

#### 3 张附表

| 表 | 内容 | 位置 |
|----|------|------|
| §2a.1 三道幂等防线 | Consumer Redis / AutoCreator Redis / 前端内存 Set；TTL 24h / 24h / 会话；失败降级=宁可重复不丢（对齐 ADR 0031 §6） | §2a.1 |
| §2a.2 三道安全点 | PushController 非 ADMIN 空 siteIds=403；JwtAuthFilter 仅 SSE 路径启 query fallback（header>query 防覆盖攻击）；nginx /api/push/ access_log off（防 ?token 进日志） | §2a.2 |
| §2a.3 扩展点（Phase 4+ / Phase 5 复用） | cron 每日巡检（L）、多实例 Redis PubSub 全局 Push（M）、SSE 心跳（L）、PLC MQTT 接入复用同一 PushGateway（M） | §2a.3 |

#### Application-Architecture.md 其他同步更新

| 位置 | 更新 |
|------|------|
| §1 头部 Version/BasedOn | 2.4→2.5；BasedOn 增 "V7-V15 迁移 / Day 66-87 三项" |
| §3 中间件整合 mq/ 包描述 | "Push Gateway/SSE/Vue 待实现" → "全部完工，见 §2a 完整图" |
| §6 演进路线 Phase 4 内容/状态 | Day 66-87 含 ADR0031 / AiAlarmAutoCreator / 前端工业化 描述 |
| §5 数据库 Flyway 列表 | V14 PUSH/SSE + V15 AUTO_ALARM 追加 |

### 1.3 Day 88 文档 vs DAILY_ROADMAP Day 88 预期对照

DAILY_ROADMAP L552 Day 88 要求 = **「全链路联调：数据→AI分析→报警→MCP查询→Agent总结」**。Day 87 §5 明日计划给出 4 项候选（周复盘 / 架构图 / 端到端联调 / prompt 增强）。Day 88 执行了优先级 P0 的**文档产出项（周复盘+架构图）**，联调项顺延（需要 compose 环境 + DeepSeek api_key 启用，是运行态任务，当前为纯文档会话）作为可选后续。

对齐策略：
1. **Week13 复盘 + 推送链路完整架构图 = Day 88 必须交付**（治理类/架构类产出，Phase 4 收官前的唯一锚点文档，没有这两篇 Day 89 重构会在"边界不清楚"的情况下反复改）；
2. **全链路联调 = 顺延到 Day 88 之后 / Day 89 重构前验证**（或者作为 feat/day88-e2e 子任务分支，等 compose 启动时再执行，不阻塞治理类日程）；
3. **巡检 Agent prompt 增强 = 顺延**，同联调，不影响今天的架构文档。

***

## 2. 设计决策（文档类）

### 2.1 架构图用 ASCII，不用 Mermaid / PlantUML

- **零依赖**：不需要安装 mermaid-cli / 额外 npm/mvn plugin，任何编辑器可读可 diff；
- **Git 友好**：ASCII 行级差异一目了然，架构图调整可以 code review；
- **对齐业界**：Google / Stripe 内部架构文档使用 ASCII 是主流（避免 binary 图片进 Git 仓库）；
- **ADR 0031 §边界驱动**：ASCII 每一行就是一个组件/一条责任线，和 Week13 §2.1 的"责任拆分决策表"1:1 对应，不会写着写着多出一条"隐含通道"。

### 2.2 幂等/安全/扩展点用三张表"钉死"，不写成自由文本

ASCII 架构图描述"正常走法"，附表锁"异常分支"：
- 架构图里写不下 Redis SETNX false / null Redis / DB 异常 6 条降级 → 放在 §2a.1 表单独列；
- 架构图里写不下 "query token fallback 只对 /api/push/ 路径，REST 路径不启" 这种细粒度规则 → 放在 §2a.2 表；
- 扩展点写进表，Phase 5 PLC 接入时不需要翻历史文档，直接按表改造成本评估。

### 2.3 Week13 复盘不提前做 Day 89 的"重构计划"

Week13 §六、下周计划只把 DAILY_ROADMAP Day 88-91 原样细化，不做"实现方向"预判——因为：
- Day 89 重构需要先跑"AI 模块代码审计"（多少公共组件？哪些异常未统一？哪里漏限流？），今天做会是拍脑袋；
- 治理类 day 的内容必须"当天可审计"，计划不写具体细节避免明天自己打脸。

***

## 3. 文档同步

- [AGENTS.md §3](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/AGENTS.md)：当前状态 Day 88 说明同步；下一步更新为「Day 89 AI 模块重构 +（可选）端到端联调」；已完成模块补 Week13.md + Application-Architecture V2.5 §2a 推送链路图。
- 本文件（Day88.md）：产出明细 + 对照 DAILY_ROADMAP 执行说明 + 文档类设计决策 + 明日计划。
- [Week13.md](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/REVIEW/Week13.md)：周复盘完成
- [Application-Architecture.md §2a](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/docs/Architecture/Application-Architecture.md#L102)：推送链路完整图 + 3 附表 + 版本 V2.5 bump

***

## 4. 遗留 & 风险

1. **DAILY_ROADMAP Day 88 联调项顺延**：需要 Docker compose 起 MySQL 8.4 / Redis 7.4 / RabbitMQ 4.0 + Spring Boot dev profile + `.env` 中 `DEEPSEEK_ENABLED=true + DEEPSEEK_API_KEY=xxx` 启用；建议在 feat/day88-e2e 分支执行，不占用治理类 day 的上下文；
2. **巡检 Agent prompt detectedIssues 未约束**（Day 86 §4 风险 1 / Week13 §五 遗留 3），联调时一起加；
3. **架构图 §2a.3 扩展点全部未实现**（cron / Redis PubSub / 心跳 / PLC 接入），表列出来只是作为 Phase 5 入口定位，不作为 Tech-Debt 债；
4. **文档类 day 容易让 commit 历史"空心"（没有代码增量）**：Day 89 必须做真正代码级重构（公共组件抽取 + 异常处理 + 限流），不能连续两天纯文档。

***

## 5. 明日计划（Day 89 候选，严格对齐 DAILY_ROADMAP）

1. **重构 AI 模块代码**（DAILY_ROADMAP L553 Day 89）：
   - 公共 util 抽取：`frontend/src/utils/escapeHtml.js` 抽 RagAssistant/DeviceDetail/AlarmList/InspectionReport 4 处 `escapeText` 函数（当前 4 份重复）；
   - `escapeText` 同步补到后端 DTO 层 toString 审计摘要（可选，避免 operation_log detail 截断时含未转义 XSS payload 进日志系统）；
   - AI 异常处理统一：AiService / 3 个 Agent Service 的"JSON 解析失败降级为 plain text"统一成一个 `AiJsonFallbackUtil`（当前 AiService / DeviceStatus / McpInspection 3 处各写一套 try-catch fallback）；
   - AI 限流：`/api/ai/**` 端点加独立 RateLimiter（跟登录限流 QPS 不同，AI 接口 QPS 更低 + 每用户 token 桶，对齐 DeepSeek token 成本）；
2. **（可选，穿插）** 端到端联调执行记录（Day 88 §4.1 顺延项）：起 compose → 登录 admin → curl inspection-report → 浏览器验证 SSE + alarm + operation_log 三张表；
3. **代码级重构单测补齐**：新增 escapeHtml 单测 / AiJsonFallbackUtil 单测（覆盖 null/非法 JSON/半合法 JSON 截断 3 场景），保持 330→≥335 单测全绿。

***

> 完成时间：2026-09-03 03:05（Asia/Shanghai）
> 维护者：AI 助手 + hula0710
