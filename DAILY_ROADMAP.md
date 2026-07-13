 # Reboot 每日路线图（紧凑版）

> 基于 ChatGPT 的总体路线，压缩为每日可执行的计划。
> 原则：每天有产出，每周有复盘，不做练习项目。

---

## 总体阶段概览

| 阶段 | 时间 | 目标 |
|------|------|------|
| 第一阶段：Java 复苏 | 第 1-3 周（21 天） | 恢复 Java 基础和 SpringBoot CRUD 能力 |
| 第二阶段：项目 V1 | 第 4-6 周（21 天） | Industrial AI Hub 核心骨架：设备管理+用户+日志 |
| 第三阶段：中间件武装 | 第 7-9 周（21 天） | Redis + RabbitMQ + Docker + Linux 部署 |
| 第四阶段：AI 集成 | 第 10-13 周（28 天） | AI 分析、RAG、Agent、MCP 进入项目 |
| 第五阶段：PLC + 完整系统 | 第 14-16 周（21 天） | PLC 模拟、MQTT、完整部署上线 |

> 总计 16 周（约 4 个月）完成完整技术栈闭环，之后进入持续迭代。

---

## 每日作息模板

```
08:00 - 08:30  起床、洗漱、早餐
08:30 - 09:00  回顾昨天产出，明确今天目标（写下来）
09:00 - 11:00  编码块 1（番茄钟 × 4）
11:00 - 11:15  休息
11:15 - 12:30  编码块 2（番茄钟 × 3）
12:30 - 14:00  午饭 + 午休（不碰手机/游戏）
14:00 - 16:00  编码块 3（番茄钟 × 4）
16:00 - 16:30  运动/散步（必须出门）
16:30 - 18:30  编码块 4（番茄钟 × 4）
18:30 - 20:00  晚饭 + 自由时间
20:00 - 21:00  复盘：今日产出 + Git commit + 笔记
21:00 - 22:00  自由时间（可以游戏，但要设定闹钟）
22:00 - 23:00  阅读/刷题/准备明天
23:00          睡觉
```

> **最低底线**：如果状态极差，至少完成 09:00-11:00 和 14:00-16:00 两个编码块（共 4 小时）。
> **游戏规则**：只在 21:00-22:00 玩，闹钟响必须停。卸载手机上的游戏/短视频 app。

---

# 第一阶段：Java 复苏（第 1-3 周）

> 目标：恢复 Java 基础 → SpringBoot → MyBatis → CRUD 项目
> 禁止：AI/MCP/SpringCloud/微服务
> 每天至少 1 次 Git commit

---

## 第 1 周：Java 核心语法 + OOP

### Day 1（周一）
- [ ] 搭建开发环境：JDK 17 + IDEA + Git 配置 + Maven
- [ ] 创建第一个 Java 项目，写出 HelloWorld
- [ ] 回顾：基本数据类型、变量声明、运算符
- [ ] 写代码练习（至少 200 行）：
  - 声明 8 种基本类型变量并打印
  - 实现四则运算计算器（从控制台读取输入）
  - 字符串拼接、截取、替换练习
- [ ] 笔记：整理 Java 数据类型速查表到 NOTES/Java/basics.md
- [ ] LeetCode 1 道简单题（比如 1.两数之和）
- [ ] Git commit：初始化仓库 + Day1 代码

### Day 2（周二）
- [ ] 回顾：if/else、switch、for、while、do-while
- [ ] 写代码练习（至少 250 行）：
  - 打印九九乘法表（for 循环）
  - 判断闰年（if/else）
  - 猜数字游戏（while 循环 + 随机数）
  - 输出 1-100 的质数（双重 for）
  - switch 实现简易菜单选择
- [ ] LeetCode 1 道（比如 9.回文数）
- [ ] Git commit

### Day 3（周三）
- [ ] 回顾：数组（一维、二维）、Arrays 工具类
- [ ] 写代码练习（至少 200 行）：
  - 数组遍历、求和、最大值、最小值
  - 冒泡排序、选择排序（手写，不要复制）
  - 二维数组实现杨辉三角
  - 数组反转
- [ ] LeetCode 1 道（比如 26.删除有序数组中的重复项）
- [ ] Git commit

