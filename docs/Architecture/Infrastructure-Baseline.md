# Infrastructure Baseline V1.6

> **Status:** Active  
> **Version:** 1.6
> **Updated:** 2026-10-14
> **Based on Commit:** Day 104 (Phase 5)
> **Governs:** All infrastructure decisions for Industrial AI Hub

---

## 1. Core Principles

- **Unified Specification** — One compose file, one network, one .env
- **Configuration as Code** — All configs versioned in Git
- **Secrets SSOT** — `.env` is the single source of truth for local dev secrets
  (ADR 0015): dev loads it via `spring.config.import`; Compose interpolates it;
  test uses isolated placeholders; prod injects via container env. No secret copies
  in IDE run configurations or `application*.yml`.
- **Separation of Concerns** — config / data / logs in separate directories
- **Decision Log** — Every architectural choice is documented
- **Config vs Runtime** — This document tracks **configuration state**. Runtime via `docker compose ps`.

---

## 2. Service Registry

| Service | Version | Port(s) | Config Status | Notes |
|---------|---------|---------|:---:|------|
| MySQL | 8.4 | 3307 | Active | 主开发数据库 |
| MySQL Master | 8.4 | 13306 | Configured | 主从-写库（full profile） |
| MySQL Slave1 | 8.4 | 13307 | Configured | 主从-只读1（full profile） |
| MySQL Slave2 | 8.4 | 13308 | Configured | 主从-只读2（full profile） |
| Redis Stack | 7.4.0 | 6379, 8001 | Active | Phase 3 已集成 |
| Redis Sentinel (×3) | 7.4.0 | 26379-81 | Configured | 高可用预留（full profile） |
| RabbitMQ | 4.0 | 5672, 15672 | Active | Phase 3 已集成 |
| Nacos | 2.4.3 | 8848, 9848 | Configured | 预留基础设施：不纳入路线/不启动/无业务依赖 |
| MinIO | RELEASE.2025-09-07 | 9000, 9001 | Configured | Phase 5 预留（对象存储/RAG 文档） |
| Elasticsearch | 8.17 | 9200, 9300 | Configured | ELK 日志（Day 101，可选） |
| EMQX | 5.8.9 | 1883, 8083, 18083 | Active | Phase 5 MQTT Broker：PLC/模拟设备接入，ADR 0033；backend 容器自 v1.4 注入 `MQTT_ENABLED=true/MQTT_HOST=emqx` 并等待 healthy（Day 96, ADR 0034） |
| Prometheus | 2.55.1 | 9090 | Active | Day 100 指标观测（ADR 0036）：**双抓取目标并存（v1.6）**——`industrial-ai-hub-backend`（宿主机 `host.docker.internal:8080`）+ `industrial-ai-hub-backend-container`（服务名 `backend:8080`），哪种部署模式在线哪个 up，`prometheus/prometheus.yml` bind mount + `prometheus-data` named volume |
| Backend | Boot 3.5.0 / JDK 25 | 8080→`${BACKEND_HOST_PORT:-8080}` | Active | Spring Boot 应用容器（multi-stage Dockerfile + non-root + HEALTHCHECK）；v1.6：日志卷 `backend-logs`（logback FILE/ERROR_FILE，Day 101）挂 `/app/logs`、独立 MQTT clientId（默认 `iah-backend-container`，与宿主机实例并存不互踢）、`rate.limit.*` 三项经环境变量注入（ADR 0037）、DeepSeek/MCP 传输门变量透传、`TZ=Asia/Shanghai` |

### 2.1 Profile 分级（compose v1.6 实测）

| 层级 | 启动方式 | 服务 | 用途 |
|------|---------|------|------|
| core（默认） | `docker compose up -d` | mysql / redis / rabbitmq / emqx / prometheus / backend（6） | 日常开发全栈 |
| full（可选） | `docker compose --profile full up -d` | + nacos / minio / elasticsearch / mysql-master / mysql-slave1 / mysql-slave2 / redis-sentinel1-3（共 **15**） | 预留基础设施一键拉起（Day 104 实测 15/15 healthy，~75s 收敛；验证后按需 stop） |

---

## 3. MySQL Port Policy

| MySQL 实例 | 宿主机端口 | 用途 |
|-----------|-----------|------|
| 本机 MySQL 8.2 | 3306 | 本地开发 (Unix socket) |
| Docker standalone | 3307 | 项目 dev/test |
| Docker master | 13306 | 读写分离-写 |
| Docker slave1 | 13307 | 读写分离-读 |
| Docker slave2 | 13308 | 读写分离-读 |

---

## 4. Spring Boot 部署模型

- **宿主机模式**: `application.yml` defaults `127.0.0.1:3307`
- **容器化模式**: 将 `MYSQL_HOST` 改为 `mysql`，compose 注入
- **Profiles**: dev（默认）/ prod（Phase 1 实现）

---

## 5. 数据库约束

8 个 CHECK 约束全覆盖 device_type/status/alarm_level/alarm_status/data_type/operation_type/target_type/user_status。

---

## 6. 审计记录

| 版本 | 日期 | 内容 |
|------|------|------|
| V1.0 | 2026-07-16 | 初始基线 |
| V1.1 | 2026-07-20 | 端口+Sentinel+连接策略 |
| V1.2 | 2026-08-03 | Day 042 同步 |
| V1.3 | 2026-09-04 | Phase 5 Day 93：新增 EMQX 5.8.9 MQTT Broker 服务（1883/8083/18083），compose.yml 同步 v1.3 |
| V1.4 | 2026-09-05 | Phase 5 Day 96：backend MQTT Listener 容器启用（compose v1.4 注入 MQTT_ENABLED/MQTT_HOST/MQTT_PORT + depends_on EMQX healthy），ADR 0034 |
| V1.5 | 2026-10-13 | Phase 5 Day 100：新增 Prometheus 2.55.1 指标观测服务（9090，抓宿主机后端 `/actuator/prometheus`），compose v1.5 + `prometheus/prometheus.yml` + `prometheus-data` volume，ADR 0036 |
| V1.6 | 2026-10-14 | Phase 5 Day 104 完整编排：backend 日志卷 `backend-logs` + Dockerfile 预建 `/app/logs`（Day 101 日志体系容器化落盘）；容器 MQTT clientId 独立（`MQTT_CLIENT_ID` 可配，Day 101 互踢坑位）；`rate.limit.*` / DeepSeek / MCP 变量经环境注入；Prometheus 双抓取目标（宿主机 + 容器模式并存）；`BACKEND_HOST_PORT` 可配端口避让宿主机 8080；**实测 `--profile full` 15 服务一键启动全部 healthy**（~75s 收敛）；发现并记录：MinIO 2025-09 起 Docker Hub 社区镜像 tag 已不可拉取（本地 `latest` 即 `RELEASE.2025-09-07T16-13-09Z`，新环境需自 quay.io 或离线导入）；compose v1.6；同日结清 TD-038（畸形 JSON 400） |
