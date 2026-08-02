# Industrial AI Hub — 系统架构图

> 版本：v1.0 | 更新：2026-08-02

## 总体架构

```mermaid
graph TB
    subgraph "Frontend (Vue 3 + Vite)"
        FE[Vue 3 SPA<br/>4 Pages: Device / Alarm / Log / Detail]
        ECharts[ECharts<br/>Temperature & Pressure Charts]
        FE --> ECharts
    end

    subgraph "Gateway & Security"
        JWT[JWT Filter<br/>Token Validation]
        RL[Rate Limit Interceptor<br/>Guava 50 req/s]
        AUTH[Auth Interceptor<br/>RBAC: @RequireRole]
        JWT --> RL --> AUTH
    end

    subgraph "Spring Boot 3.5 (JDK 25)"
        subgraph "Controller Layer"
            DC[DeviceController]
            AC[AuthController]
            UC[UserController]
            DDC[DeviceDataController]
            ALC[AlarmController]
            OLC[OperationLogController]
        end

        subgraph "Service Layer"
            DS[DeviceService]
            AS[AuthService]
            US[UserService]
            DDS[DeviceDataService]
            ALS[AlarmService]
            OLS[OperationLogService]
            AD[AlarmDetector<br/>8 Rule Engine]
        end

        subgraph "Mapper Layer"
            DM[DeviceMapper]
            UM[UserMapper]
            DDM[DeviceDataMapper]
            ALM[AlarmMapper]
            OLM[OperationLogMapper]
            RM[RoleMapper]
            URM[UserRoleMapper]
        end

        subgraph "Cross-cutting"
            GEH[GlobalExceptionHandler<br/>4 Exception Types]
            AOP[OperationLog Aspect<br/>Auto Record]
            KNIFE[Knife4j / Swagger<br/>API Docs]
        end
    end

    subgraph "Infrastructure (Docker Compose)"
        MYSQL[(MySQL 8.4<br/>Master-Slave<br/>7 Tables)]
        REDIS[(Redis Stack<br/>Bloom + JSON + TS)]
        RMQ[RabbitMQ 4.0]
        NACOS[Nacos 2.4]
        ES[Elasticsearch 8.17]
        MINIO[MinIO]
    end

    FE --> JWT
    DC --> DS --> DM --> MYSQL
    AC --> AS --> UM --> MYSQL
    UC --> US --> UM --> MYSQL
    DDC --> DDS --> DDM --> MYSQL
    ALC --> ALS --> ALM --> MYSQL
    OLC --> OLS --> OLM --> MYSQL
    DDS --> AD
    AD --> ALS

    GEH -.-> DC & AC & UC & DDC & ALC & OLC
    AOP -.-> DC & AC & ALC & DDC
    KNIFE -.-> DC & AC & UC & DDC & ALC & OLC
```

## 请求处理链路

```mermaid
sequenceDiagram
    participant Browser
    participant JwtFilter
    participant RateLimiter
    participant AuthInterceptor
    participant Controller
    participant Service
    participant Mapper
    participant MySQL

    Browser->>JwtFilter: GET /api/devices?page=1<br/>Authorization: Bearer {token}
    JwtFilter->>JwtFilter: Parse & Validate JWT
    JwtFilter->>RateLimiter: Set userId/username/roles
    RateLimiter->>RateLimiter: Check permits (50 req/s)
    RateLimiter->>AuthInterceptor: pass
    AuthInterceptor->>AuthInterceptor: @RequireRole check
    AuthInterceptor->>Controller: pass
    Controller->>Service: searchDevices(keyword, type, status, page, size)
    Service->>Mapper: SELECT with dynamic SQL
    Mapper->>MySQL: Execute Query
    MySQL-->>Mapper: ResultSet
    Mapper-->>Service: List<Device>
    Service-->>Controller: PageInfo<DeviceVO>
    Controller-->>Browser: ApiResponse{code:200, data:{...}}
```

## 技术栈分层

| 层 | 技术 | 版本 |
|----|------|------|
| 前端 | Vue 3 + Vite + ECharts | latest |
| 构建 | Maven Wrapper | 3.9.6 |
| 运行时 | JDK (Temurin) | 25 LTS |
| Web 框架 | Spring Boot | 3.5.0 |
| ORM | MyBatis Spring Boot | 3.0.5 |
| 分页 | PageHelper | 2.1.0 |
| 认证 | JJWT | 0.12.6 |
| 加密 | BCrypt (spring-security-crypto) | — |
| 限流 | Guava RateLimiter | 33.4.0 |
| API 文档 | Knife4j | 4.5.0 |
| 测试 | JUnit 5 + Mockito | spring-boot-starter-test |
| 数据库 | MySQL 8.4 (Docker) | — |
| 缓存 | Redis Stack | 7.4.0-v1 |
| 消息 | RabbitMQ | 4.0 |
| 注册 | Nacos | 2.4.3 |
| 搜索 | Elasticsearch | 8.17.0 |