### Day 4（周四）
- [ ] 回顾：类与对象、构造方法、this、封装
- [ ] 写代码练习（至少 200 行）：
  - 定义一个 Student 类（属性：name, age, score）
  - 实现构造方法、getter/setter
  - 创建对象数组并排序
  - 实现一个简易银行账户类（存钱、取钱、查询余额）
- [ ] LeetCode 1 道（比如 66.加一）
- [ ] Git commit

### Day 5（周五）
- [ ] 回顾：继承、super、方法重写、多态
- [ ] 写代码练习（至少 200 行）：
  - Animal → Dog/Cat 继承体系
  - 方法重写 + super 调用
  - 多态：父类引用指向子类对象
  - 实现一个简易员工管理系统（Employee → Manager/Developer）
- [ ] LeetCode 1 道（比如 88.合并两个有序数组）
- [ ] Git commit

### Day 6（周六）
- [ ] 回顾：抽象类、接口、final、static
- [ ] 写代码练习（至少 200 行）：
  - 接口定义与实现（USB 接口 → Mouse/Keyboard）
  - 抽象类 vs 接口对比练习
  - static 变量和方法的实际应用
  - final 修饰类/方法/变量的效果
- [ ] LeetCode 1 道（比如 118.杨辉三角）
- [ ] Git commit

### Day 7（周日）—— 每周复盘日
- [ ] 上午：回顾本周所有代码，重构一个觉得写得不好的模块
- [ ] 下午：写本周学习总结（REVIEW/Week01.md）
  - 哪些知识点已经完全恢复？
  - 哪些还需要加强？
  - 本周写了多少行代码？提交了几次？
- [ ] 刷 3 道 LeetCode（复习本周做过的）
- [ ] Git commit + push

---

## 第 2 周：集合 + 异常 + IO + 常用类

### Day 8（周一）
- [ ] 回顾：ArrayList、LinkedList
- [ ] 写代码练习（至少 200 行）：
  - ArrayList 增删改查遍历（4 种遍历方式）
  - 用 ArrayList 实现一个简易通讯录
  - LinkedList vs ArrayList 性能对比
- [ ] LeetCode 1 道（比如 206.反转链表）
- [ ] Git commit

### Day 9（周二）
- [ ] 回顾：HashSet、TreeSet、HashMap、TreeMap
- [ ] 写代码练习（至少 200 行）：
  - HashMap 存放学生信息并遍历
  - 统计一段文本中每个单词出现的次数（HashMap）
  - HashSet 去重练习
  - 理解 equals() 和 hashCode() 的关系
- [ ] LeetCode 1 道（比如 217.存在重复元素）
- [ ] Git commit

### Day 10（周三）
- [ ] 回顾：泛型、Collections 工具类、Comparable/Comparator
- [ ] 写代码练习（至少 200 行）：
  - 自定义泛型类和方法
  - Collections.sort() 对对象排序
  - 实现 Comparable 和 Comparator 两种排序方式
  - 斗地主发牌模拟（综合：集合 + 排序 + 随机）
- [ ] LeetCode 1 道（比如 242.有效的字母异位词）
- [ ] Git commit

### Day 11（周四）
- [x] 回顾：String、StringBuilder、StringBuffer、包装类、日期时间
- [x] 写代码练习（至少 200 行）：
  - String 常用 API 全部练习一遍
  - StringBuilder 拼接 10000 次性能对比
  - 包装类的自动装箱/拆箱
  - LocalDate/LocalDateTime 日期计算练习
- [x] LeetCode 1 道（比如 125.验证回文串）
- [x] Git commit

### Day 12（周五）
- [ ] 回顾：异常处理（try-catch-finally、throws、自定义异常）
- [ ] 写代码练习（至少 200 行）：
  - 常见异常模拟（空指针、数组越界、类型转换）
  - try-catch-finally 执行顺序练习
  - 自定义异常类并在项目中实际使用
  - 异常链（cause）的使用
- [ ] LeetCode 1 道（比如 14.最长公共前缀）
- [ ] Git commit

### Day 13（周六）
- [ ] 回顾：IO 流（File、字节流、字符流、缓冲流）
- [ ] 写代码练习（至少 200 行）：
  - 使用 File 类遍历目录
  - 文件复制（字节流 vs 缓冲流性能对比）
  - 字符流读写文本文件
  - 实现一个简易文本日志工具类
