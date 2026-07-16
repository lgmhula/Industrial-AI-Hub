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
