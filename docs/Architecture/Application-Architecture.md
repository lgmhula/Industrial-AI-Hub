# Application Architecture V1.2

> **Status:** Active  
> **Version:** 1.2
> **Updated:** 2026-07-21
> **Based on Commit:** Day 022 (三层架构 + ApiResponse 实现)  
> **Governs:** All application-layer decisions for Industrial AI Hub Backend

---

## 1. 技术栈总览 (Tech Stack)

### Runtime & Build

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 25 LTS (Temurin) | Oracle LTS: 17→21→25, 2025-09 发布 |
| Maven | 3.9.6 | 构建工具，Maven Wrapper 锁定，阿里云镜像加速 |
| Spring Boot | 3.5.0 | 应用框架父 POM |
| Spring Framework | 6.2.7 | Spring Boot 3.5 内置 |

### Persistence & Data

| 组件 | 版本 | 说明 |
|------|------|------|
| MyBatis | 3.5.19 | ORM 框架（注解模式） |
| MyBatis-Spring-Boot | 3.0.5 | Spring Boot 自动装配 |
| MySQL Connector/J | 9.2.0 | JDBC 驱动，兼容 MySQL 8.x |
| HikariCP | 6.3.0 | Spring Boot 默认连接池 |

### Web & API

| 组件 | 版本 | 说明 |
|------|------|------|
| Tomcat (Embedded) | 10.1.41 | Spring Boot 内嵌 Servlet 容器 |
| Jackson | (Spring Boot 内置) | JSON 序列化/反序列化 |

### Infrastructure (via compose.yml)

| 服务 | 版本 | 端口 | 状态 |
|------|------|------|------|
| MySQL (Dev) | 8.4 | 3307 | Active |
| MySQL Master/Slave×2 | 8.4 | 13306-13308 | Active |
| Redis Stack | 7.4.0 | 6379 | Active |
| Redis Sentinel ×3 | 7.4.0 | 26379-81 | Configured |
| RabbitMQ | 4.0 | 5672 | Active |
| Nacos | 2.4.3 | 8848 | Active |
| MinIO | latest | 9000 | Active |
| Elasticsearch | 8.17 | 9200 | Active |

---

## 2. 项目分层架构 (Layered Architecture)

### 当前实现 (Day 022)

```
┌──────────────────────────────────────┐
│            Presentation              │  Controller
│     @RestController + @RequestMapping│
├──────────────────────────────────────┤
│            Business Logic            │  Service
│     @Service (业务逻辑 + 事务)        │
├──────────────────────────────────────┤
│           Persistence                │  Mapper
│     @Mapper (注解 SQL)               │
├──────────────────────────────────────┤
│           Infrastructure             │  MySQL / HikariCP
│     compose.yml 统一管理              │
└──────────────────────────────────────┘
```

### 调用链路

```
HTTP Request → DeviceController (@RestController)
  → DeviceService (@Service, 业务逻辑)
    → DeviceMapper (@Mapper, 数据访问)
      → MySQL (HikariCP 连接池)
```

### 已实现模块 (Day 022)

- **Service 层** (`dev.reboot.service`) — DeviceService / UserService / AlarmService
- **统一响应** (`dev.reboot.dto.ApiResponse<T>`) — 标准 JSON 响应格式 {code, message, data}
- **CORS 配置** (`dev.reboot.config.CorsConfig`) — 跨域支持
- **DTO 层** (`dev.reboot.dto`) — DeviceDTO / LoginDTO

### 计划中 (Day 023+)

- **JWT 认证** — 登录/注册接口 + Token 生成/验证
- **全局异常处理** (`dev.reboot.config.GlobalExceptionHandler`) — @RestControllerAdvice
- **RBAC 权限拦截** — @RequireRole 注解 + JWT Filter
- **Product/Category 模块** — XML ResultMap 关联查询

---

## 3. 源码目录结构 (Source Tree)

