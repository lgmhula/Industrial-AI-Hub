#!/bin/sh
# ============================================================
# 01-init-db.sh — charset-safe 数据库初始化
#
# 背景（ADR 0016 / runbook 坑位 #11）：
#   官方 mysql 镜像 docker_process_sql() 不带 --default-character-set，
#   首次初始化时 UTF-8 的 SQL 文件被按非 utf8mb4 连接字符集解读，
#   中文种子数据以双重编码入库（2026-08-17 新设备部署实证）。
#
# 本脚本以显式 --default-character-set=utf8mb4 加载 init.sql + seed_test_data.sql。
# 字符集作为命令行参数传递，不依赖任何客户端配置文件（挂载权限无关，全平台可用）。
# 依赖 entrypoint 的 set -e：任一 SQL 加载失败即容器启动失败（fail-fast）。
# ============================================================
set -e

export MYSQL_PWD="${MYSQL_ROOT_PASSWORD:-}"
SOCKET=/var/run/mysqld/mysqld.sock

for sql in /init-sql/init.sql /init-sql/seed_test_data.sql; do
    echo "[init-db] loading ${sql} (--default-character-set=utf8mb4)"
    mysql -uroot --socket="${SOCKET}" --default-character-set=utf8mb4 "${MYSQL_DATABASE}" < "${sql}"
done

unset MYSQL_PWD
