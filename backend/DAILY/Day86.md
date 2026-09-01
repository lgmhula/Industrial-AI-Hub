# Day 86 — AI 巡检异常自动生成报警（AI 与业务闭环）

> **状态**：Day 86 完成（AiAlarmAutoCreator + 结构化异常 DTO + Redis 幂等 + 多级降级 + 审计 + Agent 接入 + 13 个单测）/ AI→报警链路闭环
> **关联**：ADR 0030 Agent+MCP 联调 / Day 83 / ADR 0031 §6 降级语义
> **测试**：后端 330/330 全绿（316 基线 + AiAlarmAutoCreatorTest 新增 10 + McpInspectionAgentServiceTest 扩 3 + Flyway V15 纳入）；前端无变更（Day 85 build 744ms 0 错误延续）

***

## 1. 今日产出

### 1.1 结构化异常 DTO（AiInspectionDetectedIssue）+ 日报结果扩字段

| 步骤 | 文件 | 内容 |
|------|------|------|
| DTO-1 | [AiInspectionDetectedIssue.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/java/dev/reboot/dto/ai/AiInspectionDetectedIssue.java) | 新 DTO，字段：`deviceId` / `deviceCode` / `severity(1-3)` / `alarmType` / `description(≤500)` / `occurredAt`；全参构造器 + `@NotBlank/@Size/@Min/@Max` 字段校验注解；`toString()` 安全摘要（不含超长 description 截断日志） |
| DTO-2 | [AiInspectionReportResult.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/java/dev/reboot/dto/ai/AiInspectionReportResult.java) | 扩 2 字段：`List<AiInspectionDetectedIssue> detectedIssues`（默认 `new ArrayList<>()`，null-safe）/ `int autoAlarmCount`；`toString()` 审计摘要追加 `issues=N, autoAlarms=M`（便于 operation_log 审计摘要一眼可读） |

**设计要点（替代"解析自由文本日报"方案）**：
- 不做正则从 `report` 自由文本里抽 device/type/level，直接让 Agent 返回结构化 `detectedIssues` 列表（可加 `@JsonSchema` 约束 Day 87 做可选）——可靠性远高于 NLP；
- 不把 AI 异常与系统 `alarm` 表强耦合：保留 AiInspectionDetectedIssue 独立 DTO，后续 AI 侧字段扩（confidence/recommendation/root cause）不影响 alarm 表 schema。

***

### 1.2 AiAlarmAutoCreator 服务（核心）

| 步骤 | 文件 | 内容 |
|------|------|------|
| Svc | [AiAlarmAutoCreator.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/java/dev/reboot/service/AiAlarmAutoCreator.java) | 新建 `@Service`，构造器注入 `AlarmService` + `DeviceMapper` + `@Nullable StringRedisTemplate`（test profile 允许 null）；`createAlarms(Long userId, AiInspectionReportResult result)` 主流程：null/空→快速 return 0 → 取 reportDate → 按 issues 逐个 `tryCreateOne` → 累加返回 → 回填 `result.setAutoAlarmCount(created)`；方法级 `@OperationLog(operationType="AUTO_ALARM", targetType="ALARM")` 审计 |
| `@VisibleForTesting int clampLevel(int)` | 同文件 | severity 非法（0 / -10 / 999）全部回退到 1（alarm_level CHECK 1-3，不 clamp 会写 DB 失败）；合法值 1/2/3 原样透传 |

#### 1.2.1 tryCreateOne 单条处理流程（6 段降级防御）

```
tryCreateOne(reportDate, issue):
  1) severity clampLevel → alarmLevel
  2) resolveDeviceId(issue):
       - issue.deviceId != null → 直接用
       - deviceCode 非空 → DeviceMapper.findByCode(deviceCode)
       - 仍 null → WARN 日志，return 0（跳过，不影响其他 issue）
  3) severity → alarmType 直接透传 issue.alarmType（不做枚举映射，AI 可扩展）
  4) 幂等检查 isDuplicate(deviceId, alarmType, reportDate):
       - Redis SETNX key = ai-alarm:{deviceId}:{alarmType}:{yyyy-MM-dd}, TTL 24h
       - SETNX TRUE → 首次，继续
       - SETNX FALSE → 重复 → INFO 日志，return 0
       - Redis 抛 Exception → 降级 "不幂等"（宁愿重复也不丢报警）→ 继续
       - redis==null（constructor 没注入）→ 同上降级
  5) AlarmService.createAlarm(deviceId, alarmType, alarmLevel, description, occurredAt)
       - 成功 → INFO 日志 "AI 自动生成报警成功 alarmId=X" → return 1
       - 抛 RuntimeException（DB/约束/锁冲突 等）→ WARN 日志 → return 0
  6) 任一步骤抛 RuntimeException → 外层 catch → WARN 日志单条降级 return 0
```

