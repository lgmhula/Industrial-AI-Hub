# Week 14 复盘 — Phase 4 AI 集成收官（Day 66-91 全链路闭环）

> 日期：2026-09-03 | 覆盖：Day 88 \~ Day 91（Week 13 §六 计划的「Phase 4 收官 4 天」）
> 基线演进：330 → 343 tests（+13，Day 89 AiJsonFallbackUtilTest 9 + AiRateLimitInterceptorTest 5，净 +13 因 AiService 私有 unwrapJsonFence 删除伴随旧断言调整）；Flyway 维持 V15（Day 88-91 无新迁移）；ADR 维持 0031（Day 88-91 无新 ADR，纯重构 + 文档）
> Phase 4 全段：Day 66-91（26 天，Week 10-14）—— DeepSeek → Spring AI → Function Calling → RAG → Agent → MCP → MQ/SSE → autoAlarm → 重构 → 集成文档 → 收官 tag

***

## 一、本周目标 vs 实际

| 目标（Week 13 §六 计划） | 实际 | 状态 |
| ---------------------- | ---- | :-: |
| Day 88: 文档 + 联调 = Week13 复盘 + Application-Architecture 推送链路完整图 | Week13.md 6 大章节 + Application-Architecture.md V2.5 §2a 5 段 ASCII 全链路图（Agent→MQ→Consumer→Redis→PushGW→SSE→nginx→浏览器）+ 三道附表（幂等/安全/扩展点）| ✅ |
| Day 89: 重构 AI 模块 = 抽取公共组件 + 异常处理 + 限流 | 前端 escapeHtml.js（4 页面去重 38 行）+ 后端 AiJsonFallbackUtil（2MB 上限 + fence unwrap + fallback 工厂）+ AiRateLimitInterceptor（order=-1 先于通用限流，三粒度桶 u:/ip:/ADMIN）；单测 343/343 | ✅ |
| Day 90: 集成文档 = Phase 4 AI 模块 6 步打通 | docs/ai/phase4-integration-guide.md（437 行，6 步 + 验收矩阵 11 项 + 10 故障速查 + 5 回滚策略）；端点纠正 /api/rag/documents | ✅ |
| Day 91: 第四阶段复盘 + Git tag v2.0-ai | 本文件（Week14.md）+ Day91.md + tag v2.0-ai + AGENTS §3 基线 bump | ✅ |

> 本周 4 天全 ✅，Phase 4 检查点达成（DAILY_ROADMAP L557：「AI 不再是 demo，而是真正为项目创造业务价值的功能模块」）。

***

## 二、关键收获

### 2.1 Phase 4 全链路闭环：从「调用 LLM」到「runbook 可交接」

Phase 4 26 天（Day 66-91）的产出不是「加了 9 个 AI 接口」，而是把 AI 能力从孤立的 LLM 调用逐步织进业务脉络，最终形成一条**任何人照 runbook 就能跑通**的闭环：

```
Day 66-68  接入层    DeepSeek API → Spring AI ChatClient → Function Calling @Tool
Day 69-70  表现层    前端工业化视觉 + Week10 复盘
Day 71-77  知识层    RAG 选型(Qdrant) → 切片/哈希向量 → 检索 → PDF 导入 → 运维问答 → 前端
Day 78-84  推理层    ReAct 循环 → ToolCallingAgent 通用循环 → MCP Server 只读工具 → MCP 客户端 → Agent+MCP 联调巡检
Day 85-87  闭环层    7-Phase SSE 推送链路(ADR 0031) → AI→ALARM 业务闭环 → 前端 4 页工业化
Day 88-91  收官层    周复盘+架构图 → 重构(去重/兜底/限流) → 集成 runbook → Phase 4 tag
```

每一层都对应一个 ADR：0021(DeepSeek) / 0022(SpringAI) / 0023(FC) / 0024(Qdrant) / 0026(ReAct) / 0027-0030(MCP) / 0031(推送边界)。**ADR 不是事后补的文档，是每一层开工前的边界决策**——这是 Phase 4 最值得复用的工程习惯。