- [ ] LeetCode 1 道（比如 20.有效的括号）
- [ ] Git commit

### Day 14（周日）—— 每周复盘日
- [ ] 上午：写一个综合练习——简易学生管理系统（控制台版）
  - 用集合存储学生数据
  - 增删改查 + 排序 + 文件读写持久化
  - 至少 300 行代码
- [ ] 下午：写本周学习总结（REVIEW/Week02.md）
- [ ] Git commit + push

---

## 第 3 周：MySQL + JDBC + MyBatis + SpringBoot 入门

### Day 15（周一）
- [ ] 安装 MySQL 8.0，创建数据库 reboot
- [ ] 回顾：DDL（CREATE/ALTER/DROP）、DML（INSERT/UPDATE/DELETE）、DQL（SELECT）
- [ ] 练习：
  - 创建 user 表（id, username, password, email, created_at）
  - 插入 10 条测试数据
  - 各种查询：WHERE、LIKE、BETWEEN、IN、ORDER BY、LIMIT
  - GROUP BY + 聚合函数（COUNT/SUM/AVG/MAX/MIN）
- [ ] LeetCode 1 道（比如 21.合并两个有序链表）
- [ ] Git commit

### Day 16（周二）
- [ ] 回顾：多表查询（INNER JOIN/LEFT JOIN/RIGHT JOIN）、子查询
- [ ] 练习：
  - 创建 device 表（id, name, type, status, created_at）
  - 创建 device_data 表（id, device_id, value, unit, recorded_at）
  - JOIN 查询设备及其最新数据
  - 子查询：查询数据值超过平均值的设备
- [ ] 创建 Industrial AI Hub 的初始数据库设计文档
- [ ] LeetCode 1 道（比如 141.环形链表）
- [ ] Git commit

### Day 17（周三）
- [ ] 回顾：索引、事务、JDBC
- [ ] 练习：
  - 创建索引并 EXPLAIN 分析查询性能
  - JDBC 连接 MySQL，执行 CRUD（纯 JDBC，不用框架）
  - 理解 Connection、Statement、PreparedStatement、ResultSet
  - JDBC 事务控制（commit/rollback）
- [ ] LeetCode 1 道（比如 160.相交链表）
- [ ] Git commit

### Day 18（周四）
- [ ] 学习：MyBatis 配置 + XML 映射
- [ ] 练习：
  - 搭建 Maven 项目，引入 MyBatis 依赖
  - 编写 mybatis-config.xml
  - 创建 UserMapper.xml，实现 User 的 CRUD
  - 使用 SqlSession 执行查询
- [ ] Git commit

### Day 19（周五）
- [ ] 学习：MyBatis 注解方式 + 动态 SQL + 分页
- [ ] 练习：
  - @Select/@Insert/@Update/@Delete 注解实现 Device CRUD
  - 动态 SQL：if/where/foreach
  - 实现分页查询
- [ ] 整合到项目中：将 JDBC 版本替换为 MyBatis
- [ ] Git commit

### Day 20（周六）
- [ ] 学习：SpringBoot 入门
- [ ] 练习：
  - 创建 SpringBoot 项目（使用 Spring Initializr）
  - 理解 @SpringBootApplication、自动配置
  - 编写第一个 REST 接口（Hello World）
  - 理解 application.yml 配置
- [ ] LeetCode 1 道（比如 234.回文链表）
- [ ] Git commit

### Day 21（周日）—— 第三周复盘
- [ ] 上午：SpringBoot + MyBatis 整合
  - 整合 MyBatis 到 SpringBoot 项目
  - 实现 User 的 RESTful API（GET/POST/PUT/DELETE）
  - 用 Postman 测试所有接口
- [ ] 下午：写本周学习总结（REVIEW/Week03.md）
- [ ] Git commit + push

> **第一阶段检查点**：能独立写出 SpringBoot + MyBatis CRUD API，能手写 for/while/集合遍历/SQL 查询。如果做不到，多花 2-3 天补齐再进入下一阶段。

---

# 第二阶段：Industrial AI Hub V1（第 4-6 周）

> 目标：完成项目核心骨架。只有 6 个模块：登录、用户管理、设备管理、设备数据、报警、日志。
> 技术栈：SpringBoot + MyBatis + MySQL + JWT + 前端（Vue 或纯 HTML，先简后精）

