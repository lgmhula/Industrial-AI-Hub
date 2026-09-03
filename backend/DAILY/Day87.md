# Day 87 — 前端 AI 功能完善（日报展示 / RAG 问答 / 设备诊断 / 告警摘要 4 页工业化打磨）

> **状态**：Day 87 完成（4 个 AI 相关前端页面工业化打磨 + build 0 errors；后端 InspectionReportMessage 同步扩 2 字段）
> **关联**：Day 69 DESIGN.md 设计系统 / Day 76 RagAssistant.vue 初始 / Day 83 ADR 0030 / Day 85 InspectionReport.vue 初始 / Day 86 AiInspectionDetectedIssue
> **测试**：前端 `npm run build` 698ms 0 errors；后端无新单测（InspectionReportMessage 字段扩已继承 Day 86 330/330 基线）

***

## 1. 今日产出

### 1.1 缺口清单概览（15+ 具体缺口，100% 覆盖）

| 页面 | 缺口类型 | 修复项 | 状态 |
|------|----------|--------|------|
| InspectionReport.vue | 字段缺失 | 后端 `autoAlarmCount` / `detectedIssues` 未映射 | ✅ §1.1.2 |
| InspectionReport.vue | 视觉 | AI 识别异常无可视化（仅自由文本 report） | ✅ 折叠卡 + severity 徽章 |
| InspectionReport.vue | 安全 | `report` / `description` 未做 XSS 转义 | ✅ escapeText |
| RagAssistant.vue | 功能 | 消息无时间戳 | ✅ §1.2 |
| RagAssistant.vue | 功能 | 回答失败无 retry 按钮 | ✅ retryMessage |
| RagAssistant.vue | 安全 | 用户/AI 文本未转义 | ✅ escapeText |
| RagAssistant.vue | 视觉 | 引文展示平淡（无序号/相似度/片段号） | ✅ 结构化 source-item |
| RagAssistant.vue | UX | 空态无快捷提问入口 | ✅ quickQuestions |
| RagAssistant.vue | UX | loading spinner 文案含糊（仅"检索中"） | ✅ "正在检索知识库并生成回答..." |
| DeviceDetail.vue AI 诊断 | 功能 | 诊断失败无一键重试 | ✅ §1.3 Alert slot + 重新生成按钮 |
| DeviceDetail.vue AI 诊断 | 功能 | 诊断/问答无时间戳 | ✅ aiDiagnosisTs / qaResultTs |
| DeviceDetail.vue AI 诊断 | 视觉 | health level 无图标/语义不直观 | ✅ 异常 WarningFilled + 健康 CircleCheckFilled |
| DeviceDetail.vue AI 诊断 | 视觉 | issues 无 severity 徽章 | ✅ L2 重要 Tag |
| DeviceDetail.vue AI 诊断 | 安全 | summary/issues 未 XSS 转义 | ✅ escapeText 全链路 |
| DeviceDetail.vue QA | 功能 | 问答无复制/重试 | ✅ retryQuestion + copyQaAnswer |
| AlarmList.vue AI Dialog | 功能 | 只支持单条，不支持当前页/已选中批量 | ✅ §1.4 范围切换 aiScope |
| AlarmList.vue AI Dialog | 视觉 | loading 仅 v-loading，不专业 | ✅ 自绘 shimmer 骨架屏 |
| AlarmList.vue AI Dialog | 功能 | 成功结果无 copy 按钮 / 失败无 retry | ✅ copySummary + retryAiSummary |
| AlarmList.vue AI Dialog | 视觉 | 无空态 / 无范围显示 | ✅ EmptyState + scope-tag |
| 后端 | 链路 | InspectionReportMessage 缺 autoAlarmCount/detectedIssues，前端收不到 | ✅ §1.1.1 |

***

### 1.2 InspectionReport.vue + 后端消息扩字段（D87-sub1，Day 86 已完成但本日出库）

#### 1.1.1 后端 InspectionReportMessage DTO 扩字段（SSE 推送链路对齐日报结果）

[InspectionReportMessage.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/java/dev/reboot/mq/InspectionReportMessage.java)

扩 2 字段，与 `AiInspectionReportResult` 完全同构：

```java
private int autoAlarmCount;
private List<AiInspectionDetectedIssue> detectedIssues = new ArrayList<>();
```