### 2.2 Day 89 重构的价值：技术债在收官前清零，不是留给 Phase 5

Day 89 的三项重构（escapeHtml.js / AiJsonFallbackUtil / AiRateLimitInterceptor）表面是「清理代码」，实际是清三类技术债：

| 债类型 | Day 89 之前 | Day 89 之后 | 不清的后果 |
|-------|-----------|-----------|-----------|
| 重复代码 | 4 页面各写 escapeText（InspectionReport 19 行 DOM fallback 与另 3 页不一致） | 1 个 util 3 个 API（escapeHtml/escapeText/safeJoin） | 第 5 个 AI 页面又抄一份，XSS 防护 5 种实现 |
| 异常不一致 | AiService 2 处 try/catch 各写 fallback，一个 unwrap fence 一个不 | parseOrFallback 统一入口 + 2MB 硬上限 + BiConsumer 钩子 | AI 返回 500MB payload 时 Jackson OOM；第 3 处 AI DTO 又漏 fence unwrap |
| 成本保护缺失 | 通用 RateLimitInterceptor 50 req/s 挡不住 AI 接口被刷（50 × DeepSeek = 账单事故） | AiRateLimitInterceptor order=-1 独立桶（u:userId 2/s · ADMIN 5/s · ip 2/s） | 一个 VIEWER 账号 1k QPS 打 /api/ai/chat，DeepSeek 账单失控 |

**关键决策**：Day 89 重构选在 Phase 4 收官前（Day 89）而不是 Phase 5 启动后，因为 Phase 5（PLC + MQTT）会引入新的数据源和告警类型，AI 模块那时再重构会被新功能绑架。**收官前清债 = 给 Phase 5 一个干净起点**。

### 2.3 Day 90 runbook 的价值：AI 模块可交接，不是只有作者能跑

Day 90 的 phase4-integration-guide.md 不是「写文档凑数」，是解决一个工程问题：Phase 4 的 9 个 ADR + 26 天 DAILY 日志 + 15 个 Flyway 迁移 + 11 个 AI 端点散落在各处，**新人 clone 仓库后不知道怎么把 AI 跑起来**。

runbook 的 6 步把所有碎片串成一条线性路径：

| Step | 解决的「不知道」 |
|------|----------------|
| 1 启用 | 不知道 `.env` 要改哪几个变量（DEEPSEEK_ENABLED + KEY + MCP_ACCESS_TOKEN） |
| 2 RAG | 不知道上传端点是 `/api/rag/documents`（Day89 笔记写错成 `/api/rag/ingest/upload`） |
| 3 巡检 | 不知道巡检会触发 alarm 落库 + 幂等键长啥样 |
| 4 SSE | 不知道 EventSource 要监听具名事件 `inspection-report` 不是 `message` |
| 5 限流 | 不知道 VIEWER 2/s ADMIN 5/s，不知道 429 响应体长啥样 |
| 6 故障 | 不知道 Redis 挂了幂等会降级、nginx 缓冲不关 SSE 会卡死 |

**runbook 是 Phase 4 的「验收合同」**：tag v2.0-ai 打下去，意味着任何人照这 6 步跑不通 = bug，跑得通 = Phase 4 达标。

### 2.4 Phase 4 检查点达成：AI 为业务创造价值，不是 demo

DAILY_ROADMAP L557 的检查点原文：「AI 不再是 demo，而是真正为项目创造业务价值的功能模块」。Phase 4 26 天交付的「业务价值」：