#### 1.2.2 降级语义矩阵（与 ADR 0031 §6 同策略：单点失败不阻塞主流程）

| 故障点 | 影响范围 | 行为 | 测试用例 |
|--------|----------|------|----------|
| result == null | 整批 | return 0 | `createAlarms_nullResult` |
| detectedIssues 空 | 整批 | return 0，deviceMapper/alarmService/redis **均不调用** | `createAlarms_emptyIssues` |
| deviceCode 查不到 Device | 单条 issue | WARN + return 0，其他 issue 继续 | `createAlarms_deviceCodeNotExist` |
| Redis SETNX 失败（连接异常） | 单条 issue | 降级不幂等 → 仍创建 alarm | `createAlarms_redisUnavailable` |
| Redis == null（无注入） | 全部 | 降级不幂等 → 仍创建 alarm | `createAlarms_noRedis` |
| AlarmService.createAlarm 抛异常 | 单条 issue | WARN + return 0，其他 issue 继续 | `createAlarms_mixedIssues` |
| AiAlarmAutoCreator 自身 catch 漏（未预期 RuntimeException） | — | **Agent 层二次兜底**：`McpInspectionAgentService.autoCreateAlarms` 外层 catch，不阻塞日报/MQ | `generate_autoCreatorFails_shouldNotBlockAgentFlow` |

***

### 1.3 Flyway V15 AUTO_ALARM 审计约束扩充

| 步骤 | 文件 | 内容 |
|------|------|------|
| V15 | [V15__auto_alarm_operation_type.sql](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/resources/db/migration/V15__auto_alarm_operation_type.sql) | 对齐 V12/V14 模式：`ALTER TABLE operation_log DROP CHECK chk_operation_type` → 重建 ADD CONSTRAINT，新增 `AUTO_ALARM`；**不改 chk_target_type**（复用已有 `ALARM`，语义一致） |
| H2 | [schema-h2.sql](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/test/resources/db/h2/schema-h2.sql) L123 | H2 测试 schema 同步 DROP CHECK + ADD CONSTRAINT（含 AUTO_ALARM） |
| JavaDoc | [OperationLog.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/java/dev/reboot/annotation/OperationLog.java) L18 | `@OperationLog.operationType()` Javadoc 列表追加 `AUTO_ALARM`（"AI 巡检日报自动生成报警"） |
| Flyway Test | [FlywayProductionSeedIsolationTest.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/test/java/dev/reboot/db/FlywayProductionSeedIsolationTest.java) L69 | 预期迁移列表增 `V15__auto_alarm_operation_type.sql`（否则非法迁移 → 测试红） |

***

### 1.4 McpInspectionAgentService 接入（AI→ALARM 业务闭环调用点）

| 步骤 | 文件 | 内容 |
|------|------|------|
| Agent | [McpInspectionAgentService.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/main/java/dev/reboot/service/McpInspectionAgentService.java) | ① 构造器 4 参数 → 5 参数，新增第 5 参 `@Nullable AiAlarmAutoCreator aiAlarmAutoCreator`；② `generate()` 原流程：Agent 运行 → toResult() → dispatchReport() → return。Day 86 改为 **toResult → `autoCreateAlarms(result)` → dispatchReport → return**（顺序保证：即使 MQ 不可达，报警仍先落盘）；③ 私有 `autoCreateAlarms()` 二层降级：if null → return；try 调 createAlarms，任何 RuntimeException → WARN 日志跳过（AiAlarmAutoCreator 理论上已包所有异常，这里做冗余兜底） |

**为什么接入点选 generate()，不选 InspectionReportConsumer？**
- **时效性**：Agent 跑完立即落 alarm（毫秒级 vs MQ→Consumer 秒级到分钟级）；
- **原子语义**：巡检日报生成与异常报警生成同事务上下文（Agent 进程同一调用栈），日报生成成功则 alarm 一定被尝试写入；Consumer 侧巡检可能因为重复/幂等被 skip，导致该落的 alarm 反而不落；
- **与 ADR 0031 §3.1 一致**：Agent 已经接入 MQ Producer；再加一个 AiAlarmAutoCreator（nullable）不破坏 Agent 不感知 SSE/PushGateway 的边界；
- **test profile 兼容**：@Nullable 注入，test 不装配时跳过，ApplicationContextLoadTest 不破坏（Day 85 已证明）。