```
backend/
├── pom.xml                        # Maven POM (Spring Boot 3.5 Parent)
├── .mvn/
│   ├── jvm.config                 # JDK 25 编译参数
│   └── wrapper/                   # Maven Wrapper (锁定 3.9.6)
├── .editorconfig                  # 代码风格
│
├── src/main/java/
│   ├── dev/reboot/                # === 主应用 (Day 21+) ===
│   │   ├── IndustrialAiHubApplication.java
│   │   ├── controller/
│   │   │   └── DeviceController.java    # /api/devices CRUD
│   │   ├── service/
│   │   │   ├── DeviceService.java       # 设备业务逻辑
│   │   │   ├── UserService.java         # 用户业务逻辑
│   │   │   └── AlarmService.java        # 告警业务逻辑
│   │   ├── mapper/
│   │   │   ├── DeviceMapper.java        # 注解 SQL
│   │   │   ├── UserMapper.java
│   │   │   ├── RoleMapper.java
│   │   │   ├── DeviceDataMapper.java
│   │   │   ├── AlarmMapper.java
│   │   │   └── OperationLogMapper.java
│   │   ├── entity/
│   │   │   ├── Device.java, User.java, Role.java
│   │   │   ├── UserRole.java, DeviceData.java
│   │   │   ├── Alarm.java, OperationLog.java
│   │   ├── dto/
│   │   │   ├── ApiResponse.java         # 统一响应 {code,message,data}
│   │   │   ├── DeviceDTO.java
│   │   │   └── LoginDTO.java
│   │   └── config/
│   │       └── CorsConfig.java          # 跨域配置
│   │
│   └── code/day01~22/             # === 学习代码 (Day 1~22) ===
│       └── dayXX/
│           ├── DayXX_*.java       # 当日练习
│           └── LeetCode*.java     # 算法题
│
├── src/main/resources/
│   ├── application.yml            # Spring Boot 全配置
│   ├── sql/init.sql               # 数据库初始化脚本
│   └── code/dayXX/                # 学习期 XML mapper 文件
│
├── DAILY/                         # 每日日志 Day001~022.md
├── REVIEW/                        # 周度复盘 Week01~02.md
└── DAILY_ROADMAP.md               # 112 天完整路线图
```

---

## 4. 配置说明

### application.yml 关键配置

```yaml
server.port: 8080                  # HTTP 端口

spring.datasource:
  # 使用环境变量，Docker MySQL 端口映射为 3307
  url: jdbc:mysql://${MYSQL_HOST:127.0.0.1}:${MYSQL_PORT:3307}/reboot
  username: ${MYSQL_USER:root}
  password: ${MYSQL_PASSWORD:1zxcvbnm}

mybatis:
  mapper-locations: classpath:code/**/*Mapper.xml
  type-aliases-package: dev.reboot.entity
  configuration:
    map-underscore-to-camel-case: true     # 自动驼峰映射
```

### 数据库连接策略

Spring Boot 运行于宿主机，通过 Docker 端口映射 (`3307:3306`) 访问容器内 MySQL。连接参数通过环境变量注入，支持多环境切换。

---

## 5. API 端点清单

### Device API (`/api/devices`) — 已实现

| 方法 | 路径 | 说明 | 状态 |
|------|------|------|------|
| GET | /api/devices | 查询全部设备 | ✅ |
| GET | /api/devices/{id} | 按 ID 查询 | ✅ |
| POST | /api/devices | 新增设备 | ✅ |
| PUT | /api/devices/{id} | 更新设备 | ✅ |
| DELETE | /api/devices/{id} | 删除设备 | ✅ |

### Product API (`/api/products`) — 计划中 (Day 022+)

| 方法 | 路径 | 说明 | 状态 |
|------|------|------|------|
| GET | /api/products | 全部产品(含分类) | 📅 Planned |
| GET | /api/products/{id} | 产品+分类(association) | 📅 Planned |
| GET | /api/products/category/{id} | 按分类筛选 | 📅 Planned |

### 响应格式 (Day 022 已实现)

所有 Controller 返回值统一包裹为 `ApiResponse<T>`（`dev.reboot.dto.ApiResponse`）：

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

---

## 6. 架构决策记录 (Key Decisions)

| 编号 | 决策 | 日期 | 状态 |
|------|------|------|------|
| ADR-001 | JDK 25 LTS 为唯一运行时 | 2026-07-11 | ✅ 已实施 |
| ADR-002 | Spring Boot 3.5 替代独立 MyBatis | 2026-07-19 | ✅ 已实施 |
| ADR-003 | @Mapper 注解模式（XML 保留用于学习） | 2026-07-20 | ✅ 已实施 |
| ADR-004 | Controller→Service→Mapper 三层架构 | 2026-07-20 | ✅ 已实施 |
| ADR-005 | ApiResponse<T> 统一响应 (dev.reboot.dto) | 2026-07-20 | ✅ 已实施 |

---

## 7. 启动命令

```bash
# 编译
cd backend && mvn clean compile -q

# 启动 (嵌入式 Tomcat :8080)
mvn spring-boot:run

# 测试
curl http://localhost:8080/api/devices
```

---

## 8. 演进路线

| 阶段 | 内容 | 状态 |
|------|------|------|
| Phase 1 (Day 1-7) | Java 基础语法 | ✅ 完成 |
| Phase 2 (Day 8-14) | 集合/泛型/异常/IO | ✅ 完成 |
| Phase 3 (Day 15-21) | MySQL/JDBC/MyBatis/SB | ✅ 完成 |
| Phase 4 (Day 22-28) | 工程化/校验/分页/Redis | 🔄 进行中 (Day 22 ✅) |
| Phase 5 (Day 29-42) | Spring Security/JWT | 📅 计划 |
| Phase 6 (Day 43-56) | 微服务 Spring Cloud | 📅 计划 |
