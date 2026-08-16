# Nginx 进阶：静态资源、负载均衡、HTTPS

> Day 62 | 2026-08-16

---

## 1. 静态资源优化

### gzip 压缩

```nginx
gzip on;
gzip_types text/plain text/css application/json application/javascript;
gzip_min_length 1024;   # 小于 1KB 不压缩（压缩反而更大）
gzip_comp_level 6;      # 压缩级别 1-9，6 是平衡点
```

### 缓存策略

| 资源类型 | Cache-Control | 原因 |
|----------|--------------|------|
| JS/CSS（hash 文件名） | `public, immutable, 30d` | 文件名变化即失效 |
| index.html | `no-cache` | 需每次校验更新 |
| 图片 | `public, 7d` | 变化少 |

---

## 2. 负载均衡策略

### 三种策略

```nginx
upstream backend {
    # 1. 轮询（默认）— 依次分配
    server 127.0.0.1:8080;
    server 127.0.0.1:8081;

    # 2. 加权轮询 — 按 weight 比例分配
    # server 127.0.0.1:8080 weight=3;
    # server 127.0.0.1:8081 weight=1;

    # 3. 最少连接 — 分给当前连接最少的
    # least_conn;
}
```

### 会话保持

JWT 无状态，通常**不需要** `ip_hash`。若需会话保持：

```nginx
upstream backend {
    ip_hash;   # 同 IP 始终打到同一实例
    server 127.0.0.1:8080;
    server 127.0.0.1:8081;
}
```

---

## 3. HTTPS

### Let's Encrypt 免费证书

```bash
# 安装 certbot
sudo apt install certbot python3-certbot-nginx

# 自动申请 + 配置
sudo certbot --nginx -d industrial-ai-hub.example.com

# 自动续期（certbot 会自动注册 cron）
sudo certbot renew --dry-run
```

### SSL 配置要点

```nginx
ssl_protocols TLSv1.2 TLSv1.3;   # 禁用旧 TLS
ssl_ciphers HIGH:!aNULL:!MD5;     # 只允许强加密
ssl_session_cache shared:SSL:10m; # 会话复用，减少握手
```

### HTTP → HTTPS 强制跳转

```nginx
server {
    listen 80;
    server_name example.com;
    return 301 https://$host$request_uri;
}
```

---

## 4. 多实例后端部署

要真正发挥负载均衡，需要多个后端实例：

```bash
# 实例 1（8080）
java -jar app.jar --server.port=8080

# 实例 2（8081）
java -jar app.jar --server.port=8081
```

或在 systemd 中定义两个服务实例。

---

## 5. 完整验证

```bash
# 配置语法检查
sudo nginx -t

# 重载配置（不停机）
sudo systemctl reload nginx

# 验证 HTTPS
curl -I https://example.com

# 验证负载均衡（多次请求看 X-Real-IP 或日志）
for i in $(seq 1 10); do curl -s http://localhost:8080/actuator/health; done
```
