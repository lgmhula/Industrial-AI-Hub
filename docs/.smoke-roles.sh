#!/usr/bin/env bash
# Industrial AI Hub — 多角色权限冒烟测试
# 自动获取 tokens；支持通过 stdin 安全解析 JSON，避免引号嵌套问题
set -u

BASE="${BASE:-http://localhost:8080}"

get_token() {
  curl -sS -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
    -d "{\"username\":\"$1\",\"password\":\"Test123456\"}" | \
    python3 -c "import sys,json; print(json.load(sys.stdin).get('data',''))"
}

# 自动生成 tokens（如外部未传入）
if [ -z "${ADMIN_TOKEN:-}" ] || [ ${#ADMIN_TOKEN} -lt 50 ]; then
  ADMIN_TOKEN=$(get_token "admin")
fi
if [ -z "${OPS_TOKEN:-}" ] || [ ${#OPS_TOKEN} -lt 50 ]; then
  OPS_TOKEN=$(get_token "operator01")
fi
if [ -z "${VIEW_TOKEN:-}" ] || [ ${#VIEW_TOKEN} -lt 50 ]; then
  VIEW_TOKEN=$(get_token "viewer01")
fi

PASS=0; FAIL=0; TOTAL=0
PASS_LIST=""; FAIL_LIST=""

# 安全解析 JSON：从 stdin 读 JSON 避免引号嵌套问题
parse_code() {
  python3 -c "import sys,json
try:
  d=json.load(sys.stdin)
  print(d.get('code','NA'))
except Exception:
  print('PARSEFAIL')"
}

chk() {
  local desc="$1" expected_code="$2" resp="$3"
  TOTAL=$((TOTAL+1))
  local got_code
  got_code=$(echo "$resp" | parse_code)
  if [ "$got_code" = "$expected_code" ]; then
    PASS=$((PASS+1))
    PASS_LIST+="  ✅ $desc -> $got_code"$'\n'
  else
    FAIL=$((FAIL+1))
    local snippet
    snippet=$(echo "$resp" | tr -d '\n' | cut -c1-180)
    FAIL_LIST+="  ❌ $desc -> expected=$expected_code got=$got_code resp=$snippet"$'\n'
  fi
}

echo "================================"
echo " 公开接口（无 Token，需放行）"
echo "================================"
resp=$(curl -sS -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" -d '{"username":"viewer01","password":"Test123456"}'); chk "公开: 登录 viewer01 成功" 200 "$resp"
resp=$(curl -sS -X POST "$BASE/api/auth/register" -H "Content-Type: application/json" -d '{"username":"nobody_xx","password":"Xy123456!","email":"n@x.com"}'); chk "公开: 注册默认关闭(403 或 401)禁止" 403 "$resp"
resp=$(curl -sS -X GET "$BASE/api/devices" -H "Content-Type: application/json"); chk "公开: 未登录访问设备列表 -> 401" 401 "$resp"
resp=$(curl -sS -X GET "$BASE/actuator/health"); http_code=$(curl -sS -o /dev/null -w "%{http_code}" "$BASE/actuator/health"); 
if [ "$http_code" = "200" ]; then PASS=$((PASS+1)); PASS_LIST+="  ✅ Actuator health -> 200"$'\n'; TOTAL=$((TOTAL+1))
else FAIL=$((FAIL+1)); FAIL_LIST+="  ❌ Actuator health -> $http_code"$'\n'; TOTAL=$((TOTAL+1)); fi

echo "================================"
echo " VIEWER（viewer01）— 只读权限"
echo "================================"
T="$VIEW_TOKEN"
resp=$(curl -sS -X GET "$BASE/api/devices?page=1&size=5" -H "Authorization: Bearer $T"); chk "VIEWER: 设备列表 -> 200" 200 "$resp"
resp=$(curl -sS -X GET "$BASE/api/alarms?page=1&size=5" -H "Authorization: Bearer $T"); chk "VIEWER: 告警列表 -> 200" 200 "$resp"
resp=$(curl -sS -X GET "$BASE/api/device-data/device/1/stats?dataType=TEMPERATURE" -H "Authorization: Bearer $T"); chk "VIEWER: 设备数据统计(TEMPERATURE) -> 200" 200 "$resp"
resp=$(curl -sS -X GET "$BASE/api/sites" -H "Authorization: Bearer $T"); chk "VIEWER: 站点列表 -> 200" 200 "$resp"
resp=$(curl -sS -X POST "$BASE/api/devices" -H "Authorization: Bearer $T" -H "Content-Type: application/json" -d '{"deviceCode":"V-XX-999","deviceName":"越权设备","deviceType":"SENSOR","siteId":1}'); chk "VIEWER: 创建设备 -> 403(越权)" 403 "$resp"
resp=$(curl -sS -X DELETE "$BASE/api/devices/1" -H "Authorization: Bearer $T"); chk "VIEWER: 删除设备 -> 403(越权)" 403 "$resp"
resp=$(curl -sS -X GET "$BASE/api/users" -H "Authorization: Bearer $T"); chk "VIEWER: 访问用户管理 -> 403(越权)" 403 "$resp"
resp=$(curl -sS -X GET "$BASE/api/roles" -H "Authorization: Bearer $T"); chk "VIEWER: 访问角色管理 -> 403(越权)" 403 "$resp"
resp=$(curl -sS -X GET "$BASE/api/operation-logs?page=1&size=5" -H "Authorization: Bearer $T"); chk "VIEWER: 访问操作日志 -> 403(越权)" 403 "$resp"

echo "================================"
echo " OPERATOR（operator01）— 可写设备，不可管用户/角色"
echo "================================"
T="$OPS_TOKEN"
resp=$(curl -sS -X GET "$BASE/api/devices?page=1&size=5" -H "Authorization: Bearer $T"); chk "OPERATOR: 设备列表 -> 200" 200 "$resp"
resp=$(curl -sS -X POST "$BASE/api/devices" -H "Authorization: Bearer $T" -H "Content-Type: application/json" -d '{"deviceCode":"OP-TEST-01","deviceName":"OP测试设备01","deviceType":"SENSOR","siteId":1}'); chk "OPERATOR: 创建设备 -> 200" 200 "$resp"
resp=$(curl -sS -X PUT "$BASE/api/devices/1" -H "Authorization: Bearer $T" -H "Content-Type: application/json" -d '{"deviceCode":"DEV-TEMP-001","deviceName":"OP更新设备","deviceType":"SENSOR","siteId":1}'); chk "OPERATOR: 更新设备 -> 200" 200 "$resp"
resp=$(curl -sS -X PUT "$BASE/api/alarms/1/acknowledge" -H "Authorization: Bearer $T"); chk "OPERATOR: 确认告警 -> 200" 200 "$resp"
resp=$(curl -sS -X DELETE "$BASE/api/devices/1" -H "Authorization: Bearer $T"); chk "OPERATOR: 删除设备 -> 403(需 ADMIN)" 403 "$resp"
resp=$(curl -sS -X GET "$BASE/api/users" -H "Authorization: Bearer $T"); chk "OPERATOR: 访问用户管理 -> 403(越权)" 403 "$resp"
resp=$(curl -sS -X GET "$BASE/api/roles" -H "Authorization: Bearer $T"); chk "OPERATOR: 访问角色管理 -> 403(越权)" 403 "$resp"

echo "================================"
echo " ADMIN（admin）— 所有权限，重点 pc_hula 新端点"
echo "================================"
T="$ADMIN_TOKEN"
# 核心读取
resp=$(curl -sS -X GET "$BASE/api/devices?page=1&size=5" -H "Authorization: Bearer $T"); chk "ADMIN: 设备列表 -> 200" 200 "$resp"
resp=$(curl -sS -X GET "$BASE/api/users?page=1&size=5" -H "Authorization: Bearer $T"); chk "ADMIN: 用户列表 -> 200" 200 "$resp"
resp=$(curl -sS -X GET "$BASE/api/users?page=1&size=5&keyword=viewer" -H "Authorization: Bearer $T"); chk "ADMIN: 用户 keyword 搜索(pc_hula) -> 200" 200 "$resp"
resp=$(curl -sS -X GET "$BASE/api/users/4" -H "Authorization: Bearer $T"); chk "ADMIN: 按ID查用户(viewer01) -> 200" 200 "$resp"
resp=$(curl -sS -X GET "$BASE/api/operation-logs?page=1&size=5" -H "Authorization: Bearer $T"); chk "ADMIN: 操作日志 -> 200" 200 "$resp"

# === pc_hula 新增：角色管理 6 端点 ===
resp=$(curl -sS -X GET "$BASE/api/roles" -H "Authorization: Bearer $T"); chk "ADMIN(pc_hula): 角色列表 -> 200" 200 "$resp"
resp=$(curl -sS -X GET "$BASE/api/roles/1" -H "Authorization: Bearer $T"); chk "ADMIN(pc_hula): 按ID查角色 -> 200" 200 "$resp"
resp=$(curl -sS -X POST "$BASE/api/roles" -H "Authorization: Bearer $T" -H "Content-Type: application/json" -d '{"roleCode":"QA_ENGINEER","roleName":"质量工程师","description":"pc_hula smoke test"}'); chk "ADMIN(pc_hula): 新建角色 QA_ENGINEER -> 200" 200 "$resp"
NEW_ROLE_ID=$(python3 -c "import json,sys; d=json.loads('''$resp'''); print(d.get('data',{}).get('id','') if isinstance(d.get('data'),dict) else '')")
echo "  新建 QA_ENGINEER role_id=$NEW_ROLE_ID"
if [ -n "$NEW_ROLE_ID" ]; then
  resp=$(curl -sS -X PUT "$BASE/api/roles/$NEW_ROLE_ID" -H "Authorization: Bearer $T" -H "Content-Type: application/json" -d '{"roleCode":"QA_ENGINEER","roleName":"质量工程师(V2)"}'); chk "ADMIN(pc_hula): 更新自定义角色 -> 200" 200 "$resp"
  resp=$(curl -sS -X PUT "$BASE/api/roles/$NEW_ROLE_ID/status" -H "Authorization: Bearer $T"); chk "ADMIN(pc_hula): 切换自定义角色状态 -> 200" 200 "$resp"
  resp=$(curl -sS -X DELETE "$BASE/api/roles/$NEW_ROLE_ID" -H "Authorization: Bearer $T"); chk "ADMIN(pc_hula): 删除自定义角色 -> 200" 200 "$resp"
fi
# 内置角色保护（pc_hula 新增规则）
resp=$(curl -sS -X DELETE "$BASE/api/roles/1" -H "Authorization: Bearer $T"); chk "ADMIN(pc_hula): 删除内置 ADMIN 角色 -> 400(禁止)" 400 "$resp"
resp=$(curl -sS -X PUT "$BASE/api/roles/2/status" -H "Authorization: Bearer $T"); chk "ADMIN(pc_hula): 禁用内置 OPERATOR -> 400(禁止)" 400 "$resp"
# roleCode 重复
resp=$(curl -sS -X POST "$BASE/api/roles" -H "Authorization: Bearer $T" -H "Content-Type: application/json" -d '{"roleCode":"ADMIN","roleName":"xx"}'); chk "ADMIN(pc_hula): 重复 roleCode -> 409(冲突)" 409 "$resp"

# === pc_hula 新增：用户管理扩展 7 端点 ===
resp=$(curl -sS -X POST "$BASE/api/users" -H "Authorization: Bearer $T" -H "Content-Type: application/json" -d '{"username":"smoke_user","password":"Xy123456!","email":"smoke@test.com","phone":"13900000001"}'); chk "ADMIN(pc_hula): 管理员创建用户 smoke_user -> 200" 200 "$resp"
NEW_USER_ID=$(python3 -c "import json,sys; d=json.loads('''$resp'''); print(d.get('data',{}).get('id','') if isinstance(d.get('data'),dict) else '')")
echo "  新建 smoke_user id=$NEW_USER_ID"
if [ -n "$NEW_USER_ID" ]; then
  resp=$(curl -sS -X PUT "$BASE/api/users/$NEW_USER_ID/lock" -H "Authorization: Bearer $T"); chk "ADMIN(pc_hula): 锁定 smoke_user -> 200" 200 "$resp"
  resp=$(curl -sS -X PUT "$BASE/api/users/$NEW_USER_ID/unlock" -H "Authorization: Bearer $T"); chk "ADMIN(pc_hula): 解锁 smoke_user -> 200" 200 "$resp"
  resp=$(curl -sS -X PUT "$BASE/api/users/$NEW_USER_ID/password" -H "Authorization: Bearer $T" -H "Content-Type: application/json" -d '{"newPassword":"NewPass123!"}'); chk "ADMIN(pc_hula): 重置 smoke_user 密码 -> 200" 200 "$resp"
  resp=$(curl -sS -X POST "$BASE/api/users/$NEW_USER_ID/roles/3" -H "Authorization: Bearer $T"); chk "ADMIN(pc_hula): 分配 VIEWER 角色 -> 200" 200 "$resp"
  resp=$(curl -sS -X GET "$BASE/api/users/$NEW_USER_ID/roles" -H "Authorization: Bearer $T"); chk "ADMIN(pc_hula): 查询用户角色列表 -> 200" 200 "$resp"
  resp=$(curl -sS -X DELETE "$BASE/api/users/$NEW_USER_ID/roles/3" -H "Authorization: Bearer $T"); chk "ADMIN(pc_hula): 取消 VIEWER 角色 -> 200" 200 "$resp"
  # 尝试使用 smoke_user 用新密码登录
  resp=$(curl -sS -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" -d "{\"username\":\"smoke_user\",\"password\":\"NewPass123!\"}"); chk "ADMIN(pc_hula): smoke_user 新密码登录 -> 200" 200 "$resp"
  # 删除 smoke_user 清理
  resp=$(curl -sS -X DELETE "$BASE/api/users/$NEW_USER_ID" -H "Authorization: Bearer $T"); chk "ADMIN(pc_hula): 删除 smoke_user(非当前) -> 200" 200 "$resp"
fi
# 禁止删除当前登录用户(admin id=1)
resp=$(curl -sS -X DELETE "$BASE/api/users/1" -H "Authorization: Bearer $T"); chk "ADMIN(pc_hula): 删除当前登录 admin -> 400(禁止)" 400 "$resp"

# 设备数据上报（触发报警规则）
resp=$(curl -sS -X POST "$BASE/api/device-data/device/1" -H "Authorization: Bearer $T" -H "Content-Type: application/json" -d '{"dataType":"TEMPERATURE","dataValue":120.5,"unit":"C"}'); chk "ADMIN: 上报高温数据(触发报警) -> 200" 200 "$resp"

# === 站点列表（共享端点 VIEWER+）===
resp=$(curl -sS -X GET "$BASE/api/sites" -H "Authorization: Bearer $T"); chk "ADMIN: 站点列表 -> 200" 200 "$resp"

# === 登出（ADMIN 登出）===
resp=$(curl -sS -X POST "$BASE/api/auth/logout" -H "Authorization: Bearer $T"); chk "ADMIN: 登出 -> 200" 200 "$resp"
# 登出后 token 应被黑名单
resp=$(curl -sS -X GET "$BASE/api/users" -H "Authorization: Bearer $T"); chk "ADMIN(token黑名单): 再访问用户列表 -> 401" 401 "$resp"

echo ""
echo "================================"
echo " 结果汇总"
echo "================================"
echo "总计: $TOTAL    通过: $PASS    失败: $FAIL"
echo ""
if [ -n "$PASS_LIST" ]; then echo "---- 通过 ----"; echo -n "$PASS_LIST"; fi
if [ -n "$FAIL_LIST" ]; then echo "---- 失败 ----"; echo -n "$FAIL_LIST"; fi
echo ""
[ $FAIL -eq 0 ] && echo "🎉 全部通过" || echo "⚠️ 存在失败项"