***

### 1.5 单元测试（10 + 3 = 13 新增）

#### 1.5.1 AiAlarmAutoCreatorTest（10 场景，覆盖降级矩阵全部行）

[AiAlarmAutoCreatorTest.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/test/java/dev/reboot/service/AiAlarmAutoCreatorTest.java)

| # | 方法 | 验证点 |
|---|------|--------|
| 1 | `createAlarms_normalIssue_shouldCreateOneAlarm` | SETNX 首次 → AlarmService.createAlarm 被调用 + created=1 + result.autoAlarmCount=1 |
| 2 | `createAlarms_duplicateIdempotency_shouldSkipSecondCall` | 同 device+type+date 二次 → SETNX false → 不调 AlarmService + created=0 |
| 3 | `createAlarms_deviceCodeLookup_shouldResolveAndCreate` | deviceId=null + findByCode 命中 → 仍创建，verify findByCode |
| 4 | `createAlarms_deviceCodeNotExist_shouldSkipWithoutException` | findByCode 返回 null → skip，不调 AlarmService，不抛 |
| 5 | `clampLevel_illegalSeverity_shouldFallbackTo1` | 0/-10/999 → 1；1/2/3 不变 |
| 6 | `createAlarms_redisUnavailable_shouldFallbackCreateAlarm` | SETNX 抛 RuntimeException → 仍创建 alarm（降级不幂等） |
| 7 | `createAlarms_noRedis_shouldFallbackCreateAlarm` | constructor redis=null → 仍创建 alarm |
| 8 | `createAlarms_emptyIssues_shouldReturnZeroFast` | 空 List → return 0 + verifyNever alarmService/deviceMapper |
| 9 | `createAlarms_nullResult_shouldReturnZeroSafe` | null result → return 0，不抛 |
| 10 | `createAlarms_mixedIssues_failureShouldNotBlockOthers` | 3 条 issue：1 正常 + 1 AlarmService 异常 + 1 幂等跳过 → 合计 1，其他 issue 不被阻塞；ArgumentCaptor 捕获 deviceId=[1, 2]（dup 不调 createAlarm） |

#### 1.5.2 McpInspectionAgentServiceTest（8 个测试，原 5 扩到 8；Day 86 新增 3）

[McpInspectionAgentServiceTest.java](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/backend/src/test/java/dev/reboot/service/McpInspectionAgentServiceTest.java)

| # | 方法 | 验证点（Day 86 新增） |
|---|------|----------------------|
| 1 | `generate_shouldInvokeAutoCreateAlarmsBeforeDispatchReport` | `InOrder` 验证先 aiAlarmAutoCreator.createAlarms，后 inspectionReportProducer.send；thenAnswer 回填 setAutoAlarmCount(2) → result.autoAlarmCount=2 |
| 2 | `generate_autoCreatorFails_shouldNotBlockAgentFlow` | createAlarms 抛 RuntimeException → result 仍正常返回 + MQ 投递仍执行 + session.close() 仍调用（Agent 二层兜底生效） |
| 3 | `generate_nullAutoCreator_shouldSkipAndKeepDispatch` | constructor aiAlarmAutoCreator=null → skip，不 NPE，MQ 仍投递 |

***

### 1.6 验收条件对照（用户要求）