---

## 第 4 周：项目骨架 + 用户和认证模块

### Day 22（周一）
- [ ] 创建 SpringBoot 项目 industrial-ai-hub
- [ ] 设计项目包结构（controller/service/mapper/entity/dto/config）
- [ ] 数据库表设计定稿：user, role, user_role, device, device_data, alarm, operation_log
- [ ] 编写数据库初始化 SQL 脚本
- [ ] Git commit：项目初始化

### Day 23（周二）
- [ ] 实现 JWT 工具类（生成 token、验证 token、解析 token）
- [ ] 实现登录接口 POST /api/auth/login
- [ ] 实现注册接口 POST /api/auth/register
- [ ] 密码 BCrypt 加密
- [ ] 用 Postman 测试完整登录流程
- [ ] Git commit

### Day 24（周三）
- [ ] 实现 RBAC 权限模型：user_role 表 + 角色枚举
- [ ] 实现认证拦截器/JWT Filter
- [ ] 实现 @RequireRole 自定义注解
- [ ] 测试：不同角色访问不同接口的权限控制
- [ ] Git commit

### Day 25（周四）
- [ ] 实现用户管理模块：用户列表、查询、编辑、删除
- [ ] 分页查询（PageHelper）
- [ ] 用户状态启用/禁用
- [ ] Git commit

### Day 26（周五）
- [ ] 实现统一响应格式 Result<T>（code, message, data）
- [ ] 实现全局异常处理 @ControllerAdvice
- [ ] 实现参数校验（@Valid + BindingResult）
- [ ] 重构已有接口，统一使用 Result 返回
- [ ] Git commit

### Day 27（周六）
- [ ] 前端页面（使用 Vue3 + Element Plus，简单版）：
  - 登录页面
  - 主布局（侧边栏 + 顶部栏）
  - 用户管理列表页
- [ ] 前后端联调
- [ ] Git commit

### Day 28（周日）—— 每周复盘
- [ ] 上午：检查已有代码，重构不合理的设计
- [ ] 下午：写 REVIEW/Week04.md
- [ ] 补充遗漏的单元测试
- [ ] Git commit + push

---

## 第 5 周：设备管理 + 设备数据模块

### Day 29（周一）
- [ ] 实现设备管理模块：
  - 设备列表（分页 + 搜索 + 筛选）
  - 设备新增（名称、类型、位置、状态）
  - 设备编辑
  - 设备删除（逻辑删除）
- [ ] MyBatis 复杂查询：多条件动态 SQL
- [ ] Git commit

### Day 30（周二）
- [ ] 实现设备数据模块：
  - 设备数据上报接口 POST /api/device/{id}/data
  - 设备数据列表查询（按时间范围）
  - 设备最新数据查询
- [ ] 造一批模拟数据（编写数据生成脚本）
- [ ] Git commit

### Day 31（周三）
- [ ] 前端：设备管理页面（列表 + 新增/编辑弹窗）
- [ ] 前端：设备详情页（含数据图表）
- [ ] 使用 ECharts 展示设备数据趋势
- [ ] Git commit

### Day 32（周四）
- [ ] 实现报警模块：
  - 报警规则定义（温度上限、压力上限等）
  - 数据上报时自动检测是否触发报警
  - 报警记录生成
- [ ] 实现报警列表 + 报警处理（确认/解决）
- [ ] Git commit

### Day 33（周五）
- [ ] 实现操作日志模块：
  - AOP 切面自动记录操作日志
  - 记录：操作人、操作时间、操作类型、IP、请求参数
  - 日志查询页面
- [ ] Git commit

### Day 34（周六）
- [ ] 前端补全：报警页面 + 日志页面
- [ ] 全局联调：登录 → 设备管理 → 数据上报 → 报警触发 → 日志记录
- [ ] 修复发现的 Bug
- [ ] Git commit

### Day 35（周日）—— 每周复盘 + 里程碑
- [ ] 上午：完整的业务流程测试（录屏）
- [ ] 下午：写 REVIEW/Week05.md
- [ ] 代码 review：命名、异常处理、日志打印
- [ ] Git tag: v1.0-alpha

---

## 第 6 周：V1 打磨 + 测试

