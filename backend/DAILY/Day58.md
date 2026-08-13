# Day 58 — 编写项目 Dockerfile，构建 SpringBoot 镜像

> 日期：2026-08-13 | 阶段：Phase 3（第 9 周 Docker）

## 今日目标

- [x] 审查并优化项目 Dockerfile
- [x] 解决国内网络构建依赖下载不稳定问题（阿里云镜像）
- [x] 实际构建 SpringBoot 镜像

## 产出

### 1. Maven 阿里云镜像配置
`backend/.mvn/settings.xml`：
```xml
<mirror>
  <id>aliyunmaven</id>
  <mirrorOf>central</mirrorOf>
  <url>https://maven.aliyun.com/repository/public</url>
</mirror>
```

### 2. Dockerfile 更新
- `dependency:go-offline` 和 `package` 步骤改用 `-s .mvn/settings.xml`
- 解决 Central "SSL peer shut down incorrectly" / "Truncated chunk" 网络抖动

### 3. 镜像构建成功

```bash
docker build -t industrial-ai-hub-backend:latest -f backend/Dockerfile backend/
```

结果：`industrial-ai-hub-backend:latest`，**302MB**

| 阶段 | 基础镜像 | 说明 |
|------|----------|------|
| Build | eclipse-temurin:25-jdk-alpine | 编译打包 |
| Run | eclipse-temurin:25-jre-alpine | 仅运行时，体积更小 |

## 关键知识点

1. **国内网络陷阱**：Docker 构建环境直连 Maven Central 经常 "Truncated chunk" / "SSL handshake"。阿里云镜像 `maven.aliyun.com` 是最佳实践
2. **多阶段构建价值**：JRE 运行镜像比 JDK 单阶段小约一半
3. **`-s` 参数**：Maven 用 `-s settings.xml` 指定镜像配置，构建时生效

## 验证

```bash
docker images industrial-ai-hub-backend:latest
# industrial-ai-hub-backend:latest | 302MB
```

## 明日

Day 59 — docker-compose 编排：MySQL + Redis + RabbitMQ + 应用
