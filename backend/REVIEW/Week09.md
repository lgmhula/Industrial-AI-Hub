# Week 09 复盘 — Docker + Linux 部署（Phase 3 第 9 周）

> 日期：2026-08-13 ~ 2026-08-16 | 覆盖：Day 57 ~ Day 62

---

## 一、本周目标 vs 实际

| 目标 | 实际 | 状态 |
|------|------|:----:|
| Day 57: Docker 基础 | 镜像/容器/Dockerfile 概念 + 笔记 | ✅ |
| Day 58: Dockerfile + 构建镜像 | 多阶段构建 + 阿里云镜像 + 302MB 镜像 | ✅ |
| Day 59: compose 编排 | 补全 Redis/RabbitMQ 环境变量 + depends_on | ✅ |
| Day 60: Linux 基础命令 | 六大类命令速查 + 实践清单 | ✅ |
| Day 61: 项目部署资产 | JDK 脚本 + deploy.sh + systemd + nginx | ✅ |
| Day 62: Nginx 进阶 | gzip + 负载均衡 + HTTPS | ✅ |

## 二、关键收获

### 2.1 国内网络是真实的生产挑战

Day 58 构建镜像时连续两次失败：
- "SSL peer shut down incorrectly"
- "Truncated chunk"

这不是代码问题，是网络问题。解决方案：阿里云 Maven 镜像。

**启示**：基础设施问题往往比代码问题更隐蔽，也更耗时。提前配置好镜像源是基本素养。

### 2.2 多阶段构建的价值

```
单阶段 (JDK 25) → ~450MB
多阶段 (JRE 25) → 302MB
```

省下的 150MB 不仅省磁盘，更重要的是部署时传输更快、攻击面更小。

### 2.3 systemd 是部署的标准答案

`nohup java -jar &` 只是临时方案。生产必须用 systemd：

| systemd 特性 | 价值 |
|-------------|------|
| `Restart=always` | 进程崩溃自动重启 |
| `enable` | 开机自启 |
| `journalctl` | 统一日志 |
| `NoNewPrivileges` | 安全加固 |

### 2.4 Nginx 是流量入口的瑞士军刀

一个 Nginx 解决三件事：
1. 静态资源（前端 SPA）
2. 反向代理（后端 API）
3. 负载均衡 + HTTPS

## 三、部署资产全景

```
deploy/
├── install-jdk.sh              # JDK 25 安装
├── deploy.sh                   # 一键部署
├── industrial-ai-hub.service   # systemd 服务
├── nginx.conf                  # 生产 Nginx 配置
└── README.md                   # 部署指南

docs/
├── docker/docker-basics.md     # Docker 基础
└── linux/
    ├── linux-basics.md         # Linux 命令
    └── nginx-advanced.md       # Nginx 进阶
```

## 四、第三阶段检查点达成

> **第三阶段目标**：Redis 缓存、RabbitMQ 消息队列、Docker 容器化、Linux 部署。

| 能力 | 证据 |
|------|------|
| Redis 缓存 | Spring Cache 注解 + 全项目整合（Day 43-48） |
| RabbitMQ 消息 | 工作队列/发布订阅/死信/延迟/项目整合（Day 50-55） |
| Docker 容器化 | 多阶段镜像 + compose 编排（Day 57-59） |
| Linux 部署 | systemd + Nginx + 部署脚本（Day 60-62） |

**检查点结论：技术栈已覆盖大部分中小公司的核心需求。**

## 五、不足与改进

1. **未实际在 Linux 服务器验证**：本机是 macOS，部署脚本只做了语法校验，需真实服务器跑通
2. **HTTPS 未真实验证**：Nginx 配置写好了，但需实际申请证书
3. **负载均衡未压测**：多实例配置完成，但未用 JMeter 验证吞吐提升

## 六、下周展望（第四阶段：AI 集成）

| 天 | 任务 |
|----|------|
| Day 64 | OpenAI API 基础 |
| Day 65 | Spring AI 集成 |
| Day 66 | AI 分析设备数据 |
| Day 67 | AI 分析报警记录 |
| ... | ... |

> 第三阶段（中间件武装）收官。第四阶段是"AI 集成"——让 AI 真正服务业务。
