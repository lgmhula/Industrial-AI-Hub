# Industrial AI Hub — Architecture Docs

> 最后更新: 2026-07-21

## 文档索引

| 文档 | 覆盖范围 | 状态 |
|------|---------|------|
| [Application-Architecture.md](Application-Architecture.md) | 后端框架、组件、分层、数据流 | ✅ Active |
| [Infrastructure-Baseline.md](Infrastructure-Baseline.md) | Docker、MySQL、Redis、网络、端口 | ✅ Active |

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
