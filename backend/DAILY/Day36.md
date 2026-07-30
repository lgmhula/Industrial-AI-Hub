# Day 36 — 2026-07-30

## 今日目标
- [x] UserService 单元测试（JUnit 5 + Mockito）
- [x] DeviceService 单元测试（JUnit 5 + Mockito）
- [x] mvn test 全绿
- [ ] Git commit

## 测试产出

### UserServiceTest — 19 cases

| 被测方法 | 用例数 | 覆盖场景 |
|---------|:---:|------|
| listPage | 2 | 正常分页、空列表 |
| getById | 2 | 命中返回 VO、不存在抛 404 |
| getByUsername | 2 | 命中返回实体、不存在返回 null |
| update | 2 | 正常更新、不存在抛 404 |
| toggleStatus | 4 | 启用→禁用、禁用→启用、null→启用、不存在抛异常 |
| delete | 2 | 成功软删+清理角色、不存在返回 false |
| changePassword | 5 | 成功、旧密码错误(401)、新密码太短(400)、用户不存在(404)、旧密码 null(401) |

### DeviceServiceTest — 16 cases

| 被测方法 | 用例数 | 覆盖场景 |
|---------|:---:|------|
| listAll | 2 | 正常返回、空列表 |
| getById | 2 | 命中返回 VO、不存在抛 404 |
| create | 3 | 正常创建、status=null 默认 1、编码重复抛 409 |
| update | 3 | 正常更新、status=null 保留原值、不存在抛异常 |
| delete | 2 | 成功软删、不存在抛异常 |
| listByType | 2 | 命中过滤、空结果 |
| searchDevices | 2 | 关键字命中分页、空结果分页 |

**总计: 35 tests, 0 failures, BUILD SUCCESS**

## 测试策略
- 纯单元测试 — 不启动 Spring 容器，Mock 所有 Mapper
- Security 层 (JwtAuthFilter / AuthInterceptor) 暂未单元测试（已在 Postman 集成测试覆盖）
- 后续可追加 `@SpringBootTest` 集成测试（Day 37+ 考虑）

## 项目当前状态

| 维度 | 数值 |
|------|:---:|
| 后端 Java | 55 files (+2 tests) |
| 测试用例 | 35 (新增) |
| API 端点 | 26 |
| 数据库表 | 7 |
| 前端页面 | 4 |
| Postman | 46+ cases |

---

## 明日计划
- Day 37: DAILY_ROADMAP 待定（等待路线图确认）