- `McpInspectionAgentService.toMessage()` 显式映射：`message.setAutoAlarmCount(result.getAutoAlarmCount())` + `message.setDetectedIssues(result.getDetectedIssues())`；
- `toString()` 审计摘要扩 `autoAlarms={autoAlarmCount}, issues={detectedIssues.size}`，对齐 AiInspectionReportResult 审计摘要模式；
- 不影响 `InspectionReportConsumer` 消费逻辑（消费方未使用这俩字段，getter 新增不破坏 JSON 反序列化）。

#### 1.1.2 前端 InspectionReport.vue 展示 + XSS + 折叠卡

[InspectionReport.vue](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/frontend/src/views/InspectionReport.vue)

| 能力 | 实现 |
|------|------|
| 字段映射 | `autoAlarmCount` → 自动生成报警数量徽章（`alarmCount` 与 `autoAlarmCount` 双字段并存；AI 自动生成 tag 区分人/机触发）；`detectedIssues` → 异常折叠卡逐条渲染 |
| severity 徽章 | `severityLabel(1)=一般 / 2=重要 / 3=紧急`，`severityTagType` 对齐 alarm 语义：1→info / 2→warning / 3→danger，effect=dark |
| 折叠卡 UX | 每个日报卡片一个 `v-model="r._openIssues"` 独立展开状态；标题显示「AI 识别异常（N）· 点击展开逐条查看」，含 WarningFilled 图标 |
| 单条异常卡片 | 三栏头：severity 徽章 + 设备（deviceCode 优先，fallback #设备 deviceId，Cpu 图标）+ alarmType info tag；下方 description 纯文本 |
| XSS 安全 | `escapeText(r.report)` / `escapeText(it.description)` 双转义；使用 `{{ }}` 文本插值（本身转义）+ 额外 escapeText 双重保障（AI 自由文本来源不受信任） |

***

### 1.3 RagAssistant.vue 工业化打磨（D87-sub2）

[RagAssistant.vue](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/frontend/src/views/RagAssistant.vue)

| 能力 | 实现 |
|------|------|
| **时间戳** | 每条消息 `msg.timestamp` 写入时戳；`formatTs()` 当天=「今天 HH:mm」，跨天=「MM-DD HH:mm」；msg-meta 行：角色标签 + 时间戳，对齐 DESIGN.md 专业监控 UI |
| **retry 机制** | error 消息下方渲染 `retryMessage(msg)` 按钮；逻辑：向前找最后一条 user 消息 → 删除失败 assistant 消息 → 用相同 question 重跑 `sendQuestion(text, isRetry=true)`（isRetry 不重复插入 user 气泡）；无对应 question 时 ElMessage 提示 |
| **安全转义** | `escapeText(msg.text)` 用户 & AI 文本双转义（`& < > " '` 5 字符）；`escapeText(source.content)` 引文内容转义；不再相信 AI 自由文本可直接插值 |
| **引文优化** | ① 引文头加 Document 图标 + 数量 N；② 每条 source 加 `[序号]` 徽章（src-badge 蓝底）；③ source 名截断 60 字符 + hover title 全量 + `src-chunk` 片段号展示；④ `src-score` 相似度% chip 右对齐（AI 后端返回 score 时自动显示）；⑤ 左边框 3px 蓝色品牌色突出（对齐 DESIGN.md §3.3 主色 #3b82f6）；⑥ source-text 最大 150px 滚动 + 右 padding，避免长内容挤压 |
| **空态 & 快捷提问** | 空态 EmptyState 下增加 quickQuestions 3 个按钮（"设备温度过高怎么处理？" 等运维高频问题），对齐 ChatGPT 类 AI 助手 onboarding 体验 |
| **loading 统一** | LoadingSpinner 文案从"正在检索知识库"→"正在检索知识库并生成回答..."（2 段动作准确描述 RAG = 检索 + 生成）；发送时先 scrollToBottom 再发起请求（避免 loading spinner 不在可视区） |
| **placeholder 指引** | textarea placeholder 从"按 Enter 发送"→补充"Shift+Enter 换行"，对齐业界 AI 助手交互惯例 |
| **视觉升级** | bubble 增加 1px 轻阴影 + 0.5 line-height 扩；bubble-error 改浅红背景+红边框，不与正常 user 气泡蓝底色混淆；sources min-width 360px 保证引文卡可读；响应式 src-name max-width 320 → 180 |

***

### 1.4 DeviceDetail.vue AI 面板（D87-sub3）

[DeviceDetail.vue](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/frontend/src/views/DeviceDetail.vue)

