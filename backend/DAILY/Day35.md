# Day 35 — 2026-07-29

## 今日目标
- [x] 业务流程全链路 Review（登录→设备→数据→报警→日志）
- [x] 代码 Review：命名 / 异常处理 / 日志打印 / 一致性
- [x] REVIEW/Week05.md 周复盘
- [x] Git tag: v1.0-alpha
- [ ] Git commit

## 代码 Review 结果

| 检查项 | 结果 |
|------|:---:|
| @Autowired 字段注入 | ✅ 0 |
| System.out.println | ✅ 0 |
| printStackTrace | ✅ 0 |
| TODO/FIXME 残留 | ✅ 0 |
| 日志覆盖 | ✅ 9 files |
| 构造器注入 | ✅ 100% |

### 发现 4 个小问题（P2-P3，不影响 v1.0-alpha）

1. OperationLog 实体直接返回（缺 VO）
2. 前端无登录页（需手动 curl 拿 token）
3. ECharts bundle 674KB（可分包优化）
4. 报警规则硬编码（可迁移至 DB）

## 里程碑

```
git tag -a v1.0-alpha -m "Industrial AI Hub v1.0-alpha"
```

**Phase 2 完成**。核心功能闭环：

```
认证(JWT+BCrypt) → RBAC(3级) → 设备CRUD → 数据上报
  → 报警规则引擎(8条) → AOP操作日志 → Vue3前端(4页面)
```

## 项目规模

| 维度 | v1.0-alpha |
|------|:---:|
| 后端 Java | 53 files |
| API 端点 | 26 |
| 数据库表 | 7 |
| 前端页面 | 4 |
| Postman | 46+ cases |
| Docker 服务 | 12 |

---

## 明日计划
- Day 36: 单元测试 — UserService + DeviceService