### Day 36（周一）
- [ ] 编写单元测试：UserService、DeviceService
- [ ] 编写集成测试：登录→设备 CRUD→数据上报 全链路
- [ ] 使用 JUnit5 + Mockito
- [ ] Git commit

### Day 37（周二）
- [ ] 性能优化：
  - SQL 慢查询排查
  - 设备数据查询加索引
  - 分页查询优化
- [ ] 接口限流（Guava RateLimiter 简单实现）
- [ ] Git commit

### Day 38（周三）
- [ ] 完善前端：表单校验、loading 状态、错误提示
- [ ] 响应式布局适配（至少保证 1920×1080 和 1366×768）
- [ ] Git commit

### Day 39（周四）
- [ ] Swagger/Knife4j 接口文档集成
- [ ] 编写 README.md（项目介绍、技术栈、如何运行）
- [ ] Git commit

### Day 40（周五）
- [ ] 整体测试 + Bug 修复
- [ ] 前端交互体验优化
- [ ] 代码清理（删除无用 import、注释掉的代码）
- [ ] Git commit

### Day 41（周六）
- [ ] 总结文档：项目架构图（draw.io 或 Mermaid）
- [ ] 总结文档：数据库 ER 图
- [ ] 总结文档：API 接口清单
- [ ] 准备 V1 演示（作为一个完整的可演示系统）

### Day 42（周日）—— 第二阶段复盘
- [ ] 完整演示：从登录到设备数据报警的全流程
- [ ] 写 REVIEW/Phase2-Summary.md
- [ ] 列出 V1 的技术债务清单
- [ ] Git tag: v1.0-release

> **第二阶段检查点**：拥有一个完整可用的设备管理平台，含登录、RBAC、设备 CRUD、数据上报、报警、日志。这是你简历上第一个真实项目。

---

# 第三阶段：中间件武装（第 7-9 周）

> 目标：Redis 缓存、RabbitMQ 消息队列、Docker 容器化、Linux 部署。

### 第 7 周：Redis
| 天 | 任务 |
|----|------|
| Day 43 | Redis 安装 + 五种基本数据类型练习（string/hash/list/set/zset） |
| Day 44 | Redis 缓存实战：用户信息缓存、设备数据缓存、缓存预热 |
| Day 45 | 缓存穿透/击穿/雪崩解决方案：布隆过滤器、互斥锁、随机过期 |
| Day 46 | Redis 分布式锁（Redisson）：设备数据上报防重 |
| Day 47 | Spring Cache 注解集成（@Cacheable/@CacheEvict） |
| Day 48 | Redis 实战整合：重构项目中所有可缓存的地方 |
| Day 49 | 周复盘 + Redis 笔记整理 |

### 第 8 周：RabbitMQ
| 天 | 任务 |
|----|------|
| Day 50 | RabbitMQ 安装 + 核心概念（Exchange/Queue/Binding）+ 简单收发 |
| Day 51 | 工作队列模式：报警消息异步处理 |
| Day 52 | 发布/订阅模式：设备数据同步到多个消费者 |
| Day 53 | 消息可靠性：持久化、手动 ACK、死信队列、幂等消费 |
| Day 54 | 延迟队列：报警延迟通知（30 秒内未处理则升级） |
| Day 55 | RabbitMQ 整合到项目：重构报警和数据上报模块 |
| Day 56 | 周复盘 + RabbitMQ 笔记整理 |

### 第 9 周：Docker + Linux 部署
| 天 | 任务 |
|----|------|
| Day 57 | Docker 基础：镜像、容器、Dockerfile |
| Day 58 | 编写项目 Dockerfile，构建 SpringBoot 镜像 |
| Day 59 | docker-compose 编排：MySQL + Redis + RabbitMQ + 应用 |
| Day 60 | Linux 基础命令（在虚拟机或云服务器上练习） |
| Day 61 | 项目部署到 Linux：JDK 安装、Jar 包运行、Nginx 反向代理 |
| Day 62 | Nginx 配置：静态资源、负载均衡、HTTPS |
| Day 63 | 周复盘 + 部署文档 + Docker/Linux 笔记 |

> **第三阶段检查点**：项目具备缓存、消息队列、容器化部署能力。这意味着你的技术栈已经覆盖了大部分中小公司的核心需求。

---

