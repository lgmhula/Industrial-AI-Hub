# Infrastructure Baseline V1.1

> **Status:** Active  
> **Version:** 1.1
> **Updated:** 2026-07-20
> **Based on Commit:** 2b56dec  
> **Governs:** All infrastructure decisions for Industrial AI Hub

---

## 1. Core Principles

- **Unified Specification** — One compose file, one network, one .env
- **Configuration as Code** — All configs versioned in Git
- **Separation of Concerns** — config / data / logs in separate directories
- **Decision Log** — Every architectural choice is documented
- **Config vs Runtime** — This document tracks **configuration state** (is the service defined, configured, and expected to be used). Runtime state is transient; check with `docker compose ps`.

---

## 2. Service Naming Convention

| Service | Container Name | Internal Host |
|---------|---------------|---------------|
| MySQL | `mysql` | `mysql` |
| Redis | `redis` | `redis` |
| RabbitMQ | `rabbitmq` | `rabbitmq` |
| Nacos | `nacos` | `nacos` |
| MinIO | `minio` | `minio` |
| Elasticsearch | `elasticsearch` | `elasticsearch` |
| Nginx | `nginx` | `nginx` |

**Rules:**
- No IP addresses in configuration — use service names
- No prefixes (e.g., `gateway-nginx`, `my-nginx`)
- No master/slave naming in service names — use dedicated services if needed

---

## 3. Directory Structure

```text
Industrial-AI-Hub/
├── backend/              # Java Spring Boot application
├── frontend/             # Web frontend [reserved]
├── docs/
│   ├── Architecture/
│   │   ├── README.md
│   │   ├── Application-Architecture.md
│   │   └── Infrastructure-Baseline.md
│   ├── decision-log/
│   └── reports/
├── compose.yml           # Unified Docker Compose
├── .env                  # Secrets (NOT committed)
├── .env.example          # Template
├── mysql/
│   ├── conf/my.cnf
│   ├── init/             # SQL init scripts
│   └── data/             # Persistent volume
├── redis/
│   ├── redis.conf
│   └── data/
├── nginx/
│   ├── nginx.conf
│   ├── conf.d/
│   ├── html/
│   ├── logs/
│   └── ssl/
├── rabbitmq/
├── minio/
├── nacos/
└── elasticsearch/
```

---

## 4. Docker Compose Specification

- **File:** `compose.yml` (single file at project root)
- **Network:** `industrial-network` (bridge driver)
- **All services must have:**
  - HealthCheck
  - Restart policy (`unless-stopped`)
  - Volume mounts for data persistence
  - Environment variables from `.env`
  - Membership in `industrial-network`

---

## 5. Configuration File Standards

All configuration files must include a header:

```text
# ========================
# Industrial AI Hub — {Service} Configuration
# ========================
```

**File types:**
- MySQL: `my.cnf`
- Redis: `redis.conf`
- Nginx: `nginx.conf` (global) + `conf.d/` (per-site)

---

## 6. Environment Variables

- All secrets in `.env` (never committed)
- `.env.example` committed as template
- Variable naming: `UPPER_SNAKE_CASE`, service-name prefixed when ambiguous
- Application DB connection uses `${MYSQL_HOST}`, `${MYSQL_PORT}`, `${MYSQL_USER}`, `${MYSQL_PASSWORD}` with sensible defaults in `application.yml`
- `.env` is consumed by docker compose only; Spring Boot on host reads OS env vars, falling back to `application.yml` defaults

---

## 7. New Service Checklist

Every new infrastructure service must satisfy:

- [ ] Directory created under project root
- [ ] Config file(s) with standard header
- [ ] Data persistence (volume mount)
- [ ] Logging configured
- [ ] Added to `compose.yml` with HealthCheck
- [ ] Environment variables in `.env` + `.env.example`
- [ ] `industrial-network` membership
- [ ] Decision Log entry created
- [ ] This Baseline updated if conventions change

---

## 8. Current Stack

> **Config Status key:** Active = primary dev service, always expected running. Configured = defined in compose, on-demand. Not Implemented = reserved, no compose definition.

| Service | Version | Port | Config Status |
|---------|---------|------|---------------|
| MySQL | 8.4 | 3307 | Active |
| Redis Stack | 7.4 | 6379, 8001 | Configured |
| RabbitMQ | 4.0 | 5672, 15672 | Configured |
| Nacos | 2.4 | 8848, 9848 | Configured |
| MinIO | latest | 9000, 9001 | Configured |
| Elasticsearch | 8.17 | 9200, 9300 | Configured |
| Nginx | TBD | 80, 443 | Not Implemented |

> Runtime state is checked via `docker compose ps` and recorded in audit reports under `docs/reports/`. This table reflects intended configuration, not live status.

---

## 9. MySQL Port Policy (2026-07-17)

**本机 MySQL (3306) 与 Docker MySQL 端口冲突方案：**

| MySQL 实例 | 宿主机端口 | 用途 |
|-----------|-----------|------|
| 本机 MySQL 8.2 | 3306 | 本地开发（Unix socket 优先） |
| Docker standalone | 3307 | 项目开发测试 |
| Docker master | 13306 | 主从复制-写库 |
| Docker slave1 | 13307 | 主从复制-只读 |
| Docker slave2 | 13308 | 主从复制-只读 |

**原则：Docker MySQL 不占用 3306，预留本机 MySQL 独立运行空间。**

### Spring Boot 连接策略

Spring Boot 运行于宿主机，通过 Docker 端口映射连接：
- `application.yml` default: `jdbc:mysql://127.0.0.1:3307/reboot`
- 覆盖方式: `export MYSQL_HOST=... MYSQL_PORT=...` 或 IDE Run Configuration
- 如未来容器化部署: 将 `MYSQL_HOST` 改为 `mysql`（Docker service name），compose 自动注入

---

## 10. Redis Stack Modules (Activated)

| Module | 库文件 | 状态 |
|--------|-------|------|
| RedisBloom | redisbloom.so | ✅ Bloom Filter 已验证 |
| RediSearch | redisearch.so | ✅ 模块已加载 |
| RedisJSON | rejson.so | ✅ 模块已加载 |
| RedisTimeSeries | redistimeseries.so | ✅ 模块已加载 |
| RedisCompat | rediscompat.so | ✅ 模块已加载 |

---

## 11. Redis Sentinel (Configured)

Sentinel 配置已在 compose.yml 中定义（3 节点: 26379-26381），entrypoint 脚本解决了容器内 hostname 解析问题。
Decision Log: `0006-redis-sentinel-fixed.md`

---

## 12. Full Stack Registry (2026-07-20)

> Config Status: Active = always-on dev service | Configured = compose-defined, on-demand | Not Implemented = reserved, no compose entry

| Service | Version | Port(s) | Config Status |
|---------|---------|---------|---------------|
| MySQL | 8.4 | 3307 | Active |
| MySQL Master | 8.4 | 13306 | Configured |
| MySQL Slave1 | 8.4 | 13307 | Configured |
| MySQL Slave2 | 8.4 | 13308 | Configured |
| Redis Stack | 7.4.0 | 6379, 8001 | Configured |
| RabbitMQ | 4.0 | 5672, 15672 | Configured |
| Nacos | 2.4.3 | 8848, 9848 | Configured |
| MinIO | latest | 9000, 9001 | Configured |
| Elasticsearch | 8.17 | 9200, 9300 | Configured |
| Redis Sentinel (x3) | 7.4.0 | 26379-26381 | Configured |
