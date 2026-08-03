# Application Architecture V2.1

> **Status:** Active  
> **Version:** 2.1
> **Updated:** 2026-08-03
> **Based on:** Baseline V2.1 Hotfix（Day 042 后）
> **Governs:** All application-layer decisions for Industrial AI Hub Backend

---

## 1. 技术栈总览

### Runtime & Build

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 25 LTS (Temurin) | 唯一运行时 |
| Maven | 3.9.6 | Maven Wrapper 锁定 |
| Spring Boot | 3.5.0 | 应用框架父 POM |
| Spring Framework | 6.2.7 | Spring Boot 3.5 内置 |

### Persistence & Data

| 组件 | 版本 | 说明 |
|------|------|------|
| MyBatis | 3.5.19 | ORM（注解模式，分页统一 PageHelper） |
| MyBatis-Spring-Boot | 3.0.5 | 自动装配 |
| MySQL Connector/J | 9.2.0 | JDBC 驱动 |
| HikariCP | 6.3.0 | 连接池 |

### Web & API

| 组件 | 版本 | 说明 |
|------|------|------|
| Tomcat (Embedded) | 10.1.41 | 内嵌容器 |
| Jackson | (内置) | JSON 序列化 |
| Knife4j | 4.5.0 | Swagger 文档 |

### Security

| 组件 | 说明 |
|------|------|
| BCrypt | 密码加密 |
| JWT (jjwt 0.12.x) | Token 认证 |
| AuthInterceptor | RBAC 权限拦截 |
| JwtAuthFilter | Token 解析 + 注入 |
| RateLimitInterceptor | API 限流 |

### Infrastructure (compose.yml)

| 服务 | 版本 | 端口 | 配置状态 |
|------|------|------|:--:|
| MySQL | 8.4 | 3307 | Active |
| MySQL Master/Slave×2 | 8.4 | 13306-13308 | Configured |
| Redis Stack | 7.4.0 | 6379, 8001 | Configured |
| Redis Sentinel ×3 | 7.4.0 | 26379-81 | Configured |
| RabbitMQ | 4.0 | 5672, 15672 | Configured |
| Nacos | 2.4.3 | 8848, 9848 | Configured |
| MinIO | latest | 9000, 9001 | Configured |
| Elasticsearch | 8.17 | 9200, 9300 | Configured |
| Backend (Spring Boot) | JDK 25 | 8080 | Active (compose build) |

### Observability & 部署 (Baseline V2.1)

| 项 | 说明 |
|------|------|
| Spring Profiles | `application.yml`（基础）+ `-dev.yml`（DEBUG/SQL stdout/Swagger）+ `-prod.yml`（WARN/Slf4j/Swagger 关）；`SPRING_PROFILES_ACTIVE` 切换，默认 dev |
| Actuator | 仅暴露 `health`（show-details: never），供 Dockerfile/compose HEALTHCHECK 探测 |
| Dockerfile | `backend/Dockerfile`：multi-stage（25-jdk-alpine 构建 → 25-jre-alpine 运行），non-root 用户，layer caching，HEALTHCHECK /actuator/health |
| JWT 密钥 | 生产环境由 compose 注入 `JWT_SECRET`；测试环境使用 `application-test.yml` 隔离配置 |

---

## 2. 分层架构

```
HTTP Request
  → JwtAuthFilter        (Bearer Token 解析，注入 userId/roles)
  → RateLimitInterceptor (QPS 限流)
  → AuthInterceptor      (@RequireRole 角色校验)
  → Controller            (@RestController)
  → Service               (@Service + BusinessException)
  → Mapper                (@Mapper, PageHelper 分页)
  → MySQL (HikariCP)
```

---

## 3. 模块清单（Day 42）

### Controllers (6)

| Controller | 端点 | 鉴权 |
|-----------|------|------|
| AuthController | POST /login, /register | 公开 |
| DeviceController | CRUD + searchDevices | VIEWER/OPERATOR/ADMIN |
| UserController | listPage/get/update/toggleStatus/delete | ADMIN |
| AlarmController | listPaged/acknowledge/resolve | VIEWER+ |
| DeviceDataController | report/list/stats/latest | VIEWER+ |
| OperationLogController | listPaged/listByUserId/listRecent | ADMIN |

### Services (7)

DeviceService / UserService / AuthService / AlarmService / DeviceDataService /
OperationLogService / AlarmDetector

### 横切关注点

- GlobalExceptionHandler: BusinessException + @Valid + Exception 三层兜底
- OperationLogAspect: @OperationLog AOP 自动记录（finally 块确保异常也记录）
- ErrorCode 枚举: 统一错误码
- Operator 枚举: 告警规则比较运算符 (GT/LT/GTE/LTE/EQ/NEQ)
- BusinessException: Service 层统一异常

### 前端 (Vue 3 + Element Plus)

| 页面 | 路由 | 状态 |
|------|------|:--:|
| Login | /login | ✅ 含路由守卫 + 401 跳转 |
| Dashboard | /dashboard | ✅ KPI + 告警流 + ECharts（默认首页） |
| DeviceList | /devices | ✅ |
| DeviceDetail | /devices/:id | ✅ |
| AlarmList | /alarms | ✅ |
| OperationLogList | /logs | ✅ |

---

## 4. API 端点清单

25 个端点 + Knife4j 文档 (/doc.html)。

---

## 5. 数据库

`reboot` 数据库，7 张表，8 个 CHECK 约束，零 FK，软删除策略。

---

## 6. 演进路线

| 阶段 | 内容 | 状态 |
|------|------|:--:|
| Phase 1 (Day 1-14) | Java 基础 | ✅ |
| Phase 2 (Day 15-42) | Spring Boot + MyBatis + RBAC + 告警 + 前端 | ✅ v1.0 完成 |
| Baseline V2.1 | Profiles + Actuator + Dockerfile + compose backend + JWT (compose 注入) + 启动冒烟测试 | ✅ 2026-08-03 |
| Phase 3 (Day 43-56) | Redis / RabbitMQ / ES 中间件 | 📅 待启动 |
| Phase 4 (Day 57-70) | 微服务 Spring Cloud | 📅 计划 |
| Phase 5 (Day 71-84) | AI 集成 | 📅 计划 |
