# Decision 0004: MySQL Port Conflict Resolution
**Date:** 2026-07-17 | **Status:** Accepted

## Context
本机 MySQL 8.2.0（PID 594）与 Docker MySQL 8.4 均占用 3306 端口。
虽然当前未发生实际冲突（本机 MySQL 仅监听 Unix socket），但存在风险。

## Decision
Docker standalone MySQL 从 3306 迁移至 3307。
3306 预留给本机 MySQL，确保两者可同时运行。

## Consequences
- compose.yml 端口: `3307:3306`
- .env connection string 需使用 3307
- 本机 MySQL 保持 3306 不变
