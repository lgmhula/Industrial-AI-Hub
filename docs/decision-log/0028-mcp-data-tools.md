# Decision 0028: MCP 数据查询与统计工具契约（Week 12）

| 属性 | 值 |
|------|-----|
| **状态** | ✅ 已采纳 |
| **决策日期** | 2026-08-29 |
| **决策者** | hula0710 + AI 助手 |
| **关联** | Day 81 / ADR 0027 / DeviceDataMapper / DeviceMapper |

## 1. 背景

Day 80 已建立 MCP Server（ADR 0027），但只暴露设备基础信息、最近数据与最近告警，
缺少 Day 81 Roadmap 要求的“设备查询、数据查询”能力。外部 AI 客户端需要按时间范围取数、
做聚合统计，以及按关键字/类型/状态搜索设备，才能支撑设备诊断与运维问答类场景。

## 2. 决策

在 `McpDeviceTools` 内新增 3 个只读工具，继续沿用 ADR 0027 的显式注册边界：

| `@Tool` name | 能力 | 参数 | 底层 Mapper |
| --- | --- | --- | --- |
| `mcp_get_device_data_range` | 按时间范围查询运行数据（dataType 可选） | `deviceId, dataType?, startTime?, endTime?, limit?` | `DeviceDataMapper.findByTimeRange` |
| `mcp_get_device_data_stats` | 聚合统计 avg/min/max/count | `deviceId, dataType, startTime?, endTime?` | `DeviceDataMapper.aggregate` |
| `mcp_search_devices` | 关键字/类型/状态搜索设备 | `keyword?, deviceType?, status?, limit?` | `DeviceMapper.searchDevices` |

### 2.1 统一契约

- 全部工具只读、返回 JSON 字符串，错误降级为 `{"error":"..."}`，不中断 MCP 会话。
- `limit` 统一 clamp 到 `1-50`，范围查询与搜索默认 20。
- 时间参数接受两种格式：ISO `2026-08-29T09:00:00` 或空格分隔 `2026-08-29 09:00:00`；
  与 REST 层 `@DateTimeFormat(iso = DATE_TIME)` 风格对齐，未解析成功返回错误 JSON。
- `startTime > endTime` 直接拒绝；`dataType` 等可选文本参数空白视为未传（不拼 `= ''`）。
- 统计结果统一返回 `avg / min / max / count` 键（SQL 的 `cnt` 对外归一化为 `count`）。
- MCP 无用户身份（ADR 0027），搜索不传 `siteIds`，保持全量只读语义；Day 82 客户端
  集成时再补传输层鉴权与 RBAC。

### 2.2 为什么扩展现有工具类而不是新建类

三个新工具与 Day 80 四个工具同属“外部 MCP 只读设备能力”，共享 `clampLimit` /
`requireDevice` / JSON 序列化等辅助逻辑。保持单一 `McpDeviceTools` + 一个显式
`ToolCallbackProvider`，暴露边界更简单，避免每个工具类都要单独注册。

## 3. 备选方案（未采纳）

| 方案 | 未采纳原因 |
|------|-----------|
| 新建 `McpDataTools` 并注册第二个 Provider | 增加边界数量与维护面；无鉴权差异，纯数据工具无需额外隔离 |
| 直接暴露内部 `DeviceDataService` 工具 | 内部服务依赖 userId 做站点授权，MCP 无身份必然失败；与 ADR 0027 冲突 |
| 时间只支持 ISO | 外部客户端常生成空格格式，双格式兜底可减少一次失败重试 |

## 4. 影响与验证

- 代码：`McpDeviceTools` 新增 3 工具；`McpToolConfig` 无需变更（同一 Provider 自动覆盖）。
- 测试：`McpDeviceToolsTest` 8 → 16 用例，覆盖时间解析/默认 limit/范围校验/统计归一化/搜索过滤。
- 回归：全量后端测试 251 tests 0 failures（Day80 基线 243 + 新增 8）。
- 文档：`docs/ai/mcp-learning-notes.md`、AGENTS/DAILY_ROADMAP/Application-Architecture 同步。

## 5. 风险

| 风险 | 缓解 |
|------|------|
| 时间格式错误导致模型重试 | 工具 description 明确两种格式；错误信息包含正确格式示例 |
| 聚合结果类型漂移（BigDecimal/Long） | 直接透传 DB 值并归一化键名，客户端按数值解读 |
| 搜索条件过宽返回数据量大 | `limit` 上限 50 + 字段精简 |
| 无身份通道越权 | 维持 ADR 0027 内网可信边界，Day 82 补传输鉴权 |

---

> 最后更新：2026-08-29 | 维护者：AI 助手 + hula0710
