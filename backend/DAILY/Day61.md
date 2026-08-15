# Day 61 — 项目部署到 Linux：JDK 安装、Jar 包运行、Nginx 反向代理

> 日期：2026-08-15 | 阶段：Phase 3（第 9 周 Docker/Linux）

## 今日目标

- [x] 编写 JDK 25 安装脚本
- [x] 编写一键部署脚本（构建 → 上传 → 重启）
- [x] 创建 systemd 服务文件（含安全加固）
- [x] 配置 Nginx 反向代理
- [x] 编写完整部署文档

## 产出

### 1. 部署资产清单（deploy/ 目录）

| 文件 | 作用 |
|------|------|
| `install-jdk.sh` | Temurin JDK 25 安装（Adoptium APT 仓库） |
| `deploy.sh` | 构建 Jar → 上传 → systemd 重启 |
| `industrial-ai-hub.service` | systemd 服务（自动重启 + 安全加固） |
| `nginx.conf` | Nginx 反向代理 + SPA 路由 + 静态缓存 |
| `README.md` | 完整部署指南 |

### 2. systemd 服务安全加固

```ini
[Service]
User=app
Group=app
Restart=always
RestartSec=10
NoNewPrivileges=true     # 禁止提权
ProtectSystem=strict     # 只读文件系统
ProtectHome=true         # 隔离 home
PrivateTmp=true          # 私有临时目录
```

### 3. Nginx 反向代理要点

```
upstream backend {
    server 127.0.0.1:8080;
}

location /api/ {
    proxy_pass http://backend;
    proxy_read_timeout 300s;  # AI 分析接口较慢
}

location / {
    try_files $uri $uri/ /index.html;  # Vue history 路由
}
```

## 关键知识点

1. **systemd 是标准**：相比 `nohup java -jar &`，systemd 提供自动重启、开机自启、日志收集
2. **非 root 运行**：`User=app` + `NoNewPrivileges` 防止提权
3. **Nginx 分流**：`/api/` 反代到后端，`/` 服务前端 SPA，`try_files` 处理 history 路由
4. **环境变量隔离**：`EnvironmentFile=/etc/industrial-ai-hub/env` 避免密钥硬编码在服务文件

## 验证

```bash
bash -n deploy/install-jdk.sh   # 语法 OK
bash -n deploy/deploy.sh        # 语法 OK
```

## 明日

Day 62 — Nginx 配置：静态资源、负载均衡、HTTPS
