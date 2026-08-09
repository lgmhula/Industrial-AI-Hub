# Docker 基础 — 镜像、容器、Dockerfile

> Day 57 | 2026-08-09 | Phase 3 第 9 周

---

## 1. 核心概念

| 概念 | 类比 | 本项目中 |
|------|------|----------|
| **镜像 (Image)** | 类 (Class) | `eclipse-temurin:25-jre-alpine` |
| **容器 (Container)** | 实例 (Object) | `docker run` 启动的后端进程 |
| **Dockerfile** | 构建脚本 | `backend/Dockerfile` |
| **compose.yml** | 多容器编排 | 项目根目录 13 服务 |
| **Registry** | 镜像仓库 | Docker Hub (默认) |

```
Dockerfile ──build──→ Image ──run──→ Container
                           │
                     push/pull
                           ↓
                      Registry
```

---

## 2. 本项目 Dockerfile 解读

```dockerfile
# Stage 1: Build (编译阶段)
FROM eclipse-temurin:25-jdk-alpine AS builder
COPY pom.xml ./                     # 先拷贝 pom（利用缓存）
RUN ./mvnw dependency:go-offline    # 下载依赖（层缓存关键）
COPY src ./src                       # 再拷贝源码
RUN ./mvnw package -DskipTests       # 编译打包

# Stage 2: Runtime (运行阶段)
FROM eclipse-temurin:25-jre-alpine   # 只用 JRE，镜像更小
RUN adduser app                      # 非 root 用户
USER app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
HEALTHCHECK ...                      # 健康检查
```

### 为什么多阶段构建

| 方案 | 镜像大小 | 安全 |
|------|----------|------|
| 单阶段 (JDK) | ~450MB | 含 JDK + 编译工具 |
| 多阶段 (JRE) | ~200MB | 只含运行时 |

### 层缓存策略

```
COPY pom.xml    ← 依赖不变时缓存命中
RUN mvnw dep    ← 同上
COPY src        ← 源码变了才重新编译
RUN mvnw package
```

---

## 3. 常用命令

```bash
# 构建镜像
docker build -t industrial-ai-hub:latest -f backend/Dockerfile backend/

# 查看镜像
docker images | grep industrial

# 运行容器
docker run -d -p 8080:8080 --name backend industrial-ai-hub:latest

# 查看日志
docker logs -f backend

# 进入容器
docker exec -it backend sh

# 停止/删除
docker stop backend && docker rm backend
```

---

## 4. compose.yml 中的 backend 服务

```yaml
backend:
  build:
    context: ./backend
    dockerfile: Dockerfile
  ports:
    - "8080:8080"
  depends_on:
    mysql:
      condition: service_healthy
  environment:
    - SPRING_PROFILES_ACTIVE=dev
```

`docker compose up -d backend` 一键构建 + 启动。

---

## 5. 与虚拟机的区别

| | Docker 容器 | 虚拟机 |
|---|------------|--------|
| 启动速度 | 秒级 | 分钟级 |
| 资源占用 | MB 级 | GB 级 |
| 隔离级别 | 进程级 | OS 级 |
| 镜像大小 | ~200MB | ~2GB |
