# Phase 3-A Round 2 修订报告

> **修订日期**: 2026-08-03  
> **修订范围**: 仅限规划文档（`docs/plans/phase3-a-infrastructure-stabilization.md`）  
> **Baseline**: v2.1.0 (commit `ec9a158`)

---

## Round 2 阻断点修复

### CRITICAL-1: RabbitMQ Git 生命周期规则重构

**问题**: Round 1 的 `.gitignore` 规则为全量排除 `rabbitmq/`，导致 `rabbitmq.conf` 等配置文件也被忽略。Fresh clone 无法获取 RabbitMQ 启动配置。

**修复**: 引入分层规则——配置文件通过 `!` 例外纳入版本控制，运行时数据排除。

```
# 配置文件 — 纳入版本控制
!rabbitmq/rabbitmq.conf
!rabbitmq/enabled_plugins
!rabbitmq/definitions.json

# 运行时数据 — 排除
rabbitmq/mnesia/
rabbitmq/.erlang.cookie
```

**影响**: G6 验收标准从"全空"改为"config 非空 + data 空"的双条件验证。

---

### HIGH-1: G9 扫描范围精准限定

**问题**: Round 1 的 G9 要求 `compose.yml` 中全部镜像无 `:latest`，但本地 build 的 `industrial-ai-hub-backend:latest` 是项目自身产物，非外部依赖。

**修复**: G9 扫描范围限定为三项:
- `compose.yml` 中外部依赖镜像（如 `minio/minio`）
- `Dockerfile` 中 `FROM` 基础镜像
- `deploy/` 目录下生产部署脚本

排除项:
- compose.yml 中本地 build 的 image 标签（`industrial-ai-hub-*:latest`）
- 开发环境临时 tag（如 `.env` 文件中的变量）

**验证指令**:
```bash
grep -E 'image:.*:latest' compose.yml | grep -v 'industrial-ai-hub' | grep -q minio && echo "FAIL"
```

---

### HIGH-2: T1 运行态隔离强制检查

**问题**: Round 1 的 T1 Step 1 直接执行 `git rm --cached` + `rm -rf`，未检查 RabbitMQ 容器是否在运行。运行中的 RMQ 可能正在写入 mnesia，竞态删除会导致文件系统不一致。

**修复**: Step 1 前新增前置检查（阻断条件）:

```bash
# 必须确保 RabbitMQ 容器已停止且已移除
docker compose stop rabbitmq
docker compose rm -f rabbitmq

# 确认容器已不存在
docker ps -a --filter name=rabbitmq --format "{{.Status}}" | grep -q . && \
  echo "FAIL: RabbitMQ container still exists -- abort" && exit 1
```

**阻断理由**: `git rm --cached` 配合磁盘删除在容器运行时有竞态风险。

---

## 修订影响文件

| 文件 | 操作 |
|------|:---:|
| `docs/plans/phase3-a-infrastructure-stabilization.md` | 修改 |
| `docs/reports/phase3-a-round2-revision.md` | **新增** |

---

## 验证指令

```bash
# 1. RabbitMQ 规则验证
git check-ignore -v rabbitmq/rabbitmq.conf | grep -q "::" && echo "PASS: conf tracked"
git check-ignore -v rabbitmq/mnesia/ | grep -q "::" && echo "PASS: mnesia ignored"

# 2. G9 范围验证
grep -E 'image:.*:latest' compose.yml | grep -v 'industrial-ai-hub' | grep -q . && \
  echo "FAIL: external dependency has :latest" || echo "PASS: no external :latest"

# 3. T1 隔离验证
docker compose ps rabbitmq --format "{{.Status}}" 2>/dev/null | grep -q "Up" && \
  echo "WARN: rabbitmq is running — T1 must stop it first" || echo "PASS: rabbitmq not running"
```
