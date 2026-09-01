# Day 81 — Device MCP Server：设备查询 + 数据查询工具

> **日期**：2026-08-29
> **阶段**：Phase 4 AI 集成 · Week 12（Agent + MCP）
> **分支**：`feat/agent-mcp`
> **配套 ADR**：[0028-mcp-data-tools.md](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/docs/decision-log/0028-mcp-data-tools.md)
> **验收结果**：✅ **GO**（MCP 数据查询/聚合/搜索工具落地，后端 251 tests 0 failures）

---

## 一、今日产出

| 模块 | 文件 | 说明 |
| --- | --- | --- |
| 工具 | `mcp/McpDeviceTools.java` | 新增 3 个只读工具：时间范围数据查询 / 聚合统计 / 设备搜索（Day 80 的 4 个保留，共 7 个） |
| 测试 | `mcp/McpDeviceToolsTest.java` | 8 → 16 用例：时间解析/默认 limit/范围校验/统计归一化/搜索过滤 |
| ADR | `docs/decision-log/0028-mcp-data-tools.md` | 数据查询工具契约（格式、limit、统计键名） |
| 文档 | `docs/ai/mcp-learning-notes.md` | 追加 Day 81 数据工具清单与契约 |
| 文档 | AGENTS / ROADMAP / Architecture / README | 阶段状态与 ADR 编号同步 |
| 治理 | Day80.md / ADR 0027 | 修正回归测试数 241 → 243（Day80 实际基线） |

## 二、数据查询工具契约（ADR 0028）

`McpDeviceTools` 现有 7 个只读工具。Day 81 新增 3 个：

| `@Tool` name | 能力 | 参数 |
| --- | --- | --- |
| `mcp_get_device_data_range` | 按时间范围查询运行数据（dataType 可选） | `deviceId, dataType?, startTime?, endTime?, limit?` |
| `mcp_get_device_data_stats` | 聚合统计 avg/min/max/count | `deviceId, dataType, startTime?, endTime?` |
| `mcp_search_devices` | 关键字/类型/状态搜索设备 | `keyword?, deviceType?, status?, limit?` |

统一契约：

- `limit` clamp 到 `1-50`，范围查询与搜索默认 20。
- 时间接受 ISO `2026-08-29T09:00:00` 或 `yyyy-MM-dd HH:mm:ss` 两种格式，与 REST 层风格对齐；
  `startTime > endTime` 拒绝。
- 可选文本参数空白视为未传；统计结果 SQL `cnt` 对外归一化为 `count`。
- MCP 无用户身份，搜索不传 `siteIds`（全量只读），Day 82 客户端集成时补传输鉴权与 RBAC。

示例调用：

```text
mcp_get_device_data_stats(deviceId=1, dataType=TEMPERATURE,
  startTime=2026-08-29T08:00:00, endTime=2026-08-29T10:00:00)
→ {"deviceId":1,"dataType":"TEMPERATURE","avg":82.5,"min":80.0,"max":85.0,"count":3}
```

## 三、测试与回归

```
McpDeviceToolsTest     16/16
  ├ Day80 既有 8 用例（list/basic/recent data/recent alarms）
  └ Day81 新增 8 用例
      ├ getDeviceDataRange_shouldReturnFilteredDataJson
      ├ getDeviceDataRange_shouldAcceptSpaceTimeFormatAndDefaultLimit
      ├ getDeviceDataRange_invalidTime_shouldReturnErrorJson
      ├ getDeviceDataRange_reversedRange_shouldReturnErrorJson
      ├ getDeviceDataStats_shouldReturnNormalizedStatsJson
      ├ getDeviceDataStats_missingDataType_shouldReturnErrorJson
      ├ searchDevices_shouldReturnSearchJsonAndApplyLimit
      └ searchDevices_blankFilters_shouldPassNullToMapper
McpServerContextTest    2/2
```

全量后端回归：`Tests run: 251, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS。

> 测试数说明：相较 Day80 基线 243 tests，本日新增 `McpDeviceToolsTest` 8 用例，净 +8。
> 治理修正：Day80.md 头部与 ADR 0027 的回归数由 241 修正为 243（以 Day80 全量运行为准）。

## 四、明日计划（Day 82）

| 优先级 | 内容 |
| :-: | --- |
| ★★★ | MCP 客户端集成：SSE 连接验证 + 工具清单冒烟（含传输层鉴权/RBAC 设计） |
| ★★☆ | MCP 学习笔记补客户端接入章节；前端/Agent 侧可选接入入口 |
| ★☆☆ | 同步 AGENTS/ROADMAP 与 Day82 日志 |
