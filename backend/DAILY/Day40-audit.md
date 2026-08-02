# Day 40 — 架构审计与重构 (2026-08-02)

## 审计范围

基于第 40 天代码整体审查，覆盖 8 个问题（P0×3 + P1×2 + P2×1 + P3×2）。

---

## P0 — 架构设计修复

### 1. AlarmDetector 操作符字符串比较 → Operator 枚举

**Before:**
```java
if ("GT".equalsIgnoreCase(rule.getOperator()) && cmp > 0) { ... }
else if ("LT".equalsIgnoreCase(rule.getOperator()) && cmp < 0) { ... }
```

**After:**
- 新增 `rule/Operator.java` — GT/LT/GTE/LTE/EQ/NEQ 枚举 + `evaluate(int cmp)` 方法
- `AlarmRule.operator`: String → Operator
- `AlarmDetector.check()`: 字符串匹配 → `rule.getOperator().evaluate(cmp)`
- `AlarmRuleConfig`: 字符串 "GT"/"LT" → Operator.GT / Operator.LT
- 新增操作符（GTE/LTE/EQ/NEQ）只需在枚举中添加，不修改 check() 逻辑

### 2+3. 分页方案统一：手动 offset → PageHelper + 返回 Map → PageInfo

**Before:** Alarm/OperationLog 使用手动 `(page-1)*size` + `COUNT(*)`，返回 `Map<String,Object>`

**After:**
- `AlarmMapper`: 12 方法 → 6 方法（删除 6 个手动分页+count 方法）
- `OperationLogMapper`: 8 方法 → 4 方法（删除 4 个手动分页+count 方法）
- `AlarmService`: 返回类型 `Map<String,Object>` → `PageInfo<AlarmVO>`，内部使用 PageHelper
- `OperationLogService`: 返回类型 `Map<String,Object>` → `PageInfo<OperationLog>`
- `AlarmController`: `ApiResponse<Map<String,Object>>` → `ApiResponse<PageInfo<AlarmVO>>`
- `OperationLogController`: `ApiResponse<Map<String,Object>>` → `ApiResponse<PageInfo<OperationLog>>`

**统一后项目分页方案:** Device / User / Alarm / OperationLog 全部使用 PageHelper + PageInfo<T>

---

## P1 — 代码冗余清理

### 4. 删除无调用方方法

移除：
- `AlarmController` `/all` 端点（标注"兼容旧调用"，实际无前端使用）
- `AlarmMapper`: `findAllPaged()` / `findByDeviceIdPaged()` / `findByStatusPaged()` / `count()` / `countByDeviceId()` / `countByStatus()` — 6 个手动分页方法
- `OperationLogMapper`: `findPaged()` / `findByUserIdPaged()` / `findByOpTypePaged()` / `count()` / `countByUserId()` / `countByOpType()` — 6 个手动分页方法
- `OperationLogMapper` + `AlarmMapper` 重新设计为仅保留基础查询（由 PageHelper 拦截分页）

---

## P2 — 前端登录

### 6. 登录页面 + 路由守卫 + 401 拦截

新增：
- `frontend/src/views/Login.vue` — 登录表单（用户名 + 密码 + 错误提示 + 加载状态）
- 路由 `/login` → Login 组件，`meta: { guest: true }`
- `router.beforeEach` 守卫：未登录 → `/login`，已登录访问 `/login` → `/devices`
- `api/index.js` 401 响应：清除 token + 跳转 `#/login`

---

## P3 — 代码风格

### 7. UserService.changePassword 重复 Javadoc → 保留完整版本

### 8. DeviceService 全限定类名 → import 语句

`com.github.pagehelper.PageHelper` → `PageHelper` etc.

---

## 变更统计

| 类别 | 文件数 | 新增 | 删除 |
|------|:---:|:---:|:---:|
| 后端 Java | 12 | Operator.java | — |
| 前端 Vue/JS | 3 | Login.vue | — |
| 合计 | 15 | 2 文件 | 0 文件 |

---

## 编译验证

```
mvn compile: BUILD SUCCESS
```

## 明日计划

- Day 41: 前端用户管理页面
- 延后项 #5: 动态 SQL 注解 `<script>` — XML mapper 迁移（非 bug，风格偏好）