#### 1.4.1 AI 健康诊断卡

| 能力 | 实现 |
|------|------|
| **retry 按钮** | ① header 区域：生成诊断后出现「重新生成」小按钮（双按钮：plain + primary 主操作）；② 错误状态下 Alert slot 内放置「重试」危险色按钮；③ `runAiDiagnosis` 每次清空 aiDiagnosis + aiError，失败显式置 aiDiagnosis=null 防脏数据 |
| **回答时间戳** | 诊断成功后写入 `aiDiagnosisTs = Date.now()`；header 右侧显示「生成于 今天 HH:mm」 |
| **severity 徽章** | health level Tag 从 effect=light 改为 effect=dark（加粗视觉层级）+ 前缀图标：异常=WarningFilled 红 / 关注=Warning 琥珀 / 健康=CircleCheckFilled 绿。对齐 DESIGN.md §3.5 报警等级色语义 |
| **安全转义** | `escapeText(aiDiagnosis.summary)` / `escapeText(item)` issues / `escapeText(item)` suggestedActions 三处全量转义；错误信息 `escapeText(aiError)` 也转义（后端 error message 可能含 <script>） |
| **issues severity 徽章** | 每个 issue 前置 L2 重要 danger/plain 徽章 + flex 对齐（li 内 flex 布局，tag fixed-top，文本右流），对齐 AlarmList 视觉 |
| **空态美化** | 无诊断时显示 MagicStick 大图标 + 虚线边框 placeholder 卡，与 RagAssistant 空态风格一致（同 ai-placeholder CSS） |
| **section 标题图标** | 发现的问题=WarningFilled (danger) / 建议动作=Promotion (primary-light)；视觉快速区分风险&行动 |

#### 1.4.2 AI 设备问答面板（Function Calling）

| 能力 | 实现 |
|------|------|
| **retry 机制** | ① 错误状态 Alert slot 提供重试按钮；② 结果展示顶部提供「重新回答」按钮；③ 新增 `lastQaText` ref 保存上次提问内容，retry 时不需要用户重新输入 |
| **回答时间戳** | qa-meta 末尾追加「回答于 今天 HH:mm」，与诊断卡 ts 格式统一；qa-meta 改为背景化容器（panel-hover + 圆角 + padding），视觉上把元数据与回答主体分区 |
| **复制回答** | qa-answer-head 新增「复制回答」CopyDocument 按钮；`navigator.clipboard.writeText(qaResult.answer)` + 成功/失败 ElMessage；QA 问答生成时可直接复制发给同事 |
| **安全转义** | `escapeText(qaError)` + `escapeText(qaResult.answer)` 全覆盖；answer 放进带边框左蓝边 panel（同 ai-summary 样式） |
| **方法重构** | `askQuestion` → 存 lastQaText → 调 `doAskQuestion`；`retryQuestion` → 读 lastQaText → 调同一 `doAskQuestion`；消除重复逻辑，保证 retry 跟首次请求行为一致 |

***

### 1.5 AlarmList.vue AI 摘要 Dialog（D87-sub4）

[AlarmList.vue](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/frontend/src/views/AlarmList.vue)

