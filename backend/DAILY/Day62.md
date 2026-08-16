# Day 62 — Nginx 配置：静态资源、负载均衡、HTTPS

> 日期：2026-08-16 | 阶段：Phase 3（第 9 周 Docker/Linux）

## 今日目标

- [x] 升级 nginx.conf 到生产配置（gzip + 负载均衡 + HTTPS）
- [x] 编写 Nginx 进阶笔记（静态资源/负载均衡/HTTPS）

## 产出

### 1. nginx.conf 生产化

| 特性 | 配置 |
|------|------|
| gzip 压缩 | `gzip_types` + `gzip_min_length 1024` |
| 负载均衡 | `upstream backend` 加权轮询 |
| HTTP→HTTPS | `return 301` |
| SSL | TLSv1.2/1.3 + 会话复用 |
| 静态缓存 | `immutable, 30d` |
| SPA 路由 | `try_files ... /index.html` |

### 2. Nginx 进阶笔记
`docs/linux/nginx-advanced.md`：
- gzip 压缩策略
- 三种负载均衡（轮询/加权/最少连接）
- Let's Encrypt 免费证书申请
- 多实例后端部署

## 关键知识点

1. **加权轮询**：`weight=3` 让强实例承担 3 倍流量
2. **JWT 无状态 → 不需要 ip_hash**：JWT 自带身份信息，任何实例都能验证
3. **gzip_min_length**：小文件压缩反而增大，需设阈值
4. **immutable 缓存**：仅适用于文件名带 hash 的资源（Vue 构建产物天然符合）

## 明日

Day 63 — 周复盘 + 部署文档 + Docker/Linux 笔记（第 9 周收尾 + 第三阶段检查点）
