#!/usr/bin/env bash
# ==========================================
# Industrial AI Hub — Dev Demo Seed（仅限开发环境，生产环境禁止执行）
# 用法: ./scripts/seed-dev.sh [数据库名]
#
# 作用: 幂等灌入演示/测试种子数据（backend/src/main/resources/db/seed/dev/seed_demo_data.sql）
# 连接: 参数来自根目录 .env（ADR 0015 密钥 SSOT：MYSQL_HOST/PORT/USER/PASSWORD/DATABASE）
# 特性: 可安全重复执行（按业务键 NOT EXISTS 守卫，不产生重复数据）
# 安全: 本脚本不会被任何启动流程自动调用；生产部署（compose prod profile）不执行它
# ==========================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
SEED_SQL="$ROOT_DIR/backend/src/main/resources/db/seed/dev/seed_demo_data.sql"

# 1. 读取根目录 .env（密钥/连接信息唯一事实源，ADR 0015）
if [ ! -f "$ROOT_DIR/.env" ]; then
    echo "错误：未找到 $ROOT_DIR/.env（请先 cp .env.example .env 并填写，见 docs/SETUP.md）" >&2
    exit 1
fi
set -a
# shellcheck disable=SC1091
source "$ROOT_DIR/.env"
set +a

if [ -z "${MYSQL_PASSWORD:-}" ] && [ -z "${MYSQL_ROOT_PASSWORD:-}" ]; then
    echo "错误：.env 缺少 MYSQL_PASSWORD / MYSQL_ROOT_PASSWORD" >&2
    exit 1
fi

MYSQL_BIN="${MYSQL_BIN:-mysql}"
DB="${1:-${MYSQL_DATABASE:-reboot}}"
HOST="${MYSQL_HOST:-127.0.0.1}"
PORT="${MYSQL_PORT:-3307}"
USER="${MYSQL_USER:-root}"
PASS="${MYSQL_PASSWORD:-$MYSQL_ROOT_PASSWORD}"

if [ ! -f "$SEED_SQL" ]; then
    echo "错误：找不到 seed SQL：$SEED_SQL" >&2
    exit 1
fi

echo "==> 灌入 Demo 数据：$HOST:$PORT/$DB（幂等，可重复执行）"
"$MYSQL_BIN" -h "$HOST" -P "$PORT" -u "$USER" -p"$PASS" \
    --default-character-set=utf8mb4 "$DB" < "$SEED_SQL"

echo "==> 完成。当前数据量："
"$MYSQL_BIN" -h "$HOST" -P "$PORT" -u "$USER" -p"$PASS" -N \
    --default-character-set=utf8mb4 "$DB" -e \
    'SELECT CONCAT("users          = ", COUNT(*)) FROM `user`;
     SELECT CONCAT("devices        = ", COUNT(*)) FROM device;
     SELECT CONCAT("alarms         = ", COUNT(*)) FROM alarm;
     SELECT CONCAT("device_data    = ", COUNT(*)) FROM device_data;
     SELECT CONCAT("operation_logs = ", COUNT(*)) FROM operation_log;'