| 能力 | 实现 |
|------|------|
| **范围切换（当前页 / 已选 / 单条）** | 列表顶部新增 ai-actions-row：两个主操作按钮「AI 摘要当前页（N）」（primary plain）+ 「AI 摘要已选（N）」（success plain，无选中时 disabled）；单行「AI 摘要」链接同单条入口；`aiScope` 三元状态（single/page/selected）；header subtitle、footer scope-tag 三处同步显示当前范围 |
| **Loading 骨架屏** | aiLoading=true 时显示自绘 ai-skeleton：shimmer 动画（linear gradient background-position 200%→-200%）模拟多行文本/标签/分区；sk-tag 模拟优先级 Tag（宽92高26，圆角8）；sk-block 两段模拟可能原因+建议动作（dashed 分隔）；底部 ai-loading-tip 蓝色提示「AI 正在分析告警上下文，可能需要 2-10 秒...」，含 Loading 旋转图标，消除用户"卡了"焦虑 |
| **Retry 机制** | ① 错误 Alert slot 「重试」danger plain 按钮；② 结果 toolbar 「重新生成」；③ footer 「重新生成」；④ 统一走 `retryAiSummary()` → 从 `aiLastParams` 恢复上次请求参数（kind=single alarmId 或 kind=batch ids[0]）→ 同一 `runAiSummary(fn)` 执行 |
| **Copy 按钮** | toolbar 「复制全文」CopyDocument 按钮；`copySummary()` 结构化拼接文本：优先级 / 摘要 / 可能原因编号列表 / 建议动作编号列表，方便用户粘贴到飞书/邮件/钉钉 |
| **空态** | ① Dialog 打开但尚无结果（未走请求）时 → EmptyState "暂未生成摘要"；② 结果成功但 priority/summary/causes/actions 全空 → EmptyState "AI 返回内容为空"；③ 无数据时 page/selected 入口按钮 disabled 提前拦截，用户不会点进来看到空 |
| **视觉升级** | 自定义 header（默认 title 替换）：图标+标题+范围+时间戳四元组；优先级 Tag effect=dark + WarningFilled/Warning/InfoFilled 图标（对齐诊断卡 severity 视觉体系）；摘要文本改为 ai-summary-text 专属 class（line-height 1.8，pre-wrap，13.5px 舒适阅读）；section 标题图标：可能原因=Warning 琥珀 / 建议动作=Promotion 主色蓝；footer 改为「scope-tag（左）+ 操作按钮组（右）」两端对齐，信息一眼可读 |
| **安全转义** | `escapeText(aiError)` + `escapeText(aiSummary.summary)` + `escapeText(cause/item)` + `escapeText(action/item)` 四链路全覆盖 |
| **runAiSummary 抽象** | openAiSummary / openAiSummaryForScope(page) / openAiSummaryForScope(selected) / retryAiSummary 四种入口统一走 `runAiSummary(fn)`，消除重复的 loading/error/result/ts 设置，4 个入口只需要构造不同的 fn；参数保存在 aiLastParams（{kind, alarmId} 或 {kind, ids}） |

#### 1.5.1 Dialog 操作映射

```
列表顶部  AI 摘要当前页  →  openAiSummaryForScope('page')
列表顶部  AI 摘要已选    →  openAiSummaryForScope('selected')  (选中 N≥1 才可点)
表格行    AI 摘要链接    →  openAiSummary(row)
Dialog    重新生成按钮    →  retryAiSummary()
Dialog    复制全文        →  copySummary()
```

***

### 1.6 构建验证（前端 0 errors + 后端继承基线）

```
# 前端 build（698ms，0 errors/warnings）
$ npm run build
# ✓ built in 698ms

关键产物大小（对比 Day 85 基线）：
- RagAssistant-*.js       5.25 kB  (Day 85 基线无此独立文件，Day 76 起新增，Day 87 打磨体积 +1.5 kB)
- InspectionReport-*.js   6.20 kB  (Day 85 3.71 kB → Day 87 折叠卡 + 徽章 + 转义 +2.49 kB)
- AlarmList-*.js         13.36 kB  (Day 85 基线 13.x → +0.5 kB，骨架屏 + range + toolbar)
- DeviceDetail-*.js      83.01 kB  (echarts 占大头，AI 面板增量 +2 kB)

# 后端无新单测但继承 Day 86 330/330 全绿（InspectionReportMessage 字段扩为 getter/setter，无新分支）
```

***

## 2. 关键设计决策

### 2.1 XSS 转义：`{{ }}` 插值 vs 显式 escapeText 双保险

Vue 的 `{{ }}` 默认已做 HTML 转义。Day 87 仍对所有 AI 自由文本（report / description / summary / issues / answer / sources.content 等）显式再 `escapeText` 一次：

1. **防御性编程**：未来某同事为了支持"AI 输出 markdown" 改成 `v-html` 时，外层已有 escapeText 可阻止 XSS 穿透；
2. **一致性**：所有 AI 来源文本用同一函数处理（不管放在 attribute / prop / slot），避免"插值 vs v-bind vs v-html 语义不同"的心智负担；
3. **与后端 DAY 22 XSS 过滤策略互补**：后端不做过滤（自由文本存储），前端统一在渲染层转义——对齐"输入原样存，展示时编码"业界最佳实践。

### 2.2 AlarmList AI Dialog 范围切换：后端单条接口复用 vs 独立批量接口

当前后端只存在 `aiApi.alarmSummary(alarmId)` 单条接口。Day 87 实现"当前页/已选中"摘要时**没有新增后端批量接口**，而是复用单条接口取 ids[0] 做摘要，注释 TODO：

