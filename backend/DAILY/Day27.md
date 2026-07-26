# Day 27 — 2026-07-26

## 今日目标
- [x] AGENTS.md 同步：Day 26→27, 已完成模块更新
- [x] A1: Service 层接入 BusinessException（Auth/Device/User）
- [x] A2: DeviceService 删除未使用的 ApiResponse import
- [x] Controller 层删除手工 null-check/ApiResponse.error
- [x] 联调测试：异常链路 + 正常流程全验证
- [x] mvn clean compile 通过（97 files）
- [x] Git commit

## Architecture Consistency Report V1.2 修复

| # | 严重度 | 问题 | 修复 |
|---|:---:|------|------|
| A1 | P1 | Service 层未接入 BusinessException | AuthService/DeviceService/UserService 全部抛出 BusinessException |
| A2 | P1 | DeviceService 未使用的 ApiResponse import | 已删除 |

## 重构变更

### Service 层（3 files）
| 方法 | 旧行为 | 新行为 |
|------|--------|--------|
| AuthService.login() — 用户不存在 | return null | throw BusinessException(401) |
| AuthService.login() — 密码错误 | return null | throw BusinessException(401) |
| AuthService.login() — 账户禁用 | return null | throw BusinessException(403) |
| AuthService.register() — 重复 | return null | throw BusinessException(409) |
| DeviceService.getById() — 不存在 | return null | throw BusinessException(404) |
| DeviceService.create() — 重复编码 | return null | throw BusinessException(409) |
| DeviceService.update() — 不存在 | return null | throw BusinessException(404) |
| UserService.getById() — 不存在 | return null | throw BusinessException(404) |
| UserService.update() — 不存在 | return null | throw BusinessException(404) |
| UserService.toggleStatus() — 不存在 | return null | throw BusinessException(404) |

### Controller 层（3 files）
- AuthController: 删除 `if (token == null)` / `if (vo == null)` 手工 error
- DeviceController: 删除 getById/create/update/delete 中的 null-check
- UserController: 删除 getById/update/toggleStatus 中的 null-check

## 验证结果

| # | 测试 | 结果 |
|---|------|:---:|
| 1 | 错误密码 → 401 "用户名或密码错误" | ✅ |
| 2 | 重复注册 → 409 "用户名已存在" | ✅ |
| 3 | 设备不存在 → 404 "设备不存在" | ✅ |
| 4 | 更新不存在的设备 → 404 | ✅ |
| 5 | 用户不存在 → 404 | ✅ |
| 6 | Login 正常 | ✅ 200 |
| 7 | Device list 正常 | ✅ 200 |
| 8 | User list 正常 | ✅ 200 |

> "WRONG"（5 字符）被 @Size(min=6) 先拦截返回 400 — 此为 @Valid 预期行为，非 bug。

## 审计未修复项（后续处理）

| # | 严重度 | 说明 |
|---|:---:|------|
| A3 | P1 | AlarmMapper.insert() 死代码 |
| A4-A6 | P2 | 无 Alarm/DeviceData/OperationLog Controller |
| A7 | P2 | LoginDTO 复用登录和注册语义不清 |
| A8-A9 | P3 | UserMapper.updatePassword / DeviceMapper.findByType 死代码 |

---

## 明日计划
- Day 28（周日）: 第 4 周复盘 — 回顾本周产出 + 代码重构 + 准备进入第 5 周