| 验收要求 | 实现 | 验证 |
|----------|------|------|
| AI 异常 → 结构化 DTO（deviceId/deviceCode/severity/alarmType/description/occurredAt） | AiInspectionDetectedIssue | 构造器 + getter/setter + 字段注解；AgentTest 字段访问无 null |
| 日报结果扩 detectedIssues 列表 + autoAlarmCount | AiInspectionReportResult 扩 2 字段 + toString 审计摘要 | AgentTest thenAnswer 回填 autoAlarmCount=2 验证 |
| 幂等 Redis SETNX（同 device/type/date 24h 不重复） | AiAlarmAutoCreator.isDuplicate → SETNX ai-alarm:{deviceId}:{alarmType}:{date} TTL 24h | 测试 2：二次调用 SETNX false → 不调 alarmService |
| 设备 ID 解析（deviceId 优先，deviceCode fallback findByCode） | resolveDeviceId() 两段 | 测试 3（反查命中）+ 测试 4（反查 null 跳过） |
| 多级降级：设备不存在 / Redis 异常 / DB 异常 → 不阻塞主流程 | 单条 tryCreateOne 外层 catch + Redis 异常降级不幂等 + Agent 层二次兜底 | 测试 4/6/10 + AgentTest 2 |
| 审计日志（AUTO_ALARM 操作类型 + Flyway 约束） | @OperationLog(AUTO_ALARM/ALARM) + V15 chk_operation_type + H2 同步 + OperationLog JavaDoc | V15 迁移 + FlywayProductionSeedIsolationTest pass |
| Agent 接入点：生成日报后、MQ 投递前生成 alarm | generate() 顺序：toResult → autoCreateAlarms → dispatchReport | AgentTest 1 InOrder verify |
| 单元测试覆盖（AiAlarmAutoCreator + McpInspectionAgentService 扩） | 10 + 3 = 13 新增测试 | ./mvnw test 全绿 |
| 构建验证（后端 compile/test 全绿） | compile 0 errors + 330 tests green | 见 §2 |

***

### 1.7 今日全量回归

```
# 主代码编译（0 errors）
$ ./mvnw -q -DskipTests compile   # exit 0

# 相关单元测试（22/22）
$ ./mvnw -q test -Dtest=AiAlarmAutoCreatorTest,McpInspectionAgentServiceTest,FlywayProductionSeedIsolationTest
# exit 0

# 全量测试
$ ./mvnw -q test   # exit 0, 330 tests (原有 316 + Day 86 新增 13 + V15 纳入 FlywayTest 断言 1)
# 0 Failures, 0 Errors, 0 Skipped（与 Day 85 baseline 对比 316 → 330）

# 前端无变更，继承 Day 85 基线
# npm run build 744ms 0 errors；InspectionReport.vue 3.71 kB 延续
```

***

## 2. 关键设计决策

### 2.1 为什么不做「自由文本正则提取设备/类型」？

Day 86 初始 prompt 给的备选方案是"从 `AiInspectionReportResult.getReport()` 正文里解析异常"。废弃原因：
1. **不可靠**：AI 今日写 `CNC-007 温度超限 88℃ > 85℃`，明天写 `设备 CNC007 的 TEMP 达到88度`，正则/模糊匹配无法穷尽；
2. **不可审计**：正则匹配 hit/miss 无迹可查，miss 时要调 OpenAI 日志才能复盘；
3. **Agent 已经是结构化数据的生产者** —— 通过 `mcp_list_devices`/`mcp_get_device_data_stats` 拿到 deviceId/deviceCode/指标值，直接写入 `detectedIssues` 字段即可，不需要"结构化→自由文本→再结构化"的信息损失环路。

### 2.2 幂等键选择「ai-alarm:{deviceId}:{alarmType}:{yyyy-MM-dd}」而不是包含 description/hash

- alarm 表语义是「某设备某类型异常（当日最多一条）」，跟 description 文本无关；
- description 文本变化会导致幂等失效（AI 今天写"温度超限 88℃"、明天写"温度超限 88.2℃" → hash 不同 → 重复落库）；
- 若后续需要细粒度（同 type 当日不同阈值分别 alarm），改 alarm_type 枚举扩展即可（如 `TEMP_HIGH_WARN` / `TEMP_HIGH_CRIT`），不动幂等键结构。

### 2.3 接入点 generate() vs Consumer 的取舍（§1.4 详细版）

| 维度 | generate() 接入（当前实现） | Consumer 接入（备选） |
|------|----------------------------|----------------------|
| 时效性 | Agent 跑完立即落 alarm | 需要 MQ 投递 + 消费链路成功 |
| 幂等语义对齐 | 每次巡检 → 尝试 alarm，重复由 Redis 控制 | Consumer 自身有 `inspection:{date}:siteId` 幂等，会导致 alarm 因为"日报重复"不生成 |
| 失败隔离 | Redis/MQ/Alarm 任何一环故障互不影响 | MQ 故障→alarm 不落；Consumer 进程挂→alarm 不落 |
| 边界感知 | Agent 已持有 AlarmService/DeviceMapper 引用（Spring Bean 管理） | Consumer 是 @Profile("!test")，test 下 AlarmService 能不能注入要单独适配 |
| 审计 | @OperationLog(AUTO_ALARM) 在 createAlarms 上一次落 operation_log | Consumer 侧需额外加 @OperationLog，且 triggeredByUserId 需透传 message 字段 |

