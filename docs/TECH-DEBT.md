# 技术债务清单（Technical Debt）

> **SSOT**：本文档是技术债务的唯一权威清单（迁移自 `backend/REVIEW/Phase2-Summary.md` §四，并持续更新）。
> **最后更新**：2026-08-28（Day 67：AI 操作日志治理）

---

## 一、已解决（可追溯）

| # | 问题 | 解决于 |
|:--:|------|------|
| 1 | JwtUtils static 工具类无法配置 | Phase 3-A T4（static→instance + profile-aware） |
| 2 | `listAll()` 返回实体含密码字段 | Phase 2 后（改返回 VO；现仅 `DeviceService.listAll()` 返回 `DeviceVO`） |
| 3 | 注册无频率限制 | RateLimitInterceptor（URI 级限流，覆盖 `/api/**`） |
| 11 | compose.yml 无 profiles 分组 | Phase 3-A T6（`full` profile 分级启动） |
| 12 | 无应用层 Dockerfile | Phase 3（multi-stage + non-root + HEALTHCHECK） |
| 16 | 种子数据两份重复（02_test_data.sql vs seed_test_data.sql） | 2026-08-16 合并为唯一 SSOT `seed_test_data.sql` |
| 17 | `mysql/init/01_init.sql` 符号链接（Windows 不兼容） | 2026-08-16 改为 compose 直接挂载 `sql/*.sql` |
| 18 | Nacos/MinIO/ES 孤儿能力（无路线落点） | 2026-08-16 决策：Nacos=预留不启动、MinIO=Phase 5、ES=Day 101 ELK |
| 21 | README/AGENTS 状态滞后（~3 周） | 2026-08-16 漂移纠正 |
| 22 | Phase 3 决策无 ADR | 2026-08-16 新增 ADR 0014 |
| 23 | DAILY_ROADMAP Phase 2 复选框未勾 | 2026-08-16 勾选 |
| 28 | AI 端点无操作日志（TD-028） | 2026-08-28（Day 67：Flyway V9 扩展 `CHAT/SUMMARY/DIAGNOSE` + `target_type=AI`，AiController 三端点补 `@OperationLog`） |

---

## 二、未解决（按优先级）

### P1（进入 Phase 4 前建议处理）

| # | 问题 | 影响 | 建议 |
|:--:|------|------|------|
| 4 | 无 Token 刷新/登出机制 | 24h 过期后必须重新登录 | Phase 4 前补 refresh token 或缩短说明 |
| 5 | 缺少全局日志 traceId | 排障难定位一次请求的完整链路 | 引入 Logback `%X{traceId}` + MDC 过滤器 |
| 19 | Redis 三客户端并存（jedis + spring-data-redis + redisson） | 依赖冗余、连接池复杂 | 收敛为 1-2 个客户端（jedis 仅 Day 43 练习用） |
| 24 | `DeepSeekProperties.apiKey` 空默认 + 启动期无 warn（ADR 0021 明示 opt-in 例外） | 误开启时缺 Key 只能在请求期暴露 | 补 `enabled=true && apiKey.isBlank()` 启动期 WARN 日志 |

### P2（渐进）

| # | 问题 | 影响 | 建议 |
|:--:|------|------|------|
| 6 | Entity 手写 getter/setter | 样板代码多 | 是否引入 Lombok（需推翻 ADR 0003 决策，先讨论） |
| 7 | 状态字段魔法数字（0/1/2） | 可读性差 | 引入 Status 枚举 |
| 8 | `user` 表名是 MySQL 保留字 | 潜在解析风险 | 重命名表或统一加反引号（现已有反引号） |
| 9 | 零外键约束 | 可能孤儿数据 | 应用层保证 + 定期审计脚本 |
| 10 | Swagger 示例值注解不丰富 | API 文档可读性 | 补 `@Schema`/`@ExampleObject` |
| 25 | AI 业务 DTO 枚举字段为纯 String（priority/healthLevel） | 前端映射/校验依赖约定 | 引入枚举或 `@Schema(allowableValues)` |

### P3（后续迭代）

| # | 问题 | 影响 | 建议 |
|:--:|------|------|------|
| 13 | ECharts 打包 687KB 无分包 | 首屏加载慢 | 按需引入 + 路由懒加载 |
| 14 | 报警规则硬编码 AlarmRuleConfig | 不可动态调整 | 规则入库 + 动态加载 |
| 15 | DeviceData 时间范围查询无分页 | 大数据量内存风险 | 加时间分页/聚合下推 |
| 26 | AI DTO 缺 `@Schema` 注解 | Knife4j 文档可读性一般 | 补注解（与其余模块 DTO 风格一致） |

---

## 三、遗留决策待确认

| 项 | 状态 |
|----|------|
| Elasticsearch 是否真正集成 | 已决策为 Day 101「ELK 日志（可选）」，届时再定去留 |
| Nacos 是否彻底移除 | 已决策为「预留基础设施：不纳入路线/不启动/无业务依赖」，仍留在 compose `full` profile |

---

> 维护约定：任何 Agent 引入新债务 → 追加本表；解决债务 → 从「未解决」移入「已解决」并标注日期。
