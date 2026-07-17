# Decision 0006: Redis Sentinel — Deferred
**Date:** 2026-07-17 | **Status:** Pending

## Context
3 个 Sentinel 容器启动后立即 crash: `Can't resolve instance hostname 'redis'`.
虽然容器已加入 industrial-network，但 sentinel 启动时解析 Docker 服务名失败。

## Decision
保留 sentinel.conf 和 compose.yml 中配置（注释状态）。
待后续通过 entrypoint 脚本或 DNS 配置解决后重新激活。

## Alternative
使用 `hostname:` 显式声明或 init 脚本在 sentinel 启动前注入 IP。
