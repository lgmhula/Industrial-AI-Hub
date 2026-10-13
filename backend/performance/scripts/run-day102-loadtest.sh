#!/usr/bin/env bash
#
# Day 102 — JMeter 压测执行脚本（非 GUI 模式）
#
# 用法:
#   ./run-day102-loadtest.sh                       # 默认 http://127.0.0.1:8081
#   BASE_URL=http://127.0.0.1:8080 ./run-day102-loadtest.sh
#   READ_THROUGHPUT=6000 ./run-day102-loadtest.sh   # 临时提高读路径速率（会触发 429）
#
# 输出:
#   results/day102-<ts>.jtl      原始结果（每请求一行）
#   results/day102-<ts>-report/  JMeter HTML dashboard
#
# 设计约束（务必先读，改参数前看 performance/README.md）:
#   1. 登录 IP 限流 = 30 次 / 5 分钟 → 脚本只登录 1 次，token 由 -Jtoken 注入；
#   2. 通用限流 RateLimitInterceptor = 50 req/s（按 path 分桶）→ 读路径默认压到 40 req/s；
#   3. 写路径默认 20 req/s 且 TEMPERATURE=25.0（阈值 0~40 之外才报警）→ 不产生报警噪音。
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PERF_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
JMX="${PERF_DIR}/jmeter/day102-load-test.jmx"

BASE_URL="${BASE_URL:-http://127.0.0.1:8081}"
JMETER_HOME="${JMETER_HOME:-$HOME/tools/apache-jmeter-5.6.3}"
JMETER_BIN="${JMETER_HOME}/bin/jmeter"
LOGIN_USER="${LOGIN_USER:-admin}"
LOGIN_PASS="${LOGIN_PASS:-admin123}"

READ_THROUGHPUT="${READ_THROUGHPUT:-2400}"     # 每分钟；2400/min = 40 req/s（限流上限 50 req/s）
READ_DURATION="${READ_DURATION:-60}"
WRITE_THROUGHPUT="${WRITE_THROUGHPUT:-1200}"   # 1200/min = 20 req/s
WRITE_DURATION="${WRITE_DURATION:-60}"
BURST_DURATION="${BURST_DURATION:-30}"

TS="$(date +%Y%m%d-%H%M%S)"
OUT_DIR="${PERF_DIR}/results"
JTL="${OUT_DIR}/day102-${TS}.jtl"
REPORT="${OUT_DIR}/day102-${TS}-report"

# 速率覆盖：JMX 里 ConstantThroughputTimer 的 doubleProp 必须是字面数字（XStream 按 double 解析，
# 不接受 ${} 表达式），所以这里用 sed 生成临时副本，只在速率与默认值不同时才做。
EFFECTIVE_JMX="${JMX}"
TMP_JMX=""
if [ "${READ_THROUGHPUT}" != "2400" ] || [ "${WRITE_THROUGHPUT}" != "1200" ]; then
  TMP_JMX="$(mktemp -t day102-XXXXXX.jmx)"
  sed -e "s|<value>2400.0</value>|<value>${READ_THROUGHPUT}.0</value>|" \
      -e "s|Throttle 2400/min (40 rps)|Throttle ${READ_THROUGHPUT}/min|" \
      -e "s|<value>1200.0</value>|<value>${WRITE_THROUGHPUT}.0</value>|" \
      -e "s|Throttle 1200/min (20 rps)|Throttle ${WRITE_THROUGHPUT}/min|" \
      "${JMX}" > "${TMP_JMX}"
  EFFECTIVE_JMX="${TMP_JMX}"
  echo "[throttle] 覆盖速率 读=${READ_THROUGHPUT}/min 写=${WRITE_THROUGHPUT}/min（临时 JMX: ${TMP_JMX}）"
fi
trap '[ -n "${TMP_JMX}" ] && rm -f "${TMP_JMX}"' EXIT

# ---- 0. 前置检查 -----------------------------------------------------------
[ -x "${JMETER_BIN}" ] || { echo "ERROR: JMeter 不存在: ${JMETER_BIN}（设 JMETER_HOME 覆盖）" >&2; exit 1; }
[ -f "${JMX}" ]        || { echo "ERROR: JMX 不存在: ${JMX}" >&2; exit 1; }