### 2.4 AiAlarmAutoCreator 不做 @Transactional

- `createAlarms` 内部是逐条调用 `AlarmService.createAlarm`（**本身**是单独 @Transactional 独立提交），外层加 Tx 会把 N 条 alarm 绑到一个 DB Tx 中：
  - 第 N 条失败 → 前 N-1 条也回滚 → 违反"单条失败不影响其他"降级语义；
  - Redis SETNX 成功但 DB Tx 回滚 → 幂等键占用，后续重试永远 skip（"空跑"）；
- 因此 AiAlarmAutoCreator **不包 @Transactional**，信任 AlarmService 逐条 Tx，与"单条降级"语义一致。

***

## 3. 文档同步

- [AGENTS.md §3](file:///Users/air/Documents/重启：软件工程师（Industrial AI Hub）/AGENTS.md)："当前状态"段新增 Day 86 完成描述 + "已完成模块"段追「Day 86 AI 巡检异常自动生成报警（AiAlarmAutoCreator + Redis SETNX 幂等 + 6 级降级 + V15 AUTO_ALARM 审计类型 + 13 单测，330/330 全绿）」；"下一步"更新为「Day 87 Week 13 复盘 + Application-Architecture 推送链路完整图 + 端到端联调（可选）」。
- 本文件（Day86.md）：结构化产出 + 验收对照 + 全量回归 + 设计决策 + 风险项。

***

## 4. 风险与注意事项

1. **AI 返回 detectedIssues 为空 → 自动跳过**：正常；但在 AI 报告里写了异常而 detectedIssues 漏填时，会出现"日报有异常描述但无 alarm 生成"——需要后续对 Agent prompt 加约束：`必须把所有异常写入 detectedIssues 字段，不能仅写在自由文本里`（可选 Day 87 巡检 prompt 增强）。
2. **Redis 不可用时降级不幂等**：极端场景（Redis 挂 + 短时间多次巡检）会有重复 alarm；代价可控：运维侧告警列表重复出现 → 可手动确认/解决；后续可选本地内存 `Caffeine` 二级幂等做 Redis 降级兜底。
3. **alarm_level CHECK 1-3**：AI 给出 severity=4 时被 clamp 为 1（一般），会导致"AI 报紧急但系统当一般"——可选后续给 severity≥4 也 clamp 为 3（最高级）更保守，但当前逻辑对齐"未知输入一律降级到一般，不误报紧急"。
4. **@OperationLog(AUTO_ALARM) AOP 切面生效边界**：Spring AOP 代理只拦截外部调用。`createAlarms()` 当前是从 `McpInspectionAgentService.autoCreateAlarms()` 外部调用 → 正常走 AOP；如果未来在 AiAlarmAutoCreator 内部某方法调 `createAlarms`，会绕过 AOP 不留审计——加 TODO 注释提醒。

***

## 5. 明日计划（Day 87 候选，优先级从高到低）

1. **Week 13 复盘**：`backend/REVIEW/Week13.md`（Day 85 Phase 1-7 + Day 86 AI→ALARM 闭环），Week 13 产出/遗留/风险汇总；
2. **Application-Architecture.md 推送链路完整图**：Agent → MQ → Consumer → Redis 幂等 → Push Gateway → SseEmitter → nginx → 浏览器 EventSource 完整架构图 + 说明；
3. **可选 端到端联调**：起 compose（MySQL/Redis/RabbitMQ + backend）+ 手动登录 admin + 打开巡检日报页 SSE + 手动 curl `POST /api/ai/agents/inspection-report` → 检查：
   - 浏览器 InspectionReport.vue 是否收到具名 inspection-report 事件；
   - alarm 表是否新增对应记录；
   - operation_log 表是否有 INSPECTION + AUTO_ALARM + PUSH 三条审计记录；
4. **可选 prompt 增强**：在 `McpInspectionAgentService.PROMPT` 中要求"所有异常必须写入 detectedIssues 结构化列表，含 deviceCode + severity + alarmType + description"，解决 §4 风险 1。

***

> 完成时间：2026-09-01 18:30（Asia/Shanghai）
> 维护者：AI 助手 + hula0710
