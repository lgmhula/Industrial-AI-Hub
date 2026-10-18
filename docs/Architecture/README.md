# Industrial AI Hub — Architecture Docs

> 最后更新：2026-10-18

## 文档索引

| 文档 | 覆盖范围 | 状态 |
|------|---------|------|
| [Application-Architecture.md](Application-Architecture.md) | 后端框架、组件、分层、API、数据库、路线后增强 | ✅ Active V2.6 |
| [System-Architecture.md](System-Architecture.md) | 系统全景图（mermaid 架构图 + 请求链路时序） | ✅ Active V2.2 |
| [API-Reference.md](API-Reference.md) | REST 端点清单与契约 | ✅ Active |
| [Database-ER.md](Database-ER.md) | 数据库 ER 图、索引设计、表规模 | ✅ Active |
| [Infrastructure-Baseline.md](Infrastructure-Baseline.md) | Docker、MySQL、Redis、网络、端口、Profile 与环境变量 | ✅ Active V1.8 |

## 决策日志

见 [../decision-log/](../decision-log/) 目录，按编号索引：

| 编号 | 主题 |
|------|------|
| 0001 | 统一 Compose 管理 |
| 0002 | Redis Stack 选型 |
| 0003 | Nacos HealthCheck |
| 0004 | MySQL 端口策略 |
| 0005 | MySQL 主从复制 |
| 0006 | Redis Sentinel 修复 |
| 0007 | JDK 25 LTS 作为唯一运行时 |
| 0008 | Spring Boot 3.5 替代独立 MyBatis |
| 0009 | 三层架构 Controller→Service→Mapper |
| 0010 | ApiResponse<T> 统一响应格式 |
| 0011 | 数据库设计审计 |
| 0012 | 数据库变更记录 |
| 0013 | Knife4j API 文档 |
| 0014 | Phase 3 中间件集成 |
| 0015 | 开发环境密钥 SSOT |
| 0016 | charset-safe init |
| 0017 | 分支策略 |
| 0018 | CI 与测试隔离 |
| 0019 | Flyway 迁移管理 |
| 0020 | 站点资源授权 |
| 0024 | RAG 向量库选型 |
| 0031 | AI 巡检日报推送架构 |
| 0038 | 活动告警聚合 |
| 0039 | Redis SSE Fan-out 与分布式限流 |
| 0040 | Qdrant 与 OpenAI-compatible Embedding |
| 0041 | 前端容器化、Loki 与 Grafana |