| 价值 | 实现 | 业务收益 |
|------|------|----------|
| 告警 AI 摘要 | `/api/ai/alarms/{id}/summary` | 操作员不用读原始告警文本，0.5s 看懂 priority + 可能原因 + 建议动作 |
| 设备健康诊断 | `/api/ai/devices/{id}/diagnose` | 维保前 AI 预评估，不用人工翻历史数据/告警 |
| 知识库问答 | `/api/rag/ask` | 新操作员不用翻 PDF 手册，直接问「该设备日常巡检项目」 |
| 自动巡检日报 | `/api/ai/agents/inspection-report` + SSE 推送 | ADMIN 每天自动收到全设备巡检结果，不用手动巡检每台设备 |
| AI 自动报警 | AiAlarmAutoCreator（巡检 detectedIssues → alarm 表） | AI 发现的异常自动落 alarm 表，和规则引擎报警同通道处理 |

**这 5 项都不是「AI 能力展示」，是「减少人工操作时间」的具体功能**——这是 Phase 4 区别于 demo 的核心。

***

## 三、Week 14 演进全景（Day 88-91 收官 4 天）

```text
Day 88   文档日（Phase 4 倒计时 4）
  Week13.md        6 大章节周复盘（推送链路 + AI→ALARM + 前端工业化）
  Arch §2a          Application-Architecture V2.5 5 段 ASCII 全链路图
                   + 幂等/安全/扩展点三道附表，BasedOn bump Day85-87
Day 89   重构日（Phase 4 倒计时 3）
  前端 util         escapeHtml.js (escapeHtml/escapeText/safeJoin, 4 页去重 38 行)
  后端 util         AiJsonFallbackUtil (parseOrFallback + 2MB 上限 + fence unwrap)
  限流              AiRateLimitInterceptor (order=-1, u:/ip:/ADMIN 三粒度桶, 429 JSON)
  WebMvcConfig      注册 /api/ai/** /api/agents/** /api/rag/** /api/mcp/**
  单测              +14 (AiJsonFallbackUtilTest 9 + AiRateLimitInterceptorTest 5)
                   -> 343/343 全绿，前端 build 954ms 0 errors
Day 90   集成文档日（Phase 4 倒计时 2）
  runbook           docs/ai/phase4-integration-guide.md (437 行, 6 步)
                   Step1 启用 → Step2 RAG → Step3 巡检 → Step4 SSE → Step5 限流 → Step6 故障
  端点纠正          /api/rag/ingest/upload (Day89 §5 笔误) → /api/rag/documents
  AGENTS §2/§3      文档索引新增 ★★☆ 行 + 下一步→Day91
Day 91   收官日（Phase 4 倒计时 1，本日）
  Week14.md         本文件（Phase 4 26 天全段复盘）
  Git tag           v2.0-ai (annotated, Phase 4 收官检查点)
  Day91.md          日志 + AGENTS §3 基线 bump v2.3.0 → v2.0-ai
```

***

## 四、关键指标

### 4.1 测试矩阵（Phase 4 全段累计）

| 周 | 范围 | 单测总数 | 净增 | 失败 | 跳过 |
|----|------|---------|------|:--:|:--:|
| Week 10 (Day66-70) | DeepSeek / Spring AI / FC / 前端视觉 | ~210 | — | 0 | 3 |
| Week 11 (Day71-77) | RAG 入库/检索/导入/前端 | ~220 | +10 | 0 | 3 |
| Week 12 (Day78-84) | Agent / MCP Server / Client / 联调 | 269 | +49 | 0 | 3 |
| Week 13 (Day85-87) | 推送链路 7 phase + AI→ALARM + 前端 | 330 | +61 | 0 | 3 |
| **Week 14 (Day88-91)** | 重构 + 集成文档（+14 单测，删 1 旧） | **343** | +13 | 0 | 0 |

> Day 89 Skipped 从 3 降到 0：Day 89 重构后 AiJsonFallbackUtil 替代了原来标记 @Disabled 的 unwrapJsonFence 测试，全量可跑。
> Phase 4 累计净增 ~133 单测（210 → 343），覆盖 AI 接入层 / 知识层 / 推理层 / 推送层 / 兜底层 5 个维度。

