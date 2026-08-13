# Day 59 — docker-compose 编排：MySQL + Redis + RabbitMQ + 应用

> 日期：2026-08-13 | 阶段：Phase 3（第 9 周 Docker）

## 今日目标

- [x] 审查 compose.yml 编排
- [x] 修复 backend 服务缺失的 Redis/RabbitMQ 环境变量
- [x] 补全 depends_on 依赖链
- [x] `docker compose config` 校验通过

## 产出

### 1. compose backend 服务修复

**问题**：backend 服务只配置了 MySQL + JWT，缺失 Redis 和 RabbitMQ 环境变量，
导致应用启动后无法连接缓存和消息队列。

**修复**：补全环境变量 + 依赖链：

```yaml
backend:
  environment:
    # MySQL
    MYSQL_HOST: mysql
    MYSQL_PORT: "3306"
    # Redis（新增）
    REDIS_HOST: redis
    REDIS_PORT: "6379"
    REDIS_PASSWORD: ${REDIS_PASSWORD}
    # RabbitMQ（新增）
    RABBITMQ_HOST: rabbitmq
    RABBITMQ_PORT: "5672"
    RABBITMQ_DEFAULT_USER: ${RABBITMQ_DEFAULT_USER}
    RABBITMQ_DEFAULT_PASS: ${RABBITMQ_DEFAULT_PASS}
  depends_on:
    mysql:
      condition: service_healthy
    redis:
      condition: service_healthy        # 新增
    rabbitmq:
      condition: service_healthy        # 新增
```

### 2. 编排依赖链

```
backend (工业 AI Hub 应用)
  ├─ mysql (数据库)    ← service_healthy 后启动
  ├─ redis (缓存)      ← service_healthy 后启动
  └─ rabbitmq (消息)   ← service_healthy 后启动
```

### 3. 配置校验

```bash
docker compose config --quiet
# 通过
```

## 关键知识点

1. **服务名即主机名**：compose 网络内，`redis` 服务名可直接作为 `REDIS_HOST`，无需 IP
2. **容器内端口 vs 宿主机端口**：MySQL 宿主机映射 3307→3306，容器内 backend 用 3306
3. **`condition: service_healthy`**：依赖服务健康检查通过后才启动，避免应用连不上中间件

## 验证

```bash
docker compose config --quiet && echo "配置校验通过"
```

## 明日

Day 60 — Linux 基础命令（虚拟机或云服务器练习）
