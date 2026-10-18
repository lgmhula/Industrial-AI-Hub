# Decision 0041: 前端容器化、Loki 与 Grafana

| 属性 | 值 |
|------|-----|
| **状态** | 已落地，Grafana 为可选 profile |
| **决策日期** | 2026-10-18 |
| **决策者** | hula0710 + AI 助手 |
| **关联** | ADR 0014 / ADR 0036 / ADR 0037 / Compose v1.8 |

## 1. 背景

Day 111 前，前端主要通过 Vite dev server 运行，宿主机后端与容器后端并存；Prometheus 已能抓取指标，
但没有浏览器可用的日志聚合视图。项目已经有滚动文件日志、ERROR 独立文件和 traceId，本轮补齐：

- 前端静态构建与 Nginx 反向代理容器；
- 可选 Loki + Promtail + Grafana 观测链；
- core/full/observability profile 的清晰边界。

## 2. 决策

### 2.1 前端容器

1. 新增 `frontend/Dockerfile`：Node 构建 Vue 产物，Nginx 运行静态文件。
2. 新增 `frontend/nginx.conf`：
   - SPA history fallback；
   - `/api/` 反向代理到 `backend:8080`；
   - `/api/push/` 关闭缓冲与缓存，保持 SSE 长连接；
   - `/healthz` 提供给 Compose healthcheck。
3. Compose `core` 增加 `frontend` 服务，默认 `3000:80`，`FRONTEND_HOST_PORT` 可覆盖。
4. 保留 Vite `5173` 作为本地热更路径，容器前端不是开发热更替代品。

### 2.2 Loki / Promtail / Grafana

1. Loki 接收 Promtail 推送的容器日志，Grafana 同时配置 Loki 与 Prometheus 数据源。
2. Promtail 只读 `backend-logs` named volume，positions 使用独立 volume，避免每次重启重复采集。
3. 服务放入 `observability` 或 `full` profile，默认 `core` 不强制启动。
4. Grafana 管理密码由 `GRAFANA_ADMIN_PASSWORD` 注入；模板只放占位值。
5. Grafana 预置 dashboard JSON 和 provisioning，放在 `observability/grafana/`。

## 3. 备选方案

| 方案 | 未采纳原因 |
|------|------------|
| 继续只用 Vite dev server 作为唯一前端交付 | 不能验证静态构建、Nginx 反代和生产式入口 |
| 把前端打包进后端 JAR | 破坏前后端独立构建与部署边界 |
| 直接引入 ELK | 资源占用和运维复杂度高；当前单实例文件日志 + traceId 已满足基础排障 |
| 默认启动 Grafana/Loki | 会显著增加 `core` 启动时间和内存占用，学习/演示环境应可选 |

## 4. 后果与风险

- 容器前端与 Vite dev server 是两条路径，环境变量和 API 代理行为需要分别验证。
- Loki/Promtail/Grafana 只提供观察能力，不等同于日志长期存储或生产级审计平台。
- Grafana 初始密码必须在部署时覆盖，模板中的 `change_me` 不能直接用于共享环境。
- profile 组合增加 Compose 服务数量，文档与实际 `docker compose config` 必须保持一致。

## 5. 验证证据

- 前端 `npm run build` 通过，Docker 镜像可构建并通过 `/healthz`。
- Compose v1.8 `docker compose config --quiet` 通过；core 7 服务、full 20 服务口径由实际配置核对。
- Loki ready、Promtail 采集 backend 日志、Grafana provisioning 数据源与 dashboard 可访问。
- Prometheus 双 targets 与前端 `/api/` 反代在容器环境复验。