# 第四阶段：AI 集成（第 10-13 周）

> 目标：OpenAI API → RAG → Agent → MCP，全部进入项目。
> 注意：不是做聊天机器人，而是让 AI 服务业务。

### 第 10 周：OpenAI API + Spring AI
| 天 | 任务 |
|----|------|
| Day 64 | OpenAI API 基础：ChatCompletion、Streaming、Token 管理 |
| Day 65 | Spring AI 集成：配置、ChatClient、Prompt Template |
| Day 66 | 实战：AI 分析设备数据并生成自然语言摘要 |
| Day 67 | 实战：AI 分析报警记录并给出处理建议 |
| Day 68 | Function Calling：AI 自动调用项目接口查询设备状态 |
| Day 69 | 前端集成：设备详情页加入"AI 分析"按钮 |
| Day 70 | 周复盘 + AI 模块笔记 |

### 第 11 周：RAG + 知识库
| 天 | 任务 |
|----|------|
| Day 71 | RAG 概念 + 向量数据库选型（Milvus/Chroma/Qdrant） |
| Day 72 | 文档切片 + 向量化（embedding）+ 存入向量库 |
| Day 73 | 知识检索实现：根据用户问题检索相关文档 |
| Day 74 | 实战：设备手册知识库——导入 PDF 设备说明书 |
| Day 75 | 实战：AI 运维助手——根据知识库回答设备运维问题 |
| Day 76 | 前端：AI 助手对话页面（侧边栏或独立页面） |
| Day 77 | 周复盘 + RAG 笔记 |

### 第 12 周：Agent + MCP
| 天 | 任务 |
|----|------|
| Day 78 | Agent 概念：ReAct 模式、工具调用循环 |
| Day 79 | 实现简单 Agent：多步推理（先查设备→再查数据→再分析） |
| Day 80 | MCP 协议概念 + MCP Server 开发 |
| Day 81 | 开发 Device MCP Server：暴露设备查询、数据查询工具 |
| Day 82 | Codex/MCP 客户端集成：让 AI 通过 MCP 调用项目接口 |
| Day 83 | Agent + MCP 联调：AI 自动巡检设备并生成日报 |
| Day 84 | 周复盘 + Agent/MCP 笔记 |

### 第 13 周：AI 模块打磨
| 天 | 任务 |
|----|------|
| Day 85 | AI 生成的日报自动通过 RabbitMQ 推送到前端 |
| Day 86 | AI 巡检异常自动生成报警——AI 与业务闭环 |
| Day 87 | 前端 AI 功能完善：日报展示、巡检结果、知识库搜索 |
| Day 88 | 全链路联调：数据→AI分析→报警→MCP查询→Agent总结 |
| Day 89 | 重构 AI 模块代码：抽取公共组件、异常处理、限流 |
| Day 90 | 写 AI 模块集成文档 |
| Day 91 | 第四阶段复盘 + Git tag: v2.0-ai |

> **第四阶段检查点**：AI 不再是 demo，而是真正为项目创造业务价值的功能模块。

---

# 第五阶段：PLC + 完整系统（第 14-16 周）

> 目标：PLC 模拟设备接入、MQTT 协议、完整系统上线。

### 第 14 周：PLC 模拟 + MQTT
| 天 | 任务 |
|----|------|
| Day 92 | PLC 基础概念：Modbus、寄存器、线圈 |
| Day 93 | MQTT 协议基础 + EMQX/Mosquitto 安装 |
| Day 94 | Java MQTT 客户端（Eclipse Paho）开发 |
| Day 95 | 模拟 PLC 设备：Java 程序定时发送模拟传感器数据 |
| Day 96 | MQTT → 项目：接收 MQTT 数据并存入 device_data 表 |
| Day 97 | 模拟多设备并发数据上报 + 压力测试 |
| Day 98 | 周复盘 + PLC/MQTT 笔记 |

### 第 15 周：系统整合 + 运维
| 天 | 任务 |
|----|------|
| Day 99 | 完整系统联调：MQTT数据→业务处理→报警→AI分析→通知 |
| Day 100 | 系统监控：Spring Boot Actuator + Prometheus |
| Day 101 | 日志系统：Logback 配置 + ELK（可选） |
| Day 102 | 压力测试：JMeter 对关键接口压测 |
| Day 103 | 性能优化：根据压测结果优化 SQL/缓存/线程池 |
| Day 104 | Docker Compose 完整编排：10+ 服务一键启动 |
| Day 105 | 周复盘 |

