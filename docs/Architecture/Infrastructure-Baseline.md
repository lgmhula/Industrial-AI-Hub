# Infrastructure Baseline V1.2

> **Status:** Active  
> **Version:** 1.2
> **Updated:** 2026-08-03
> **Based on Commit:** Day 042
> **Governs:** All infrastructure decisions for Industrial AI Hub

---

## 1. Core Principles

- **Unified Specification** — One compose file, one network, one .env
- **Configuration as Code** — All configs versioned in Git
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
