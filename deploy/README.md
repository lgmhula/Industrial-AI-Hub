# Industrial AI Hub — Linux 部署指南

> Day 61 | 2026-08-15

---

## 1. 前置条件

| 组件 | 版本 | 说明 |
|------|------|------|
| 操作系统 | Debian/Ubuntu | 推荐 Ubuntu 22.04+ |
| JDK | 25 LTS (Temurin) | `install-jdk.sh` 一键安装 |
| MySQL | 8.4 | 可 Docker 或裸机安装 |
| Redis | 7.4 | 可 Docker 或裸机安装 |
| RabbitMQ | 4.0 | 可 Docker 或裸机安装 |
| Nginx | 1.x | 反向代理 |

---

## 2. 部署步骤

### 步骤 1: 安装 JDK

```bash
sudo bash deploy/install-jdk.sh
java -version  # 验证 25.x
```

### 步骤 2: 准备目录与用户

```bash
sudo useradd -r -s /bin/false app
sudo mkdir -p /opt/industrial-ai-hub /etc/industrial-ai-hub
sudo chown -R app:app /opt/industrial-ai-hub
```

### 步骤 3: 配置环境变量

创建 `/etc/industrial-ai-hub/env`：

```bash
MYSQL_HOST=127.0.0.1
MYSQL_PORT=3307
MYSQL_USER=root
MYSQL_PASSWORD=your_password
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
REDIS_PASSWORD=your_password
RABBITMQ_HOST=127.0.0.1
RABBITMQ_PORT=5672
RABBITMQ_DEFAULT_USER=admin
RABBITMQ_DEFAULT_PASS=your_password
JWT_SECRET=your_jwt_secret
JWT_EXPIRATION_MS=86400000
```

### 步骤 4: 注册 systemd 服务

```bash
sudo cp deploy/industrial-ai-hub.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable industrial-ai-hub
```

### 步骤 5: 部署

```bash
# 本地部署
sudo bash deploy/deploy.sh

# 或远程部署
sudo bash deploy/deploy.sh user@server-ip
```

### 步骤 6: Nginx 反向代理

```bash
sudo apt install nginx
sudo cp deploy/nginx.conf /etc/nginx/sites-available/industrial-ai-hub
sudo ln -s /etc/nginx/sites-available/industrial-ai-hub /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
```

---

## 3. 验证

```bash
# 服务状态
sudo systemctl status industrial-ai-hub

# 健康检查
curl http://localhost:8080/actuator/health

# 通过 Nginx 访问
curl http://industrial-ai-hub.example.com/api/devices
```

---

## 4. 常用运维命令

```bash
# 查看日志
sudo journalctl -u industrial-ai-hub -f

# 重启
sudo systemctl restart industrial-ai-hub

# 查看端口
sudo ss -tlnp | grep -E '8080|80'
```

---

## 5. 文件清单

| 文件 | 作用 |
|------|------|
| `install-jdk.sh` | JDK 25 安装 |
| `deploy.sh` | 构建 + 上传 + 重启 |
| `industrial-ai-hub.service` | systemd 服务 |
| `nginx.conf` | Nginx 反向代理 |
