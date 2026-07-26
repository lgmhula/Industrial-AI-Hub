# Week 04 复盘 — 2026-07-21 ~ 2026-07-26

## 本周已完成

| Day | 日期 | 任务 | 状态 |
|:---:|------|------|:---:|
| Day 22 | 7/21 | MyBatis CRUD 基础 + Service 层 | ✅ |
| Day 23 | 7/22 | JWT 工具类 + 登录/注册 + BCrypt | ✅ |
| Day 24 | 7/23 | RBAC 权限拦截 + UserRoleMapper | ✅ |
| Day 25 | 7/24 | 用户管理 + PageHelper 分页 | ✅ |
| Day 26 | 7/25 | GlobalExceptionHandler + @Valid 校验 | ✅ |
| Day 27 | 7/26 | BusinessException 全链路接入 + 审计修复 | ✅ |
| Day 28 | 7/26 | 周复盘 + 全审计遗留项修复 | ✅ |

## 本周技术产出

### 认证与安全
- JWT Token 生成/验证/解析（JwtUtils + JwtAuthFilter）
- BCrypt 密码加密（SecurityConfig + BCryptPasswordEncoder）
- RBAC 三级权限（ADMIN/OPERATOR/VIEWER） + AuthInterceptor + @RequireRole
- LoginRequest/RegisterRequest DTO 语义分离

### 核心业务
- Device CRUD（Controller→Service→Mapper 三层完整）
- User 管理（分页、编辑、状态切换、逻辑删除）
- Alarm/DeviceData/OperationLog API 端点全覆盖

### 架构质量
- ApiResponse<T> 统一响应格式（12 个端点一致）
- GlobalExceptionHandler 全局异常兜底（BusinessException/@Valid/500）
- 8 个 CHECK 约束保护数据完整性
- Service 层全接入 BusinessException，Controller 零手工 error

### 代码规模
- 编译文件：97 → 104（+7）
- API 端点：12 → 19（+7）
- 审计 V1.2 全部 9 项：✅ 已修复 9/9 (100%)

## 下周计划（第 5 周：设备管理 + 设备数据模块）

| Day | 任务 |
|:---:|------|
| Day 29 | 设备数据模块完善（device_data → 图表/统计 API） |
| Day 30 | 告警模块完善（Alarm 时间线、批量操作） |
| Day 31 | 前后端联调 + Postman 集合完整版 |
| Day 32 | device_data 图表 API + 聚合查询 |
| Day 33 | AOP 操作日志自动记录 |
| Day 34 | 代码重构 + 单元测试补充 |
| Day 35 | 第 5 周复盘 |

## 经验沉淀

1. **异常处理最佳实践**：Service throw → GlobalExceptionHandler catch → ApiResponse 返回。不再在 Controller 写 if-null。
2. **DTO 拆分原则**：登录和注册用不同 DTO，各自独立的校验规则。
3. **死代码即技术债**：Mapper 写了就要用，不用就删。
4. **审计驱动开发**：Architecture Consistency Report 是真实的质量锚点。

---

> **第 4 周结束**。第二阶段（Industrial AI Hub V1）核心骨架已基本搭建完成。