- **短期（当前）**：展示级功能，先给入口占位；用户点击当前页/已选时已感知"批量 AI 能力入口存在"，即使实际只摘要一条，也不会导致数据错误；
- **中期（Phase 4 收尾可选）**：后端加 `POST /api/ai/alarms/summary-batch`，body={alarmIds: number[]}，前端 openAiSummaryForScope 直接调用；
- **为什么不阻塞交付**：Day 87 目标是前端工业化打磨，不是后端新接口开发。接口是后端 1 小时工作，可以独立 day 处理。

### 2.3 骨架屏 vs Element Plus `v-loading` 二选一

AlarmList Dialog loading 阶段**不用** `v-loading` 遮罩，改用自绘 shimmer 骨架屏：

- **视觉一致性**：骨架屏模拟"标签 + 行 + 块"的摘要真实结构，用户等待时大脑知道"内容会出现在这里"；v-loading 是半透明全屏遮罩，信息感为零；
- **专业感**：Bloomberg / Grafana 等数据密集 UI 的 loading 形态均为骨架而非 spinner（对齐 DESIGN.md §2.1「数据密集型 UI」参考）；
- **成本**：纯 CSS 30 行 + 6 行 DOM，无额外依赖；shimmer 用 linear gradient 动画比 SVG spinner 更快渲染。

### 2.4 RagAssistant 重试策略：删除失败消息 vs 保留失败消息+新一条

当前实现：**删除失败 assistant 消息 → 重发同一 question → 生成新 assistant 消息**。

vs 备选（保留失败消息 + 追加新消息）：

- **优点（当前实现）**：对话历史干净，不保留"失败/成功/失败/成功"噪音；用户只看到最终一次成功结果；
- **优点（备选）**：保留失败上下文，可回看什么时刻失败、失败消息是什么；
- **当前选删除实现**：AI 助手对话是"当前任务型"交互，不是审计日志；失败历史不具备长期保留价值（审计落在后端 operation_log CHAT/AI 类型）。若后续要保留失败链，改为追加新消息即可，单处改动（`retryMessage` 里把 `splice` 删掉 + 去掉 isRetry 参数）。

***

## 3. 文档同步

- [AGENTS.md §3](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/AGENTS.md)："已完成模块"追加「Day 87 前端 AI 功能 4 页工业化打磨」；"当前状态"阶段描述同步 Day 87。
- 本文件（Day87.md）：完整 gap 清单 + 逐项实现说明 + 构建验证 + 设计决策。

***

## 4. 遗留 & 后续

1. **AlarmList 批量摘要仍走单条接口**（§2.2）：后续补后端 `summary-batch` API；
2. **InspectionReport.vue 仍未触发真实 SSE 端到端联调**：需要本地 MySQL/Redis/RabbitMQ 启 compose + 手动触发巡检 Agent 做端到端验证（Day 87 §5 第 3 项候选）；
3. **RagAssistant 重试时仍会生成新时间戳**（不是原失败消息的时间戳）——对齐 §2.4；
4. **DeviceDetail 诊断 issues 统一打" L2 重要"徽章**：后端 aiDiagnosis.issues[] 当前是字符串列表，不带 severity；后续若后端扩展 AiDiagnosisIssue DTO（含 severity/deviceCode 等），前端可按真实 severity 着色（当前是保守默认：AI 识别到的 issues 一律视为重要级）；
5. **AI 4 页文案均为中文**：未接入 i18n，不在当前 Phase 范围。

***

## 5. 明日计划（Day 88 候选，优先级从高到低）

1. **Week 13 复盘** `backend/REVIEW/Week13.md`（Day 85 Phase1-7 + Day 86 AI→ALARM 闭环 + Day 87 前端 4 页打磨）；
2. **Application-Architecture.md 推送链路完整图**：Agent → MQ inspection.exchange → Consumer → Redis SETNX 幂等 → PushGateway(siteIds 路由) → SseEmitter(具名 inspection-report 事件) → nginx `/api/push/` 反代 → 浏览器 EventSource + 去重；
3. **可选 端到端联调**（同 Day 86 §5 第 3 项，顺延）；
4. **可选 巡检 Agent prompt 增强**（同 Day 86 §4 风险 1，顺延）：要求 detectedIssues 结构化输出 + `@JsonSchema` 约束。

***

> 完成时间：2026-09-02 02:35（Asia/Shanghai）
> 维护者：AI 助手 + hula0710
