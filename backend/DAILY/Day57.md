# Day 57 — Docker 基础：镜像、容器、Dockerfile

> 日期：2026-08-09 | 阶段：Phase 3（第 9 周 Docker）

## 今日目标

- [x] Docker 核心概念笔记（镜像/容器/Dockerfile/compose）
- [x] 审查项目现有 Dockerfile（多阶段构建 + 非 root + 健康检查）
- [x] 89/89 测试全绿

## 产出

### 1. Docker 基础笔记
`docs/docker/docker-basics.md`：
- 镜像/容器/Dockerfile/Registry 概念及类比
- 本项目 Dockerfile 逐段解读（多阶段构建 / 层缓存 / 非 root 用户）
- 常用命令速查
- Docker vs 虚拟机对比

### 2. 现有 Dockerfile 审查

`backend/Dockerfile`（Phase 3-A 产物，审查通过）：

| 特性 | 状态 |
|------|:----:|
| 多阶段构建 (JDK build → JRE run) | ✅ |
| 层缓存优化 (先 COPY pom.xml) | ✅ |
| 非 root 用户运行 | ✅ |
| 健康检查 (/actuator/health) | ✅ |
| JDK 25 LTS | ✅ |

Dockerfile 无需修改——`spring-boot-starter-amqp` 通过 Maven 依赖管理自动纳入构建。

## 核心知识点

1. **Dockerfile → Image → Container**：Dockerfile 是配方，Image 是成品，Container 是运行实例
2. **多阶段构建**：编译用 JDK（~450MB），运行用 JRE（~200MB），最终镜像比单阶段小一半
3. **层缓存**：Docker 按行缓存。`COPY pom.xml` 先于 `COPY src`→ 依赖不变时跳过下载，构建秒级完成
4. **非 root 用户**：`USER app` 防止容器逃逸提权，生产必需

## 验证命令

```bash
cd backend && ./mvnw test
# Tests run: 89, Failures: 0, Errors: 0, Skipped: 0
```

## 明日

Day 58 — 编写项目 Dockerfile，构建 SpringBoot 镜像