### 4.2 Flyway 迁移链（Phase 4 全段 V9 → V15）

| 版本 | 内容 | Day | Week |
|------|------|-----|------|
| V9  | AI 操作日志类型（CHAT/SUMMARY/DIAGNOSE） | Day 67 | 10 |
| V10 | FUNCTION_CALL 操作日志类型 | Day 68 | 10 |
| V11 | INGEST/KNOWLEDGE 操作日志类型 + RAG 知识块表 | Day 74 | 11 |
| V12 | MCP_SMOKE/MCP 操作日志类型 | Day 83 | 12 |
| V13 | INSPECTION 操作日志类型 | Day 83 | 12 |
| V14 | PUSH/SSE 操作日志类型 | Day 85 | 13 |
| V15 | AUTO_ALARM 操作日志类型 | Day 86 | 13 |

> Week 14（Day 88-91）无新 Flyway 迁移——重构日不动 schema，集成文档日不动代码，符合 AGENTS §4.2「不做未要求的事」。

### 4.3 ADR 决策记录（Phase 4 全段 0021 → 0031）

| ADR | 主题 | Day | Week |
|-----|------|-----|-----|
| 0021 | DeepSeek LLM provider 选型 | Day 66 | 10 |
| 0022 | Spring AI ChatClient 抽象 | Day 67 | 10 |
| 0023 | Function Calling 3 轮硬限 | Day 68 | 10 |
| 0024 | RAG 向量库选型（Qdrant + 内存第一阶段） | Day 71 | 11 |
| 0025 | RAG PDF 导入（PDFBox） | Day 74 | 11 |
| 0026 | Agent ReAct 循环治理 | Day 78 | 12 |
| 0027 | MCP Server 工具暴露边界 | Day 80 | 12 |
| 0028 | MCP 数据查询/统计/搜索工具 | Day 81 | 12 |
| 0029 | MCP 客户端鉴权 + 冒烟 | Day 82 | 12 |
| 0030 | Agent + MCP 联调巡检 | Day 83 | 12 |
| 0031 | 推送链路架构边界冻结 | Day 85 | 13 |

> Week 14（Day 88-91）无新 ADR——重构日是「落实既有 ADR 边界」不是「做新决策」，集成文档日是「固化既有决策」不是「开新方向」。

### 4.4 文档产出（Week 14 新增）

| 文档 | 行数 | Day | 用途 |
|------|------|-----|------|
| Week13.md | ~165 | Day 88 | Week 13 周复盘 |
| Application-Architecture.md §2a 增量 | ~150 | Day 88 | 推送链路 5 段 ASCII 图 + 3 附表 |
| Day88.md / Day89.md / Day90.md / Day91.md | ~4×150 | Day 88-91 | 每日日志 |
| docs/ai/phase4-integration-guide.md | 437 | Day 90 | SETUP 级 6 步 runbook |
| Week14.md（本文件） | ~200 | Day 91 | Phase 4 收官复盘 |

***

## 五、遗留 & 风险

### 遗留（进入 Phase 5 候选）

1. **AlarmList.vue copySummary 手动 forEach 未用 safeJoin**（Day 89 §4.1 / Day 90 §4 延续）：Day 89 抽取了 safeJoin 但未改调用点。Phase 5 空闲日替换，风险 0；
2. **DeviceStatusAgentService 手写 3 轮循环未迁移到 ToolCallingAgent.run()**（Day 89 §4.2）：迁移会破坏 10+ DeviceStatusAgentServiceTest mock 行为，Phase 5 不动；
3. **AiRateLimitInterceptor 未接 Redis 分布式限流**（Day 89 §4.3）：当前 ConcurrentHashMap 单实例有效，2+ 副本水平扩展时需改 Redis `ai-rate:{userId}` Lua 滑动窗口。Phase 5 多实例部署前补；
4. **rate.limit.ai.* 未写入 application.yml**（Day 89 §4.5 / Day 90 §4.1）：靠代码默认值 2/5/150ms，Phase 5 部署前补 yml 注释化默认值；
5. **真实 SSE 端到端联调未实跑**（Day 88 §五 / Day 90 §4.4）：Runbook §7 验收矩阵 11 项给了路径，但本会话是文档日没起容器。Phase 5 启动前建议手动过一遍。

