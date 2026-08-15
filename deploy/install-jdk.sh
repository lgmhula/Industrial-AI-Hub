#!/usr/bin/env bash
# ==========================================
# JDK 25 (Temurin) 安装脚本 — Debian/Ubuntu
# ==========================================
set -euo pipefail

echo "==> 安装 Temurin JDK 25"

# 使用 Adoptium APT 仓库（最稳妥的官方源）
if ! command -v java >/dev/null 2>&1 || ! java -version 2>&1 | grep -q "25"; then
    apt-get update
    apt-get install -y wget apt-transport-https ca-certificates gnupg

    wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public \
        | gpg --dearmor > /usr/share/keyrings/adoptium.gpg

    echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" \
        > /etc/apt/sources.list.d/adoptium.list

    apt-get update
    apt-get install -y temurin-25-jdk
else
    echo "JDK 25 已安装"
fi

java -version
echo "==> JDK 安装完成"
