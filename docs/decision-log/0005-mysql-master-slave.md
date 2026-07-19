# Decision 0005: MySQL Master-Slave Replication
**Date:** 2026-07-17 | **Status:** Accepted

## Context
Industrial AI Hub 需要读写分离架构：主库写入设备数据，从库处理查询和分析。

## Decision
采用 MySQL 8.4 原生异步复制，1 Master + 2 Slaves。
- Master: port 13306, server-id=1, binlog ROW format
- Slave1: port 13307, server-id=2, read-only
- Slave2: port 13308, server-id=3, read-only
复制用户: repl@'%'

## Consequences
- 3 容器统一加入 industrial-network
- 独立 cnf 文件在 mysql/ms-conf/
- 数据持久化: mysql/ms-data/{master,slave1,slave2}/
