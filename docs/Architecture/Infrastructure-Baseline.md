# Infrastructure Baseline V1

> **Status:** Active  
> **Version:** 1.0  
> **Last Updated:** 2026-07-16  
> **Governs:** All infrastructure decisions for Industrial AI Hub

---

## 1. Core Principles

- **Unified Specification** — One compose file, one network, one .env
- **Configuration as Code** — All configs versioned in Git
- **Separation of Concerns** — config / data / logs in separate directories
- **Decision Log** — Every architectural choice is documented

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
├── frontend/             # Web frontend
├── docs/
│   ├── Architecture/
│   │   └── Infrastructure-Baseline.md
│   └── decision-log/
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

| Service | Version | Port | Status |
|---------|---------|------|--------|
| MySQL | 8.4 | 3306 | Active |
| Redis Stack | 7.4 | 6379, 8001 | Active |
| RabbitMQ | 4.0 | 5672, 15672 | Configured |
| Nacos | 2.4 | 8848, 9848 | Configured |
| MinIO | latest | 9000, 9001 | Configured |
| Elasticsearch | 8.17 | 9200, 9300 | Configured |
| Nginx | TBD | 80, 443 | Planned |

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

## 11. Redis Sentinel (Pending)

Sentinel 配置已就绪（`redis/sentinel.conf`），但 Docker 容器内 hostname 解析问题
导致 `Can't resolve instance hostname`。暂保留配置文件，后续通过 entrypoint 脚本解决。
Decision Log: `0004-redis-sentinel-pending.md`

---

## 12. Updated Stack (2026-07-17)

| Service | Version | Port(s) | Status |
|---------|---------|---------|--------|
| MySQL | 8.4 | 3307 | Active |
| MySQL Master | 8.4 | 13306 | Active |
| MySQL Slave1 | 8.4 | 13307 | Active |
| MySQL Slave2 | 8.4 | 13308 | Active |
| Redis Stack | 7.4.0 | 6379, 8001 | Active |
| RabbitMQ | 4.0 | 5672, 15672 | Active |
| Nacos | 2.4.3 | 8848, 9848 | Active |
| MinIO | latest | 9000, 9001 | Active |
| Elasticsearch | 8.17 | 9200, 9300 | Active |
| Redis Sentinel (x3) | 7.4.0 | 26379-26381 | Pending |
