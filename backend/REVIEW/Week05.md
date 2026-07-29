# Week 05 复盘 — 2026-07-27 ~ 2026-07-29

## 本周已完成

| Day | 日期 | 任务 | 状态 |
|:---:|------|------|:---:|
| Day 29 | 7/27 | 设备数据模块完善 + SQL 审计 | ✅ |
| Day 30 | 7/27 | 设备数据上报 + 时间范围 + 聚合统计 + Postman | ✅ |
| Day 31 | 7/27 | Vue 3 前端搭建 + 设备管理 + ECharts 详情 | ✅ |
| Day 32 | 7/28 | 报警规则引擎 + 数据上报自动检测 + 分页 | ✅ |
| Day 33 | 7/28 | AOP 操作日志 + @OperationLog 注解 + Postman 更新 | ✅ |
| Day 34 | 7/29 | 前端补全 — 报警页面 + 日志页面 + 全局联调 | ✅ |
| Day 35 | 7/29 | 周复盘 + 代码 Review + v1.0-alpha tag | ✅ |

## 本周技术产出

### 后端（53 Java files / 26 API endpoints / 14 packages）

**设备数据模块（Day 29-30）**
- 设备数据上报接口 + 时间范围查询 + 聚合统计（avg/min/max/count）
- 48 条模拟数据（24 温度 + 24 压力）
- DECIMAL(18,6) 工业精度

**报警规则引擎（Day 32）**
- AlarmRule POJO + AlarmRuleConfig（8 条默认规则）
- AlarmDetector 检测引擎：数据上报 → 自动匹配规则 → 触发报警
- 报警分页查询 + 确认/解决
- 规则覆盖：温度/压力/转速/湿度 × 上下限

**AOP 操作日志（Day 33）**
- @OperationLog 注解标记 Controller 方法
- OperationLogAspect 自动获取 userId/IP 并写入 DB
- 已标注 7 个关键端点（Device CRUD / Auth / Alarm）
- 零侵入 — 日志失败不影响业务

**SQL 审计（Day 29 附带）**
- 3 文件审计 → migrate_v1.1.sql 归档，mock → seed_test_data.sql
- init.sql 去硬编码 DB 名
- application.yml P0 密码默认值移除
- mysql/init/01_init.sql 符号链接（Docker 重建保护）

### 前端（Vue 3 / 4 页面 / 3 API 模块）

| 页面 | 路由 | 功能 |
|------|------|------|
| DeviceList | /devices | 搜索/筛选/分页/新增编辑弹窗/删除 |
| DeviceDetail | /devices/:id | 信息网格 + 统计卡片 + ECharts 双图表 |
| AlarmList | /alarms | 状态筛选/确认/解决/等级颜色 |
| OperationLogList | /logs | 分页（ADMIN）/操作类型/IP/时间 |

### 基础设施

- Postman 集合：7 个文件夹 / 46+ 测试用例
- Docker Compose：MySQL 8.4 + Redis Stack + RabbitMQ + Nacos + MinIO + ES + 主从 + Sentinel
- Maven：spring-boot-starter-aop 新增
- 前端：vite proxy /api → 8080

## 代码质量 Review

| 检查项 | 结果 | 说明 |
|------|:---:|------|
| @Autowired 字段注入 | ✅ 0 处 | 全部构造器注入 |
| System.out.println | ✅ 0 处 | 全部 slf4j log.* |
| printStackTrace | ✅ 0 处 | 全部 GlobalExceptionHandler 兜底 |
| TODO/FIXME 残留 | ✅ 0 处 | 代码干净 |
| 日志覆盖 | ✅ 9 文件 | controller/service/aop/security 全覆盖 |
| 异常处理 | ✅ 统一 | BusinessException + GlobalExceptionHandler |

### 发现的小问题

| # | 问题 | 严重度 | 建议 |
|:---:|------|:---:|------|
| 1 | `OperationLog` 实体直接返回 Controller | P2 | Day 36 后新建 OperationLogVO |
| 2 | 前端无登录页面（需手动 curl 获取 token 后粘贴） | P2 | P3 — 当前学习阶段可接受 |
| 3 | ECharts 全量打包导致 JS bundle 674KB | P3 | 后期改为动态 import 分包 |
| 4 | 报警规则硬编码在 AlarmRuleConfig | P3 | 后期迁移到数据库/配置中心 |

## 项目规模

| 维度 | 数量 |
|------|:---:|
| 后端 Java 文件 | 53 |
| API 端点 | 26 |
| 数据库表 | 7 |
| CHECK 约束 | 8 |
| 报警规则 | 8 |
| 前端页面 | 4 |
| Postman 用例 | 46+ |
| Docker 服务 | 12 |
| 审计报告 | 2 |
| 架构决策记录 | 11 |

## 下周计划（第 6 周：V1 打磨 + 测试）

| Day | 任务 |
|:---:|------|
| Day 36 | 单元测试：UserService、DeviceService |
| Day 37 | 单元测试：AlarmService、DeviceDataService |
| Day 38 | 集成测试 + Postman 自动化 |
| Day 39 | 前端登录页面 + Token 管理 |
| Day 40 | 代码重构 + SonarLint 清零 |
| Day 41 | API 文档（Swagger/Knife4j） |
| Day 42 | 第二阶段复盘 + v1.0 正式版 |

## 经验沉淀

1. **AOP 是审计利器**：一个 @OperationLog 注解即可实现全链路操作追踪，零业务代码侵入。
2. **报警与数据上报解耦**：AlarmDetector 独立于 DeviceDataService，单向依赖，易测试易替换。
3. **Postman 即文档**：46+ 用例的 Postman 集合比手写 API 文档更可靠（可执行验证）。
4. **前端渐进式搭建**：先列表后详情，先设备后报警，每个页面独立可测。
5. **审计驱动质量**：SQL Audit Report + Architecture Consistency Report 两次审计共计发现并修复 40+ 项问题。

---

> **第 5 周结束**。Industrial AI Hub V1 核心功能闭环已完成：认证 → RBAC → 设备 CRUD → 数据上报 → 报警引擎 → AOP 日志。
> 
> **里程碑**: v1.0-alpha
