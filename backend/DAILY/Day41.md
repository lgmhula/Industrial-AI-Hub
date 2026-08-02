# Day 41 — 2026-08-02

## 今日目标
- [x] 修复 GlobalExceptionHandler — 新增 ConstraintViolationException 处理器
- [x] 统一分页参数校验 — 5 个 Controller 全部加 @Validated + @Min(1)/@Max(100)
- [x] 动态 SQL 迁移 — DeviceMapper + DeviceDataMapper XML 化
- [x] 系统架构图 — System-Architecture.md (Mermaid)
- [x] 数据库 ER 图 — Database-ER.md (Mermaid)
- [x] API 接口清单 — API-Reference.md (26 endpoints)
- [x] 全量测试回归 — 75 tests, 0 failures
- [ ] Git commit & push

---

## Bug 修复详情

### 1. GlobalExceptionHandler
- **问题**：`@Validated` + `@Min/@Max` 抛出 `ConstraintViolationException` 未处理 → 500
- **修复**：新增 `handleConstraintViolation()` → 400 + `e.getMessage()`
- **异常类型覆盖**：Business → MethodArgumentNotValid → ConstraintViolation → Exception(兜底)

### 2. 分页参数校验（5/5 Controller）
| Controller | @Validated | page @Min(1) | size @Min(1) @Max(100) |
|-----------|:---:|:---:|:---:|
| AlarmController | ✅ | ✅ | ✅ |
| OperationLogController | ✅ | ✅ | ✅ (fixed) |
| DeviceController | ✅ (fixed) | ✅ (fixed) | ✅ (fixed) |
| UserController | ✅ (fixed) | ✅ (fixed) | ✅ (fixed) |
| AuthController | N/A | — | — |

### 3. 动态 SQL XML 迁移
| Mapper | 迁移前 | 迁移后 |
|--------|------|------|
| `DeviceMapper` | `searchDevices` + `findByType` `@Select(<script>)` | → `mapper/DeviceMapper.xml` |
| `DeviceDataMapper` | `findByTimeRange` + `aggregate` `@Select(<script>)` | → `mapper/DeviceDataMapper.xml` |

- XML 无转义符（`&gt;` → `>`）
- IDE SQL 语法高亮
- 后续扩展更易维护

---

## 文档产出

| 文档 | 路径 | 内容 |
|------|------|------|
| 系统架构图 | `docs/Architecture/System-Architecture.md` | Mermaid 架构图 + 请求链路 + 技术栈分层表 |
| 数据库 ER 图 | `docs/Architecture/Database-ER.md` | Mermaid ER 图 + 27 索引清单 + 表规模统计 |
| API 接口清单 | `docs/Architecture/API-Reference.md` | 26 端点 × 请求/响应示例 + 报警规则表 + 响应码表 |

---

## 项目规模

| 维度 | Day 40 | Day 41 |
|------|:---:|:---:|
| 后端 Java | 64 | 64 |
| 测试用例 | 75 | 75 (回归) |
| XML Mapper | 0 | 2 (新增) |
| 架构文档 | 2 | 5 (+3) |
| Bug 修复 | — | 3 个 (P0×1, M2×2) |

---

## 明日计划
- Day 42: 第二阶段复盘 — 完整演示 + REVIEW/Phase2-Summary.md + V1 技术债务清单