# JMeter 5.6.3 官方支持到 Java 17/21；本机默认 JDK 25 不保证（会有 Unsafe 弃用告警）。
# 优先显式指定 > 本机 JDK 17 > 沿用现有 JAVA_HOME。
if [ -n "${JMETER_JAVA_HOME:-}" ] && [ -x "${JMETER_JAVA_HOME}/bin/java" ]; then
  JAVA_HOME="${JMETER_JAVA_HOME}"
elif JH17="$(/usr/libexec/java_home -v 17 2>/dev/null)" && [ -x "${JH17}/bin/java" ]; then
  JAVA_HOME="${JH17}"
fi
if [ -n "${JAVA_HOME}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
  export JAVA_HOME
  echo "[env] JAVA_HOME=${JAVA_HOME}"
fi
"${JMETER_BIN}" --version >/dev/null 2>&1 || { echo "ERROR: JMeter 无法启动（Java 版本？）" >&2; exit 1; }

HOST_PORT="${BASE_URL#*://}"
HOST="${HOST_PORT%%:*}"
PORT="${HOST_PORT##*:}"
[ "${PORT}" = "${HOST}" ] && PORT=80

# ---- 1. 健康检查 -----------------------------------------------------------
health="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "${BASE_URL}/actuator/health" || true)"
[ "${health}" = "200" ] || { echo "ERROR: ${BASE_URL}/actuator/health 返回 ${health}（目标未就绪）" >&2; exit 1; }
echo "[target] ${BASE_URL} health=200"

# ---- 2. 登录取 token（1 次，避开 30 次/5 分钟 IP 限流） ----------------------
LOGIN_RESP="$(curl -s --max-time 15 -X POST -H 'Content-Type: application/json' \
  -d "{\"username\":\"${LOGIN_USER}\",\"password\":\"${LOGIN_PASS}\"}" \
  "${BASE_URL}/api/auth/login")"
TOKEN="$(printf '%s' "${LOGIN_RESP}" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("data",""))' 2>/dev/null || true)"
[ -n "${TOKEN}" ] || { echo "ERROR: 登录取 token 失败: ${LOGIN_RESP}" >&2; exit 1; }
echo "[auth] token 长度 ${#TOKEN}"

# ---- 3. 数据水位（用于压测后精确清理写入行） -------------------------------
mkdir -p "${OUT_DIR}"
echo "[watermark] device_data max(id) 见 ${OUT_DIR}/day102-${TS}-watermark.txt"
if command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -qx mysql; then
  MYSQL_PWD_VALUE="$(grep -m1 '^MYSQL_ROOT_PASSWORD=' "${PERF_DIR}/../../.env" 2>/dev/null | cut -d= -f2- || true)"
  if [ -n "${MYSQL_PWD_VALUE}" ]; then
    docker exec mysql mysql -uroot -p"${MYSQL_PWD_VALUE}" -N \
      -e "USE reboot; SELECT COALESCE(MAX(id),0) FROM device_data;" 2>/dev/null \
      | tr -d '\r' > "${OUT_DIR}/day102-${TS}-watermark.txt" || true
    echo "[watermark] baseline=$(cat "${OUT_DIR}/day102-${TS}-watermark.txt" 2>/dev/null)"
  fi
fi

# ---- 4. 执行压测 -----------------------------------------------------------
echo "[run] jmeter -n -t ${EFFECTIVE_JMX##*/} -l ${JTL##*/} ..."
"${JMETER_BIN}" -n \
  -t "${EFFECTIVE_JMX}" \
  -l "${JTL}" \
  -e -o "${REPORT}" \
  -Jhost="${HOST}" -Jport="${PORT}" -Jprotocol="${BASE_URL%%://*}" \
  -Jtoken="${TOKEN}" \
  -Jsample_variables=traceId \
  -JreadDuration="${READ_DURATION}" \
  -JwriteDuration="${WRITE_DURATION}" \
  -JburstDuration="${BURST_DURATION}" \
  -j "${OUT_DIR}/day102-${TS}-jmeter.log"

echo
echo "================ Day 102 压测完成 ================"
echo "JTL     : ${JTL}"
echo "Report  : ${REPORT}/index.html"
echo "watermark: ${OUT_DIR}/day102-${TS}-watermark.txt (清理见 performance/README.md)"
