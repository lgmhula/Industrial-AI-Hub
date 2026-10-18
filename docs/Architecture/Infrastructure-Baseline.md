# Infrastructure Baseline V1.8

> **Status:** Active  
> **Version:** 1.8
> **Updated:** 2026-10-18
> **Based on Commit:** `20122c8` + 路线后增强（未移动 `v3.0-final`）
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
| MinIO | RELEASE.2025-09-07T16-13-09Z | 9000, 9001 | Configured | Phase 5 预留（对象存储/RAG 文档）；本地镜像 digest `sha256:14cea493d9a34af32f524e538b8346cf79f3321eff8e708c1e2960462bd8936e`；Docker Hub 该 tag 已下架，新环境需 quay.io 或离线导入 |
| Elasticsearch | 8.17 | 9200, 9300 | Configured | ELK 日志（Day 101，可选） |
| EMQX | 5.8.9 | 1883, 8083, 18083 | Active | Phase 5 MQTT Broker：PLC/模拟设备接入，ADR 0033；backend 容器自 v1.4 注入 `MQTT_ENABLED=true/MQTT_HOST=emqx` 并等待 healthy（Day 96, ADR 0034） |
| Prometheus | 2.55.1 | 9090 | Active | Day 100 指标观测（ADR 0036）：**双抓取目标并存（v1.6）**——`industrial-ai-hub-backend`（宿主机 `host.docker.internal:8080`）+ `industrial-ai-hub-backend-container`（服务名 `backend:8080`），哪种部署模式在线哪个 up，`prometheus/prometheus.yml` bind mount + `prometheus-data` named volume |
| Backend | Boot 3.5.0 / JDK 25 | 8080→`${BACKEND_HOST_PORT:-8080}` | Active | Spring Boot 应用容器（multi-stage Dockerfile + non-root + HEALTHCHECK）；日志卷 `backend-logs` 挂 `/app/logs`，`backend-data` 挂 `/app/data`（`SimpleVectorStore` 跨容器重建保留）；独立 MQTT clientId（默认 `iah-backend-container`）；注入 `RATE_LIMIT_*`、`RAG_*` 与 `QDRANT_*`（ADR 0039/0040），DeepSeek/MCP 传输门变量透传，`TZ=Asia/Shanghai` |
| Frontend | Node 22 build / Nginx 1.27 | 3000→`${FRONTEND_HOST_PORT:-3000}` | Active | Vue 静态构建 + Nginx（ADR 0041）；`/api/` 反代 `backend:8080`，`/api/push/` 关闭缓冲/缓存并延长 SSE 超时；`/healthz` 供容器健康检查 |
| Qdrant | 1.12.4 | 6333, 6334 | Configured | `full` profile，可选 RAG 向量库（ADR 0040）；默认 RAG 仍为 `simple + hash`，切换 `RAG_VECTOR_STORE=qdrant` 时使用 |
| Loki | 3.2.1 | 3100 | Configured | `observability` / `full` profile；接收 Promtail 推送的后端文件日志（ADR 0041） |
| Promtail | 3.2.1 | — | Configured | `observability` / `full` profile；只读 `backend-logs` named volume，positions 独立持久化 |
| Grafana | 11.3.0 | 3001 | Configured | `observability` / `full` profile；预置 Prometheus + Loki 数据源与项目 dashboard；管理密码由 `GRAFANA_ADMIN_PASSWORD` 注入 |

### 2.1 Profile 分级（compose v1.8）

| 层级 | 启动方式 | 服务 | 用途 |
|------|---------|------|------|
| core（默认） | `docker compose up -d` | mysql / redis / rabbitmq / emqx / prometheus / backend / frontend（**7**） | 日常开发与静态前端交付 |
| observability（可选） | `docker compose --profile observability up -d` | core + loki / promtail / grafana（**10**） | 集中日志与 Grafana 观测 |
| full（可选） | `docker compose --profile full up -d` | core + observability + qdrant / nacos / minio / elasticsearch / mysql 主从×3 / redis sentinel×3（共 **20**） | 全量基础设施验证；不代表业务已拆微服务 |

> v1.7 的 core 6 / full 15 口径保留为 `v3.0-final` 历史事实。v1.8 新增 frontend、Qdrant 与 observability 服务，实际数量以 `docker compose config --services` 为准。

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
| V1.7 | 2026-10-18 | Phase 5 Day 111 发布预演：运行镜像预建并授权 `/app/data`、`/app/logs` 给非 root `app` 用户；compose 新增 `backend-data` named volume 挂载 `/app/data`，使 `SimpleVectorStore` 文件跨容器重建保留；容器重建后持久化探针通过，完整日志无新的 `/app/data` 创建失败 WARN；full profile 15/15 healthy，Flyway 14 个活动迁移/V15 校验通过；compose v1.7 |
| V1.8 | 2026-10-18 | 路线后增强：core 新增 `frontend`（Vue + Nginx，ADR 0041）；full 新增 Qdrant 1.12.4（ADR 0040）；新增 `observability`/`full` 可选 Loki 3.2.1 / Promtail 3.2.1 / Grafana 11.3.0（ADR 0041）；backend 注入 `RATE_LIMIT_MODE/IDENTITY_PERMITS`、`RAG_*`、`QDRANT_*`；MinIO 固定镜像 digest 已核实；`docker compose config` 实测 core 7 / observability 10 / full 20；Schema V16（ADR 0038）；compose v1.8 |