### 第 16 周：收尾 + 面试准备
| 天 | 任务 |
|----|------|
| Day 106 | 项目 README 完整版（架构图 + 技术栈 + 功能模块 + AI 亮点） |
| Day 107 | 录制项目演示视频（5-8 分钟） |
| Day 108 | 整理项目技术文档（设计决策、踩坑记录） |
| Day 109 | 准备面试项目介绍（3 分钟版本 + 10 分钟版本） |
| Day 110 | Java 八股文梳理（集合、多线程、JVM、Spring） |
| Day 111 | 简历重写（以 Industrial AI Hub 为核心） |
| Day 112 | 最终复盘：ROADMAP-COMPLETE.md + Git tag: v3.0-final |

---

# 附录 A：LeetCode 刷题清单（精简版）

> 每天 1 道，跟着路线走。只刷必须会的，不贪多。

| 编号 | 题目 | 对应学习日 |
|------|------|-----------|
| 1 | 两数之和 | Day 1 |
| 9 | 回文数 | Day 2 |
| 26 | 删除有序数组中的重复项 | Day 3 |
| 66 | 加一 | Day 4 |
| 88 | 合并两个有序数组 | Day 5 |
| 118 | 杨辉三角 | Day 6 |
| 206 | 反转链表 | Day 8 |
| 217 | 存在重复元素 | Day 9 |
| 242 | 有效的字母异位词 | Day 10 |
| 125 | 验证回文串 | Day 11 |
| 14 | 最长公共前缀 | Day 12 |
| 20 | 有效的括号 | Day 13 |
| 21 | 合并两个有序链表 | Day 15 |
| 141 | 环形链表 | Day 16 |
| 160 | 相交链表 | Day 17 |
| 234 | 回文链表 | Day 20 |
| 704 | 二分查找 | Day 22 |
| 232 | 用栈实现队列 | Day 25 |
| 155 | 最小栈 | Day 28 |
| 283 | 移动零 | Day 31 |
| 136 | 只出现一次的数字 | Day 34 |
| 169 | 多数元素 | Day 36 |
| 121 | 买卖股票的最佳时机 | Day 39 |
| 53 | 最大子数组和 | Day 42 |
| 70 | 爬楼梯 | Day 45 |
| 94 | 二叉树的中序遍历 | Day 50 |
| 104 | 二叉树的最大深度 | Day 55 |
| 226 | 翻转二叉树 | Day 60 |
| 15 | 三数之和 | Day 65 |
| 3 | 无重复字符的最长子串 | Day 70 |

> 总共 30 道，覆盖数组、链表、栈、哈希表、树、动态规划入门。

---

# 附录 B：每天 Checklist 模板

```markdown
# Day XXX - YYYY/MM/DD

## 今日目标
- [ ] 任务 1
- [ ] 任务 2
- [ ] 任务 3

## 编码时长
__ 小时

## 代码行数
__ 行

## LeetCode
- [ ] 完成题目：___

## Git
- [ ] commit 并 push

## 明日计划
1.
2.
```

---

# 附录 C：游戏控制策略

1. **卸载手机所有游戏**，只保留电脑上的（控制入口）
2. **游戏只在 21:00-22:00** 这个窗口玩，设闹钟
3. **如果当天没完成最低底线**（2 个编码块），当天不能游戏
4. **第一周最危险**：前 3 天最难熬，扛过去大脑就适应了
5. **替代方案**：想打游戏时，先去散步 10 分钟，回来再决定

---

> **最后提醒**：这个计划是路线图，不是圣经。如果某天卡住了，不要跳过——在 Codex 里告诉我，我们一起解决。你的任务不是完美执行，而是每天都有产出。
> 
> 现在，从 Day 1 开始。

## 技术栈更新（2026-07-11 决定）

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 25 LTS (Temurin) | Oracle LTS 节奏 17→21→25，2025年9月发布 |
| Spring Boot | 3.5.x | 当前最新稳定版，JDK 25 兼容 |
| Maven | 3.9.6 | 已就绪 |
| MySQL | 8.0+ | - |
