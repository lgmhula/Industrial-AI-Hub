#!/usr/bin/env bash
# ==========================================
# Industrial AI Hub — 一键部署脚本
# 用法: ./deploy.sh [服务器地址]
# ==========================================
set -euo pipefail

SERVER="${1:-}"
APP_DIR="/opt/industrial-ai-hub"
SERVICE="industrial-ai-hub"

echo "==> 1. 本地构建 Jar 包"
cd "$(dirname "$0")/../backend"
./mvnw clean package -DskipTests -B
JAR="$(ls target/*.jar | head -1)"
echo "    构建完成: $JAR"

if [ -n "$SERVER" ]; then
    echo "==> 2. 上传到服务器 $SERVER"
    ssh "$SERVER" "sudo mkdir -p $APP_DIR /etc/$SERVICE"
    scp "$JAR" "$SERVER:$APP_DIR/app.jar.new"
    ssh "$SERVER" "sudo mv $APP_DIR/app.jar.new $APP_DIR/app.jar"

    echo "==> 3. 重启服务"
    ssh "$SERVER" "sudo systemctl restart $SERVICE && sudo systemctl status $SERVICE --no-pager"

    echo "==> 4. 验证健康检查"
    ssh "$SERVER" "curl -s http://localhost:8080/actuator/health"
    echo ""
    echo "==> 部署完成"
else
    echo "==> 本地部署到 $APP_DIR"
    sudo mkdir -p "$APP_DIR"
    sudo cp "$JAR" "$APP_DIR/app.jar"
    echo "    Jar 已复制到 $APP_DIR/app.jar"
    echo "    提示: 使用 systemd 服务启动: sudo systemctl restart $SERVICE"
fi