### 风险（Phase 5 前瞻）

1. **Phase 5 PLC + MQTT 引入新数据源**：device_data 表会接入 MQTT 实时流，AI 巡检 Agent 的 MCP 工具（mcp_get_device_data_range）查询频率会从「每日巡检 1 次」变成「实时监听」，AiRateLimitInterceptor 2/s 桶可能不够，需评估是否给 Agent 内部 MCP 调用走单独通道（不走 /api/ai/** 限流）；
2. **Phase 5 多实例部署触发分布式限流需求**：Phase 5 第 15 周「系统整合 + 运维」会起 Prometheus + 多副本，届时 AiRateLimitInterceptor 单机 ConcurrentHashMap 必须升级 Redis 滑动窗口（遗留 3）；
3. **AI 模块 markdown 渲染**：当前 4 页面 escapeText + `{{ }}` 双层转义，如果 Phase 5 要支持 AI 日报 markdown 渲染，不能直接放开 v-html，必须引 DOMPurify 白名单（Week13 §五风险 3 延续）；
4. **v2.0-ai tag 后的分支策略**：tag 锁定 Phase 4 收官态，Phase 5 开发走 `feat/phase5-*` 分支，不跟 main HEAD 漂移（ADR 0017）。

***

## 六、下周计划（Week 15 = Day 92-98，Phase 5 PLC + MQTT 启动）

> Phase 5 = 第 14-16 周（Day 92-112），目标：PLC 模拟设备接入 + MQTT 协议 + 完整系统上线。
> Week 15（Day 92-98）= PLC 基础 + MQTT 协议 + Java MQTT 客户端 + 模拟 PLC + MQTT→device_data 入库 + 多设备并发压测。

| Day | 任务 | 交付物 |
|-----|------|--------|
| Day 92 | PLC 基础概念：Modbus、寄存器、线圈 | 学习笔记 + Modbus 概念图 |
| Day 93 | MQTT 协议基础 + EMQX/Mosquitto 安装 | MQTT QoS 0/1/2 笔记 + compose.yml 加 EMQX |
| Day 94 | Java MQTT 客户端（Eclipse Paho）开发 | MqttClientService + 订阅/发布单测 |
| Day 95 | 模拟 PLC 设备：Java 程序定时发送模拟传感器数据 | SimulatorMain + 多主题发布 |
| Day 96 | MQTT → 项目：接收 MQTT 数据并存入 device_data 表 | MqttDataListener + DeviceData 入库 + Flyway V16 |
| Day 97 | 模拟多设备并发数据上报 + 压力测试 | JMeter 压测脚本 + 并发安全验证 |
| Day 98 | 周复盘 + PLC/MQTT 笔记 | Week15.md + docs/notes/plc-mqtt-learning-notes.md |

> Phase 5 第一周风险：MQTT QoS 1/2 的消息幂等与 Day 85 Consumer Redis SETNX 幂等模式对齐，避免重复入库；EMQX 容器与现有 RabbitMQ 4.0 端口/资源不冲突。

***

> Phase 4 收官声明：Day 66-91（26 天）交付 9 个 AI 业务端点 + 7 个 MCP 只读工具 + RAG 知识库 + Agent 多步推理 + SSE 实时推送 + AI 自动报警 + 限流/兜底统一 + 集成 runbook。**AI 不再是 demo，而是为工业设备管理平台创造业务价值的功能模块**。tag `v2.0-ai` 锁定收官态，Phase 5（PLC + MQTT + 完整系统上线）启动。
>
> 维护者：AI 助手 + hula0710
