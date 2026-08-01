# Industrial AI Hub

> 工业 AI 设备管理平台 — 从 Java 基础到 AI 集成的完整技术栈闭环

[![v1.0-alpha](https://img.shields.io/badge/version-v1.0--alpha-blue)](https://github.com/lgmhula/Industrial-AI-Hub)
[![JDK](https://img.shields.io/badge/JDK-25_LTS-orange)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.0-green)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/license-MIT-lightgrey)](LICENSE)

---

## 项目简介

Industrial AI Hub 是一个面向工业场景的设备管理与 AI 分析平台。支持设备 CRUD、传感器数据采集、报警规则引擎、RBAC 权限控制、操作日志审计等功能。前端 Vue3 + ECharts 可视化，后端 Spring Boot 3.5 + MyBatis，基础设施 Docker Compose 统一编排（MySQL 主从、Redis Stack、RabbitMQ、Nacos、Elasticsearch 等 12 个服务）。

---

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| **运行时** | JDK (Eclipse Temurin) | 25 LTS |
| **构建** | Maven Wrapper | 3.9.6 |
| **后端** | Spring Boot | 3.5.0 |
| | MyBatis Spring Boot | 3.0.5 |
| | PageHelper | 2.1.0 |
| | Knife4j (Swagger) | 4.5.0 |
| **安全** | JJWT | 0.12.6 |
| | BCrypt (spring-security-crypto) | — |
| **限流** | Guava RateLimiter | 33.4.0-jre |
| **数据库** | MySQL 8.4 (Docker) | 8.4 |
| **缓存** | Redis Stack | 7.4.0-v1 |
| **消息** | RabbitMQ | 4.0-management |
| **注册** | Nacos | 2.4.3 |
| **搜索** | Elasticsearch | 8.17.0 |
| **前端** | Vue 3 + Vite | — |
| | ECharts + vue-echarts | — |
| | Axios | — |

---

## 快速开始

### 环境要求

- JDK 25
- Docker Desktop / OrbStack
- Node.js 20+
- Maven（项目内置 Maven Wrapper，无需手动安装）

### 1. 启动基础设施

```bash
# 启动 MySQL(主从) + Redis
docker compose --profile core up -d

# 启动全部服务（12 个）
docker compose up -d
```

### 2. 初始化数据库

容器首次启动会自动执行 `backend/src/main/resources/sql/init.sql`（含 7 张表 + 默认角色 + admin 账户）。

默认管理员：
```
用户名: admin
密码: admin123
```

### 3. 启动后端

```bash
cd backend
./mvnw spring-boot:run
```

应用启动在 `http://localhost:8080`。

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端启动在 `http://localhost:5173`（Vite 代理已配置 `/api` → `localhost:8080`）。

---

## API 文档

启动后端后访问：

```
http://localhost:8080/doc.html
```

Knife4j 提供交互式 Swagger UI，含所有 26 个 API 端点的请求/响应示例。

---

## 功能模块

| 模块 | 说明 |
|------|------|
| **认证 (Auth)** | JWT 登录/注册，BCrypt 密码加密 |
| **RBAC 权限** | 三级角色 (ADMIN/OPERATOR/VIEWER)，`@RequireRole` 注解驱动 |
| **设备管理 (Device)** | CRUD + 逻辑删除 + 分页搜索 (关键字/类型/状态) |
| **设备数据 (DeviceData)** | 传感器数据上报、时间范围查询、聚合统计 (avg/min/max) |
| **报警管理 (Alarm)** | 8 条报警规则引擎，确认/解决工作流 |
| **操作日志 (OperationLog)** | AOP 自动记录，支持按用户查询 |
| **接口限流** | Guava RateLimiter，按 URI 独立限流 (默认 50 req/s) |
| **Postman 测试集** | 47 个测试用例 (auth/device/alarm/log/data 全覆盖) |

---

## 项目结构

```
Industrial-AI-Hub/
├── README.md
├── AGENTS.md                  # AI 助手入口
├── compose.yml                # Docker 编排
├── .env / .env.example
│
├── backend/
│   ├── pom.xml                # Maven POM
│   ├── .mvn/maven-wrapper.properties
│   ├── DAILY_ROADMAP.md       # 16 周路线图
│   ├── DAILY/                 # 每日日志
│   ├── REVIEW/                # 周复盘
│   ├── postman/               # Postman 测试集合
│   └── src/
│       ├── main/java/dev/reboot/
│       │   ├── controller/    # REST API (6 controllers, 26 endpoints)
│       │   ├── service/       # 业务逻辑
│       │   ├── mapper/        # MyBatis Mapper
│       │   ├── entity/        # 实体类
│       │   ├── dto/           # DTO + VO + ApiResponse
│       │   ├── config/        # Spring 配置
│       │   ├── security/      # JWT Filter + Auth/RateLimit Interceptor
│       │   ├── enums/         # ErrorCode / RoleEnum
│       │   ├── annotation/    # @RequireRole / @OperationLog
│       │   └── exception/     # BusinessException + GlobalExceptionHandler
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── sql/init.sql   # 数据库初始化
│       └── test/              # 35 单元测试
│
├── frontend/
│   ├── src/
│   │   ├── views/             # 4 页面 (DeviceList/Detail/AlarmList/OperationLogList)
│   │   ├── components/        # Toast / LoadingSpinner
│   │   ├── router/            # Vue Router
│   │   └── api/               # Axios 封装
│   └── vite.config.js
│
├── docs/
│   ├── Architecture/          # 架构文档
│   ├── decision-log/          # ADR 决策记录
│   └── reports/               # 审计报告
│
├── mysql/                     # MySQL 配置 + 数据 (主从)
├── redis/                     # Redis 配置 + 数据
├── nginx/                     # Nginx 配置
├── rabbitmq/                  # RabbitMQ 数据
├── minio/                     # MinIO 数据
├── nacos/                     # Nacos 数据
└── elasticsearch/             # ES 数据
```

---

## 数据库

数据库名 `reboot`，7 张表：

| 表 | 说明 |
|----|------|
| `user` | 用户表 (BCrypt + 逻辑删除) |
| `role` | 角色表 (ADMIN/OPERATOR/VIEWER) |
| `user_role` | 用户角色关联 |
| `device` | 设备表 (逻辑删除) |
| `device_data` | 设备数据 (DECIMAL 精度) |
| `alarm` | 告警表 (确认/解决工作流) |
| `operation_log` | 操作日志 (AOP 自动写入) |

---

## 学习路线

项目按照 16 周路线图 (`backend/DAILY_ROADMAP.md`) 渐进式构建：

| 阶段 | 周期 | 内容 |
|------|------|------|
| Phase 1 | Week 1-3 | Java 基础复苏 (Day 1-21) |
| Phase 2 | Week 4-6 | Industrial AI Hub V1 (Day 22-42) |
| Phase 3 | Week 7-9 | 中间件武装 (Day 43-63) |
| Phase 4 | Week 10-13 | AI 集成 (Day 64-91) |
| Phase 5 | Week 14-16 | PLC + 完整系统 (Day 92-112) |

> 当前进度：Day 39 (第二阶段 — V1 打磨 + 测试)

---

## 开发约定

- **构造器注入** — 禁止 `@Autowired` 字段注入
- **统一响应** — 所有 API 返回 `ApiResponse<T>`
- **Javadoc** — 所有公共类和方法必须有 Javadoc 注释
- **4 空格缩进** — `.editorconfig` 管理
- **Git commit** — 格式 `Day XXX: 简要说明`
- **逻辑删除** — `user`/`device` 使用 `is_deleted` 字段

---

## License

MIT

---

> 最后更新：2026-08-01 | 维护者：[hula0710](https://github.com/lgmhula)
