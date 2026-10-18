# Industrial AI Hub Java 后端面试指南

> 适用场景：Java 后端一面、二面中的集合、并发、JVM、Spring 高频问答
> 事实基线：Day 112 / 2026-10-18
> 核心原则：先讲通用原理，再给项目证据；项目没有做过的调优和架构，不包装成经历

---

## 一、怎么使用这份文档

不要把下面的文字当成需要逐字背诵的“标准答案”。面试官真正想确认的是三件事：

1. 你是否理解机制的因果关系，而不是只记住结论；
2. 你是否知道方案的成本、适用条件和失效边界；
3. 你是否能把手上的项目当作证据，而不是把通用知识硬贴成项目经历。

### 1.1 事实等级

本文所有项目表述按三档标注：

| 等级 | 含义 | 回答方式 |
|------|------|----------|
| **A：项目实测** | 有代码、测试或真实验证记录 | “项目里这样落地，证据是……” |
| **B：项目落地但有限制** | 项目确实使用，但只适用于当前单机/学习场景 | “当前这样做，边界是……” |
| **C：仅通用原理** | 项目没有专项实现或实测 | “原理上是这样，但我没有在项目中做专项调优” |

典型边界：

- 项目是 **Java 25 + Spring Boot 3.5.0**，但没有使用虚拟线程；
- 项目使用 JDK 默认 GC，**没有做 `-Xmx`、GC 选型或 JVM 参数调优**；
- `SimpleVectorStore` 是单机文件向量库，**不是 Qdrant**；
- `SseEmitterRegistry`、Guava 限流桶、日志节流状态都是 **per-JVM**；
- `AlarmConsumer` 的 `ConcurrentHashMap` 只是进程内集合，**不能称为跨实例可靠幂等**；
- 项目有 Redis SETNX 幂等和 RabbitMQ 手动 ACK，但**没有分布式事务**。

### 1.2 五段式答题法

每个问题尽量按下面顺序回答，通常控制在 60-120 秒：

1. **定义**：一句话说清它是什么；
2. **原理**：关键数据结构、线程模型或执行流程；
3. **取舍**：为什么用它，代价是什么；
4. **项目证据**：仓库里哪段代码或哪次验证用过；
5. **边界**：当前规模、测试缺口或后续演进。

示例：

> `ConcurrentHashMap` 是线程安全哈希表。JDK 8 以后主要依靠 CAS、桶级别 `synchronized` 和协助扩容提升并发度，读操作通常不加锁。项目里的 SSE 会话表和限流桶用了它，因为它适合多线程注册、查询和删除。但集合本身线程安全，不代表多个操作的组合原子，也不能让状态跨 JVM 共享，所以 SSE 路由和限流目前在单副本内有效。

这个答案比“底层是数组加链表加红黑树”更完整，也自然暴露了真实边界。

---

## 二、集合高频题

### Q1. `ArrayList` 和 `LinkedList` 怎么选？

**结论**：绝大多数业务场景优先 `ArrayList`。

`ArrayList` 基于连续数组，随机访问是 O(1)，尾部追加摊销 O(1)，中间插入/删除需要移动元素。扩容通常约为原容量的 1.5 倍，并把旧数组复制到新数组。`LinkedList` 是双向链表，已知节点位置时插入/删除是 O(1)，但按索引查找是 O(n)，节点对象和指针也带来额外内存与缓存不友好。

项目里列表型 DTO、Agent 消息历史、工具调用轨迹主要使用 `ArrayList`，因为它们以追加、遍历和结束后的整体返回为主，没有大量“持有一个节点反复在中间删除”的需求。**A：项目实测/代码可见。**

追问时要补一句：`LinkedList` 的“插入快”只有在已经拿到目标节点时成立；按索引插入仍要先遍历。

### Q2. `HashMap` 的底层结构是什么？

JDK 8 以后，`HashMap` 的核心是 `Node[] table`：

- 首次插入时懒初始化数组；
- `hash` 扰动把高位差异混入低位；
- 用 `(n - 1) & hash` 定位桶；
- 单桶先链表，达到树化条件后转红黑树；
- 默认负载因子 0.75，元素数超过阈值后扩容为约两倍；
- 链表树化还要求表容量至少为 64，否则优先扩容。

常见追问：

- 为什么容量是 2 的幂？因为可以用位运算快速取桶下标，扩容时还能通过高位判断元素留在原索引还是移动 `oldCap`。
- 为什么负载因子是 0.75？是在空间利用率与冲突概率之间折中，不是绝对最优值。
- 红黑树解决什么？当恶意哈希或坏哈希让单桶链表变长时，把最坏查找从 O(n) 改善到 O(log n)。

**C：通用原理。** 项目没有为 `HashMap` 做专项基准测试。

### Q3. `HashMap`、`Hashtable`、`ConcurrentHashMap` 的区别？

| 对比项 | `HashMap` | `Hashtable` | `ConcurrentHashMap` |
|--------|-----------|-------------|---------------------|
| 线程安全 | 否 | 是 | 是 |
| 并发方式 | 无 | 方法级同步，粒度粗 | CAS + 桶级同步 |
| null | key/value 都允许一个/多个 null | 不允许 | key/value 都不允许 null |
| 迭代语义 | fail-fast | 枚举器不抛 `ConcurrentModificationException` | 弱一致迭代 |
| 常见用途 | 单线程或外部隔离 | 旧代码 | 多线程共享映射 |

`ConcurrentHashMap` 的 `get` 大多无锁，修改空桶用 CAS，修改冲突桶对桶头节点加锁，扩容时多线程可以协助迁移。`size()` 是并发环境下的估算值，不能拿去和业务计数做强一致判断。

项目中：

- `SseEmitterRegistry` 使用 `ConcurrentHashMap<Long, SseEmitterSession>` 保存单 JVM 的 SSE 会话；
- `RateLimitInterceptor` 使用 `ConcurrentHashMap<String, PathLimiter>` 保存路径限流桶；
- `SimpleVectorStore` 使用 `ConcurrentHashMap<String, Entry>` 保存向量条目。

**A/B：项目代码可见，但都只解决单 JVM 内并发。**

### Q4. 既然 `ConcurrentHashMap` 线程安全，为什么还会出现并发问题？

集合线程安全只保证单次 `get`、`put`、`remove` 等操作不会破坏内部结构，不保证“先检查再修改”的组合原子。

错误模式：

```java
if (!map.containsKey(key)) {
    map.put(key, createValue()); // 两个线程可能都进入这里
}
```

应使用：

```java
map.computeIfAbsent(key, ignored -> createValue());
```

普通写入去重可使用 `putIfAbsent`，累加可使用 `merge` 或 `LongAdder`/`AtomicLong`。

项目中的 `RateLimitInterceptor` 用 `computeIfAbsent` 原子创建路径桶；`AlarmConsumer` 用 `putIfAbsent(deliveryTag, true)` 检查是否见过标签。但后者并没有解决跨实例幂等，详见 Q12。

### Q5. `fail-fast` 是什么？它能保证并发安全吗？

`ArrayList`、`HashMap` 等集合的迭代器发现结构被意外修改时，可能抛 `ConcurrentModificationException`，这叫 fail-fast。

它有两个重要限制：

- 它是**检测提示**，不是严格契约，不保证所有并发修改都能检测到；
- fail-safe/弱一致迭代也不代表业务数据一定是最新快照。

项目中 `SseEmitterRegistry.findAll()` 返回 `new ArrayList<>(sessions.values())`，作用是复制当前会话视图，让后续推送遍历时不受注册/删除影响。这个快照能降低遍历期结构变化带来的问题，但不是全局一致性快照。

### Q6. `equals()` 和 `hashCode()` 的契约是什么？

- `equals` 为 true 的两个对象，`hashCode` 必须相同；
- `hashCode` 相同的对象，`equals` 不一定为 true；
- 作为 `HashMap`/`HashSet` 的 key 时，如果修改了参与 `hashCode` 的字段，原桶位置可能找不到，造成“逻辑泄漏”。

项目中的业务对象和 DTO 大多不直接作为哈希键；集合、向量条目等 key 优先使用稳定的 `Long` 或 `String` ID。这是避免可变业务对象破坏哈希语义的保守选择。

### Q7. `List.of()`、`List.copyOf()` 和普通 `ArrayList` 有什么区别？

`List.of()` 创建结构不可变列表，不接受 null，修改方法会抛异常。`List.copyOf()` 常作为防御性拷贝，避免调用方随后修改原集合；它不是“深度不可变”，元素对象内部仍可能可变。

`SseEmitterSession` 构造时对 `siteIds` 做 `List.copyOf(siteIds)`，防止注册后外部列表变化改变权限路由，这是集合边界上的防御性设计。

`List.of()` 也不是排序集合，遇到重复站点 ID 不会自动去重；如果需要唯一性，应显式使用 `Set` 或业务校验。

### Q8. 为什么项目有些地方用 `LinkedHashMap`？

`HashMap` 不承诺业务遍历顺序，`LinkedHashMap` 维护插入顺序或访问顺序。项目在构造 MQTT 下行命令、字段映射和工具结果的 Map 时使用 `LinkedHashMap`，主要目的是让字段顺序稳定、日志和 JSON 更易读，而不是为了性能。

注意：`LinkedHashMap` 可以通过 access-order 实现简化 LRU，但这不是线程安全的；高并发缓存通常使用 Caffeine 等成熟方案。项目当前没有用 `LinkedHashMap` 作为生产级 LRU。

### Q9. 集合面试最容易失分的边界是什么？

不要用一句“线程安全”结束回答。至少补三点：

1. 复合操作是否原子；
2. 迭代期间修改是什么语义；
3. 状态是否跨进程、是否有容量上限和清理策略。

项目里 `ConcurrentHashMap` 很常见，但它不能自动解决内存增长。`SseEmitterRegistry` 依赖 emitter 回调与 `@PreDestroy` 清理；`AlarmConsumer.processedTags` 当前没有清理或容量上限，因此只能视为学习链路的进程内去重。

---

## 三、并发与 JUC 高频题

### Q10. 创建线程有哪几种方式？实际项目怎么选？

常见方式：

- 实现 `Runnable`；
- 实现 `Callable`，通过 `Future` 获取结果；
- 继承 `Thread`；
- 使用线程池、`CompletableFuture` 或框架托管的异步机制。

生产代码优先让框架或线程池管理生命周期，不直接 `new Thread()`。项目用 `@Async("loginAuditExecutor")` 执行登录审计，用 RabbitMQ listener 容器并发消费消息，都没有在业务代码里手工创建裸线程。

**A：项目代码可见。**

### Q11. 线程池的核心参数怎么理解？

`ThreadPoolExecutor` 的主流程：

1. 运行线程数小于 `corePoolSize`，创建核心线程；
2. 否则进入工作队列；
3. 队列满且线程数小于 `maximumPoolSize`，创建非核心线程；
4. 仍无法接收，执行拒绝策略；
5. 空闲的非核心线程超过 `keepAliveTime` 后回收，是否回收核心线程取决于 `allowCoreThreadTimeOut`。

项目登录审计线程池配置为：

- `corePoolSize=2`；
- `maxPoolSize=4`；
- `queueCapacity=1000`；
- 线程名前缀 `login-audit-`。

这个配置把登录审计与业务请求隔离，防止审计慢写拖住登录主流程。但队列 1000 意味着突发时会先排队，线程不会立刻扩到 4；队列积压会增加审计延迟和内存占用。项目当前没有针对该 executor 做容量压测、监控或自定义拒绝策略，不能声称参数经过压测调优。**B：项目落地但有限制。**

### Q12. `@Async` 的失效场景有哪些？

常见失效原因：

- 同类内部方法直接调用，绕过 Spring 代理；
- 方法不是可代理的 public 方法；
- 所在对象不是 Spring Bean；
- 异常在异步线程中被吞掉，调用方看不到；
- 线程池队列满后提交被拒绝；
- 异步方法内直接依赖调用方事务上下文，但事务线程与异步线程并不共享同一个连接/事务。

`LoginAuditService.record()` 是独立 Bean 的 public 方法，由其他 Bean 通过代理调用，符合 `@Async` 生效条件。方法内部捕获异常并记录日志，目标是“审计失败不影响登录结果”。这适合登录审计，但不适合必须可靠落库的金融流水；后者需要消息队列、本地消息表或事务性 Outbox。

### Q13. 线程池参数是不是越大越好？

不是。线程过多会带来：

- 栈内存和线程对象开销；
- CPU 上下文切换；
- 数据库连接池竞争；
- 下游被更多并发请求压垮；
- 队列、线程和连接三者互相掩盖瓶颈。

线程池大小要结合任务类型：

- CPU 密集型通常接近 CPU 核数；
- I/O 密集型可适度增大，但不能超过下游容量；
- 混合任务应隔离线程池；
- 最终以压测、等待时间、队列长度和拒绝率为准，不能只套公式。

项目 Day 102/103 压测证明写路径尾部抖动时没有慢 SQL、Hikari pending 为 0、GC 最大暂停 27ms，因此没有做无证据的连接池或线程扩容。

### Q14. `synchronized`、`ReentrantLock`、`volatile` 怎么区分？

| 工具 | 解决的问题 | 注意点 |
|------|------------|--------|
| `synchronized` | 互斥 + 可见性 + 可重入 | JVM 管理锁，代码简单 |
| `ReentrantLock` | 可中断、超时、公平锁、条件队列等能力 | 必须 `try/finally` 解锁 |
| `volatile` | 可见性 + 禁止相关重排序 | 不保证 `i++` 原子性 |

`SimpleVectorStore.save()` 使用实例方法级 `synchronized`，保证同一实例同一时间只执行一次文件持久化；Map 本身已使用 `ConcurrentHashMap`。这能防止文件写入重入，但 `synchronized` 只在单 JVM 有效，不能让多个进程安全写同一个文件。

`MqttConfig.MqttLifecycle` 的 `start()`/`stop()` 使用 `synchronized`，并通过 `volatile` 保存 `running` 和客户端引用，目标是把生命周期切换和回调读取串行化。

### Q15. `volatile` 能保证线程安全吗？

不能一概而论。`volatile` 保证：

- 写后读的可见性；
- 对同一 volatile 变量的操作具有 happens-before 关系；
- 抑制部分指令重排。

它不保证复合操作原子性。例如 `count++` 仍然是读、加、写三步。计数器应使用 `AtomicLong`/`LongAdder` 或加锁。

`RateLimitInterceptor` 用 `AtomicLong.incrementAndGet()` 统计拒绝次数，用 `compareAndSet()` 竞争窗口记录权，避免多个请求线程同时重复刷 WARN 日志。这里的核心不是“用了原子类就万事大吉”，而是日志节流允许少量竞争失败，最终仍保留窗口内拒绝总数。

### Q16. CAS 是什么？有什么问题？

CAS 是 compare-and-swap：只有当前值等于期望值时才更新成功，否则失败重试。Java 通过 `Atomic*`、`VarHandle` 等提供相关能力。

常见问题：

- 自旋过多消耗 CPU；
- ABA 问题；
- 只能保证单个共享变量原子性；
- 高竞争下失败重试成本高。

项目使用 `AtomicLong` 做日志节流 CAS，不涉及需要版本号的 ABA 场景。若业务状态依赖“值相同但历史已变化”，可以使用 `AtomicStampedReference` 或显式版本列。

**A：代码可见；C：ABA 为通用延伸。**

### Q17. 死锁的四个必要条件是什么？怎么避免？

必要条件：

1. 互斥；
2. 持有并等待；
3. 不可抢占；
4. 循环等待。

工程上常用：

- 统一锁顺序；
- 尽量缩小锁粒度；
- 使用带超时的 `tryLock`；
- 避免在持锁期间调用外部 I/O；
- 让锁层级清晰，禁止回调进未知第三方代码；
- 线上抓取 thread dump 分析 `BLOCKED` 和锁持有者。

项目没有专门的死锁演练。可以说原理和排查方法，但不能说“项目做过死锁压测”。

### Q18. 线程安全的单例 Bean 应该怎么设计？

Spring 默认 Bean 作用域是 singleton。Service、Controller、Config 通常会被多个请求线程同时访问，因此关键是**让 Bean 无状态，或者让共享状态有明确的并发控制**。

项目模式：

- Service 大多以构造器注入 Mapper/Client，方法内创建局部变量，不在请求方法间共享可变状态；
- `SseEmitterRegistry` 等确实需要共享状态的类使用 `ConcurrentHashMap`；
- `RateLimitInterceptor` 使用 `AtomicLong`；
- `McpInspectionSession` 使用 `AtomicInteger`；
- `MqttDeviceDataIngestService.commandGateway` 使用 `volatile`。

不要笼统说“Spring Bean 是线程安全的”。Spring 只负责创建和注入，不会自动给字段加锁。

### Q19. `ThreadLocal` 有什么用？项目哪里用了？

`ThreadLocal` 为每个线程保存独立副本，适合请求上下文、事务同步和日志 MDC。它的风险是：

- 线程池复用线程，不清理会串数据；
- value 过大会造成内存泄漏；
- 异步切换线程后不会自动传递。

项目 `TraceIdFilter` 把 traceId 写入 SLF4J MDC，并在 `finally` 中 `MDC.remove`，避免 Tomcat 线程复用后把上一个请求的 traceId 带给下一个请求。异步线程不会天然继承 MDC，因此跨异步链路的 trace 透传仍需额外设计。

**A：项目代码可验证。**

### Q20. RabbitMQ 的 `concurrency=2-4` 是不是 Java 线程数？

它是 Spring AMQP listener container 的并发消费者配置，底层确实会使用消费线程，但不能简单等同于“项目线程池有 2-4 个线程”。多个 listener、多个 container、业务线程池、Tomcat 线程会分别占用线程资源。

项目报警消费者配置为 `concurrency=2-4`，巡检消费者配置为 `1-2`，二者都是手动 ACK，失败 `requeue=false` 后进入 DLQ。消费并发应根据消息处理时间、数据库容量、外部服务限流和 DLQ 策略设置，不是越高越好。

### Q21. 为什么需要线程命名和可观测性？

线程名能让日志、thread dump、Micrometer 指标和 APM 快速定位执行者。项目给登录审计线程统一加 `login-audit-` 前缀；日志体系还用 traceId 串起同一请求。

如果线程全部叫 `pool-1-thread-1`，线上只能看到“某个线程慢”，很难判断是登录审计、RabbitMQ 消费、Tomcat 还是 Agent 调用。线程名、traceId、指标标签是并发问题的基础证据。

### Q22. 项目中哪些并发结论必须保守表述？

必须区分：

- `ConcurrentHashMap` 是单 JVM 容器安全，不是分布式协调；
- `AlarmConsumer` 的 `deliveryTag` 去重是进程内、无清理、无跨实例保证；
- Guava `RateLimiter` 桶在单 JVM 内，重启或多副本会重新分配额度；
- `SseEmitterRegistry` 是进程内 emitter 表，多副本之间没有广播；
- `@Async` 失败不会自动变成可靠消息，必须接受“审计可能丢失”；
- 项目没有实现分布式锁的统一事务编排，Redis 锁/SETNX 也不能替代事务。

这些边界比“我们用了线程安全集合”更能证明工程判断。

### Q23. `CompletableFuture`、`Future` 和响应式编程怎么回答？

`Future` 主要表示异步计算结果，`get()` 会阻塞；`CompletableFuture` 支持链式组合、异常处理和回调；响应式编程强调非阻塞背压，但会改变线程模型和调试方式。

项目当前主链路是 Spring MVC + 平台线程池 + RabbitMQ 异步消费，没有引入 WebFlux 或 Reactor。不要因为内部异步调用用了 Future/回调，就宣称项目是响应式架构。若要演进，应先用压测证明线程瓶颈，再评估虚拟线程、异步 Servlet 或 WebFlux。

---

## 四、JVM 高频题

### Q24. JVM 运行时内存区域怎么划分？

常见划分：

- **线程私有**：程序计数器、Java 虚拟机栈、本地方法栈；
- **线程共享**：Java 堆、方法区（HotSpot 常以 Metaspace 实现）；
- **堆外**：直接内存、本地库分配的内存。

堆主要存对象实例；栈帧存局部变量表、操作数栈、动态链接和返回地址；Metaspace 存类元数据、常量池元数据和 JIT 相关信息。JDK 8 以后 PermGen 被移除，字符串常量池也已移到堆中。

项目 Dockerfile 只执行 `java -jar app.jar`，没有配置 `-Xmx`、Metaspace 上限或特定 GC 参数，因此回答时应说“依赖 JDK 容器的默认内存与 GC 策略”，不能说做过精细 JVM 参数设计。**C：通用原理 + 项目未调优。**

### Q25. 对象创建和分配过程是什么？

简化流程：

1. 类加载和初始化完成；
2. 线程分配 TLAB，优先在 TLAB 内快速分配；
3. TLAB 不足时走共享分配路径；
4. 对象头、实例字段、对齐填充形成最终内存布局；
5. 构造器执行；
6. 可能发生栈上替换、标量替换等 JIT 优化。

“对象一定在堆上”是过度简化；逃逸分析可能让对象分配被优化掉，但这是 JVM 实现和 JIT 行为，不是业务代码可依赖的语义。

### Q26. 什么是 GC Root？怎么判断对象可回收？

常见 GC Roots：

- 线程栈中的局部变量和参数；
- 活跃线程；
- 类的静态字段；
- JNI 引用；
- 同步监视器；
- JVM 内部关键引用。

现代 HotSpot 主要使用可达性分析，从 GC Roots 找对象图，不可达对象才可回收；“引用计数归零”不是 Java 的主要判断方式。

软引用、弱引用、虚引用也不能简单概括为“自动缓存/自动清理”，它们要和引用队列、回收时机一起回答。

### Q27. 分代回收和 G1 是什么关系？

分代假设：

- 大多数对象很快死亡；
- 活得越久的对象越可能继续存活。

传统分代收集器分为年轻代和老年代。G1 把堆分成 Region，仍保留年轻代/老年代语义，但以 Region 为回收单位，采用停顿预测模型，优先回收收益高的 Region。

G1 的 Young GC 是年轻代回收；Mixed GC 会回收部分老年代 Region；并发标记周期用于识别老年代垃圾。具体行为受堆大小、`MaxGCPauseMillis`、对象晋升速度、Region 可用量等影响。

项目 Day 103 观测到 `jvm_gc_pause_seconds_max` 为 **27ms**，标签显示 G1 Young。这个数字只能说明那一次压测样本中单次暂停不大，不能推导出生产 SLO，更不能证明项目做过 GC 调优。**B：有观测，无调优。**

### Q28. 为什么不能只看堆使用率就判断内存泄漏？

Java 内存问题至少分：

- 堆泄漏：对象仍被无意引用，GC 无法回收；
- Metaspace 增长：类加载器/动态代理持续创建；
- 直接内存或 native 内存增长：堆指标看不出来；
- 线程过多：栈和本地内存消耗；
- 容器 cgroup 限制：JVM 看到的内存与宿主机进程行为不同。

排查顺序通常是：确认 OOM 类型 -> 看 GC 前后存活对象 -> 抓 heap dump/Metaspace/线程 -> 对比对象数量和引用链 -> 复现并验证修复。不能只凭 `jmap -histo` 里某个类很多就宣布泄漏。

项目没有发生生产级堆 OOM 演练；只能回答排查方法，不应编造“线上定位过全量 Full GC”的经历。

### Q29. 类加载过程是什么？双亲委派解决什么问题？

类加载大致分：

1. 加载：查找字节码，生成 Class 对象；
2. 验证：格式、语义、字节码、符号引用校验；
3. 准备：分配静态字段内存并设初始值；
4. 解析：符号引用转直接引用；
5. 初始化：执行 `<clinit>`，静态赋值和静态块按源码顺序执行。

双亲委派的核心是向上委托加载、父加载器无法完成时再尝试自己加载，避免同一个核心类被不同加载器加载出多个不兼容版本，也保护 JDK 核心类边界。

Spring Boot 可执行 jar 使用自己的 launcher 和嵌套 jar 类加载机制，细节与普通 `URLClassLoader` 不完全相同。项目中可以讲“Boot 打包引入了自己的类加载层次”，但不要说项目手写过自定义 ClassLoader。

### Q30. JVM 问题怎么观测和定位？

常用手段：

- `jps` 找 Java 进程；
- `jcmd` 查看 VM 信息、线程、堆、类直方图、GC；
- `jstat` 看 GC 频率、容量和耗时；
- thread dump 看死锁、阻塞和线程栈；
- heap dump 看对象分布和引用链；
- JFR 做低开销运行时事件记录；
- Micrometer/Actuator/Prometheus 长期观测 JVM 指标；
- APM 或日志系统串业务 traceId。

项目的可观测证据是：Actuator/Prometheus 暴露 JVM/HTTP 指标，Micrometer 记录 GC、Hikari 和业务计数器，日志有 traceId。Day 103 用这些指标排除了 SQL、连接池和 GC 作为写入尾部抖动的主要解释。**A：项目实测。**

### Q31. 如果让你给项目做 JVM 调优，你会怎么做？

不能先报一串参数，应该先问目标：

1. 是吞吐、延迟还是内存占用问题；
2. 峰值 QPS、对象分配率、堆使用曲线是什么；
3. 延迟尾部是否伴随 GC pause、Safepoint、线程阻塞或 I/O；
4. 容器内存限制和 JVM 实际可用内存是否一致；
5. 哪个指标能被优化前后稳定复现。

然后可以在隔离环境做小步实验，例如调整堆上限或 `MaxGCPauseMillis`，并用相同 JMeter 场景比较 p95/p99、GC pause、CPU、内存和错误率。项目当前没有足够证据，因此正确回答是“先建立基线和目标，再做对照实验”，而不是声称已经调过 G1。

---

## 五、Spring / Spring Boot 高频题

### Q32. IoC、DI、AOP 分别是什么？

- **IoC**：对象的创建和依赖关系交给容器管理，业务代码不自己 new 依赖；
- **DI**：容器把构造器参数、setter 或字段注入到对象中；
- **AOP**：把日志、事务、权限等横切关注点织入方法调用。

项目以构造器注入为主，`WebMvcConfig`、`CacheConfig` 的依赖都由 Spring 注入。`OperationLogAspect` 用 `@Around` 记录操作审计，`@Transactional` 和 `@Cacheable` 也依赖代理/AOP 机制。

项目不使用字段 `@Autowired`，原因是依赖关系显式、便于单元测试和发现循环依赖。**A：项目代码可见。**

### Q33. Bean 生命周期有哪些关键阶段？

简化版：

1. 实例化；
2. 属性填充和依赖注入；
3. Aware 回调；
4. `BeanPostProcessor#postProcessBeforeInitialization`；
5. `@PostConstruct` / `InitializingBean` / init-method；
6. `BeanPostProcessor#postProcessAfterInitialization`（代理常在这里产生）；
7. 使用；
8. `@PreDestroy` / `DisposableBean` / destroy-method。

项目里：

- `SimpleVectorStore` 用 `@PostConstruct` 从磁盘加载，用 `@PreDestroy` 刷盘；
- `SseEmitterRegistry` 用 `@PreDestroy` 关闭全部 SSE emitter；
- `MqttConfig.MqttLifecycle` 通过 `SmartLifecycle` 控制连接启停阶段。

回答时要说明：`@PostConstruct` 属于 Bean 初始化回调，`SmartLifecycle` 属于 Spring 容器生命周期编排，二者不是同一层级。

### Q34. Spring 默认单例 Bean 会不会有并发问题？

会，如果 Bean 持有可变状态。容器保证创建和注入过程，不会自动保证业务方法线程安全。

项目中：

- `MqttDeviceDataIngestService` 有 `volatile commandGateway`，由 MQTT 生命周期在运行时注入和清空；
- `SseEmitterRegistry` 的共享 Map 使用并发容器；
- `RateLimitInterceptor` 的桶 Map 使用并发容器；
- 大多数 Service 不保存请求级可变字段，更多使用局部变量。

面试时不要只说“Spring 单例线程不安全”，应补充“关键看状态设计，不能通过把 Bean 改成 prototype 来逃避资源管理问题”。

### Q35. 构造器注入和字段注入怎么取舍？

构造器注入：

- 依赖不可变，通常用 `final`；
- 对象创建后依赖完整；
- 便于单元测试；
- 循环依赖更早暴露。

字段注入：

- 代码短，但隐藏依赖；
- 反射注入，测试和生命周期不直观；
- 容易掩盖循环依赖。

Boot 2.6+ 默认禁止循环引用。项目 `MqttConfig` 为避免构造期循环依赖，采用“先建 MQTT 生命周期，连接成功后通过 `setCommandGateway` 运行时注入”的方式；这是在明确了解生命周期后的受控方案，不是随手使用 `@Lazy`。

### Q36. Spring AOP 为什么会“同类调用失效”？

Spring AOP 常见实现是代理：外部调用先进入代理，代理再执行通知和目标方法。同类内部 `this.method()` 不经过代理，因此 `@Transactional`、`@Cacheable`、`@Async`、自定义切面都可能失效。

项目里 `LoginAuditService.record()` 由外部 Bean 调用，能够经过代理；`OperationLogAspect` 拦截带 `@OperationLog` 的代理调用。不要把“有注解”误解成“任何调用都自动生效”。

补充：代理方式可以是 JDK 动态代理或 CGLIB，Spring Boot 默认倾向 CGLIB，但最终要看配置和 Bean 类型。

### Q37. `@Transactional` 什么时候不生效或不会回滚？

常见情况：

- 同类内部调用，绕过代理；
- 方法不是 public（不同代理/配置下行为不同，保守按风险处理）；
- 异常被方法内部捕获；
- 抛出受检异常但未配置 `rollbackFor`；
- Bean 不是 Spring 管理；
- 多线程中开启新事务，父事务不会跟着回滚；
- 传播级别不支持当前事务；
- 数据库表引擎、连接或操作本身不支持事务。

项目在 `UserService` 的关键多表写操作和 `AuthService` 的登录状态变更上使用 `@Transactional`；它不是“所有 Service 方法自动安全”的魔法，也不负责缓存、Redis、MQTT 或 RabbitMQ 的原子性。

### Q38. `@Cacheable`、`@CacheEvict` 和数据库事务能保证一致吗？

不能直接保证。缓存通常不是数据库事务的一部分：

- 更新数据库成功但淘汰缓存失败，可能读到旧值；
- 先淘汰缓存再读数据库，存在并发回填；
- 多实例本地缓存无法统一失效；
- Redis 序列化配置错误会导致读缓存 ClassCastException。

项目 `CacheConfig` 在 dev/prod 用 Redis Cache，默认 TTL 30 分钟，test 用 `ConcurrentMapCacheManager`；缓存值注册 `JavaTimeModule`，用受限 `BasicPolymorphicTypeValidator` 允许项目 DTO/entity 和常见时间、集合类型，避免无限制多态反序列化风险。

`UserService` 的写方法配合 `@CacheEvict`，能覆盖常规更新路径，但没有使用事务消息、Outbox 或双写一致性协议，所以准确说法是“注解式缓存失效”，不是“数据库缓存强一致”。

### Q39. Spring Cache 的 key、TTL 和序列化怎么设计？

- key 应稳定、可读，并包含权限维度，例如项目设备详情用 `#userId + ':' + #id`，避免跨用户泄露；
- TTL 按数据变化频率和可接受陈旧时间设置，项目默认 30 分钟；
- 序列化要能处理时间类型，读侧和写侧配置必须一致；
- 不要使用不限制类型的反序列化器；
- 空值策略要明确，项目关闭 null 缓存。

测试 profile 切到进程内 CacheManager，是为了减少单测对外部 Redis 的依赖；这不等于生产缓存行为已经由单测证明，Redis 序列化和实际网络仍需要集成测试覆盖。

### Q40. Filter 和 Interceptor 的执行顺序、职责怎么区分？

Filter 属于 Servlet 容器链，位于 DispatcherServlet 之前，能处理所有请求，但通常拿不到 Spring MVC 的 `HandlerMethod`。Interceptor 位于 DispatcherServlet 内，在 HandlerMapping 之后执行，能知道具体 Controller 方法，适合角色注解和业务路由。

项目 Web 层顺序：

1. `TraceIdFilter`：order `-2`，尽早生成/透传 traceId；
2. `McpAccessFilter`：order `0`，只保护 `/mcp/sse`、`/mcp/message`；
3. `JwtAuthFilter`：order `1`，主要作用于 `/api/*`。

MVC Interceptor 顺序：

1. `AiRateLimitInterceptor`：order `-1`，保护 AI/MCP/Agent 等高成本接口；
2. `RateLimitInterceptor`：order `0`，全 `/api/**` 通用流量保护；
3. `AuthInterceptor`：order `1`，先认证再按 `@RequireRole` 做角色校验。

回答时要强调：Filter 抛出的异常通常不会自动进入 `@RestControllerAdvice`，所以 `JwtAuthFilter` 自己写 401 JSON；Controller 内的异常才由全局异常处理器统一转换。

### Q41. `@RestControllerAdvice` 能处理哪些异常？

项目 `GlobalExceptionHandler` 覆盖：

- `BusinessException`：映射到业务错误码；
- `MethodArgumentNotValidException`：`@Valid` 字段校验失败，400；
- `ConstraintViolationException`：方法参数约束失败，400；
- `MissingServletRequestParameterException`：缺少参数，400；
- `MethodArgumentTypeMismatchException`：类型转换失败，400；
- `HttpMessageNotReadableException`：畸形 JSON/缺失请求体，400；
- 其他未捕获异常：隐藏内部细节，返回 500，记录堆栈。

Day 104 修复过一个真实问题：畸形 JSON 原先落入 500 兜底，污染 ERROR 日志；新增 `HttpMessageNotReadableException` 后容器实测返回 400。这个案例很适合回答“异常分类为什么要精确”。

### Q42. `@ConditionalOnProperty`、`@Profile`、`SmartLifecycle` 怎么配合？

项目 `MqttConfig`：

- `@Profile("!test")`：测试环境不创建任何 MQTT Socket；
- `@ConditionalOnProperty(prefix="mqtt", name="enabled", havingValue="true")`：只有显式开启才创建客户端；
- `MqttLifecycle` 实现 `SmartLifecycle`：容器启动到最后一个阶段再连接，关闭时先断开；
- 连接成功后才注入 `commandGateway`，避免构造期循环依赖和未连接时误发；
- Paho 回调内捕获异常，避免异常逃逸到客户端回调线程。

这个组合体现的是“外部依赖可选、启动可预测、关闭有序”，比单纯写一个 `@Bean` 更完整。DeepSeek 也是类似的 opt-in 思路，未配置 API Key 时核心业务仍应正常工作。

### Q43. Spring Boot 自动配置原理是什么？

大致过程：

1. `@SpringBootApplication` 组合了组件扫描和 `@EnableAutoConfiguration`；
2. 自动配置根据 classpath、已有 Bean、配置属性等条件筛选；
3. 条件注解（如 `@ConditionalOnClass`、`@ConditionalOnMissingBean`、`@ConditionalOnProperty`）决定是否生效；
4. Spring Boot 3 通过自动配置导入清单加载候选项；
5. 用户自定义 Bean 通常通过 `@ConditionalOnMissingBean` 覆盖默认配置。

项目自身更常见的面试切入点是 `@ConditionalOnProperty` 控制 MQTT、`@Profile` 控制测试缓存和外部连接，以及 `application.yml` 中的属性覆盖。不要把“自动配置”理解成“所有 Bean 都会被创建”，条件不满足就不会生效。

### Q44. Spring 事务传播怎么回答？

至少要能说出：

- `REQUIRED`：有事务加入，没有就新建，默认且最常用；
- `REQUIRES_NEW`：挂起当前事务，新开事务；
- `NESTED`：嵌套保存点，依赖底层事务能力；
- `SUPPORTS`：有则加入，没有就非事务；
- `NOT_SUPPORTED`：挂起事务执行；
- `MANDATORY`：必须已有事务；
- `NEVER`：不能有事务。

项目没有专门的传播行为压测。面试时先讲清“传播控制的是事务边界，不是锁边界；事务隔离级别也不等于业务幂等”。如果需要跨 MySQL、Redis、RabbitMQ 原子性，单靠 `@Transactional` 不够，需要使用 Outbox/事务消息/幂等消费等方案。

### Q45. 你如何解释项目中的 Spring 组件边界？

可以按层说：

- Controller 负责 HTTP 边界、参数校验和统一响应；
- Service 负责业务规则、事务边界和权限事实校验；
- Mapper 负责数据访问；
- Filter/Interceptor 处理入口横切关注点；
- AOP 处理审计；
- Config 负责线程池、缓存、MQTT 和 MVC 编排；
- `GlobalExceptionHandler` 负责异常到 API 契约的映射。

面试官追问“为什么不在 Controller 里做所有事”时，回答“为了让事务、权限、异常、审计有明确归属，并让 Service 可测试”，不要只背分层名词。

---

## 六、项目代码映射表

| 主题 | 项目证据 | 可以怎么说 | 不能说 |
|------|----------|------------|--------|
| 集合并发 | `backend/src/main/java/dev/reboot/mq/SseEmitterRegistry.java` | CHM 保存单 JVM SSE 会话，查询返回快照，生命周期清理 | “SSE 已经多副本水平扩展” |
| 集合并发 | `backend/src/main/java/dev/reboot/security/RateLimitInterceptor.java` | CHM 路径桶 + `computeIfAbsent`，AtomicLong 做日志节流 | “限流是 Redis 全局限流” |
| 集合并发 | `backend/src/main/java/dev/reboot/rag/SimpleVectorStore.java` | CHM 存条目，`synchronized` 串行化文件写入 | “这个项目用了 Qdrant/生产向量库” |
| 消息幂等 | `backend/src/main/java/dev/reboot/service/MqttDeviceDataIngestService.java` | 字段级 Redis SETNX + 24h TTL；Redis 异常时宁可重复不丢数据 | “所有消息去重都由 RabbitMQ 保证” |
| Rabbit 去重 | `backend/src/main/java/dev/reboot/mq/AlarmConsumer.java` | 进程内 `putIfAbsent(deliveryTag)`，未清理，不能跨实例 | “deliveryTag 可作为全局业务幂等键” |
| 线程池 | `backend/src/main/java/dev/reboot/config/AsyncConfig.java` | 登录审计隔离线程池：2/4/1000，命名清晰 | “这几个值经过容量压测最优” |
| 异步 | `backend/src/main/java/dev/reboot/service/LoginAuditService.java` | `@Async` 异步审计，异常不影响登录主流程 | “审计绝对不会丢” |
| MQ 消费 | `backend/src/main/java/dev/reboot/mq/AlarmConsumer.java`、`InspectionReportConsumer.java` | 手动 ACK；失败 nack 不重入队，进 DLQ；并发度因业务不同 | “失败会自动可靠重试” |
| JVM | `backend/Dockerfile` | JDK 25 容器运行，只执行 `java -jar`，没有堆/GC 参数 | “做过 JVM GC 调优” |
| JVM 观测 | Day 103 性能报告 + Prometheus/Micrometer | 压测观测到 GC pause max 27ms，辅助排除瓶颈 | “27ms 就是生产 GC SLO” |
| DI/AOP | `WebMvcConfig`、`OperationLogAspect` | 构造器注入、Filter/Interceptor 顺序、Around 审计 | “所有异常都由全局异常处理器处理” |
| 事务/缓存 | `UserService`、`AuthService`、`CacheConfig` | 关键业务事务、注解缓存、Redis TTL 和受限反序列化 | “数据库和缓存强一致” |
| 条件装配 | `MqttConfig` | Profile + 条件属性 + SmartLifecycle，测试不连外部 Broker | “MQTT 永远在线且自动恢复所有场景” |

---

## 七、四个高频“你会怎么排查”场景

### 场景 A：CPU 100%，怎么查？

1. 先确认是容器、JVM 还是宿主机指标；
2. `top -Hp` 找高 CPU 线程，转十六进制查 `jstack`；
3. 看是否在 GC、JIT、业务自旋、正则、序列化或锁竞争；
4. 对照 QPS、GC、线程池队列、下游延迟和最近发布；
5. 抓 JFR/线程栈/火焰图，确认根因后再限流、降级或回滚。

不能说“CPU 高就加线程”，线程可能让竞争和上下文切换更严重。

### 场景 B：接口偶发超时，怎么查？

按一条请求拆：

- 入口：Tomcat 线程是否排队、限流是否等待；
- 服务：锁、线程池、GC、对象分配；
- 数据库：慢 SQL、锁等待、连接池 pending；
- 下游：Redis、MQ、DeepSeek/HTTP 超时；
- 网络：DNS、连接建立、KeepAlive；
- 日志：traceId 能否串起整个调用链。

项目 Day 103 的做法是把 JMeter JTL 按响应码拆分，得到 429 p99 3ms、约 500ms 等待属于放行的 200，再结合慢 SQL 对照组、Hikari pending 和 GC pause 排除错误方向。这个顺序比“先改 SQL/缓存”更可复用。

### 场景 C：Spring 启动失败，怎么查？

1. 看异常链最底层的 `Caused by`；
2. 区分端口占用、数据库/Flyway、Redis/RabbitMQ/MQTT 连接、Bean 创建、配置缺失；
3. 看 `APPLICATION FAILED TO START` 的 Description 与 Action；
4. 检查 profile、环境变量和 `application.yml` 的键是否重复；
5. 必要时只启动最小 profile，逐个排除外部依赖。

项目的已知坑包括：`application.yml` 已有 `management:` 段导致 DuplicateKey；新 Filter 漏标 `@Component` 导致上下文加载失败；常驻实例和临时实例使用同一个 Paho clientId 会互踢。这些都可以作为真实追问素材。

### 场景 D：线上出现重复业务数据，怎么查？

先确认重复层级：

- 设备是否重复发布；
- QoS1 是否重投；
- MQTT 消费是否重复；
- Redis SETNX 是否超时/失败；
- MySQL 是否有唯一约束；
- RabbitMQ 是否重复投递或 DLQ 人工重投；
- 消费端幂等键是否包含足够业务维度。

项目的 MQTT 方案接受“至少一次”，用字段级 Redis 键去重，Redis 异常时选择不丢数据而允许重复；这不是数据库唯一约束的替代品。回答时应把“可接受重复”与“绝对不重复”分开。

---

## 八、一页速记

| 关键词 | 一句话 |
|--------|--------|
| `ArrayList` | 连续数组，随机访问快，适合追加遍历 |
| `HashMap` | 数组 + 链表 + 红黑树，结构改动 |
| `ConcurrentHashMap` | CAS + 桶级同步，组合操作仍要原子 API |
| fail-fast | `ConcurrentModificationException`，检测不是保证 |
| `volatile` | 可见性和重排，不保证复合原子 |
| CAS | 比较并交换，注意自旋和 ABA |
| 线程池 | core、queue、max 的顺序决定扩容行为 |
| `@Async` | 代理生效，线程切换后事务/MDC 不自动传递 |
| GC Roots | 可达性分析的起点 |
| G1 | 分 Region，按停顿目标选择回收收益 |
| Metaspace | 类元数据，不是传统堆 |
| `@Transactional` | 基于代理，默认只对 RuntimeException/Error 回滚 |
| `@Cacheable` | 缓存注解不是数据库事务的一部分 |
| Filter | Servlet 链，先于 DispatcherServlet |
| Interceptor | DispatcherServlet 内，可拿 HandlerMethod |
| `@Conditional` | 条件满足才装配 Bean |

---

## 九、30 题自测清单

### 集合（7）

1. `ArrayList` 扩容和 `LinkedList` 的适用场景？
2. `HashMap` 的扰动、桶定位、树化和扩容？
3. `ConcurrentHashMap` 为什么不能在并发下随便 `containsKey` 后 `put`？
4. `HashMap`、`Hashtable`、`ConcurrentHashMap` 的 null 和一致性差异？
5. fail-fast 与弱一致迭代有什么区别？
6. `equals/hashCode` 违约会造成什么问题？
7. `List.copyOf` 能保证元素对象不可变吗？

### 并发（8）

8. 线程池为什么会先把任务放进队列再扩容到最大线程？
9. `@Async` 哪些情况不生效？
10. `volatile` 能保证 `i++` 安全吗？
11. `synchronized` 和 `ReentrantLock` 的取舍？
12. CAS 的 ABA、自旋和单变量边界？
13. ThreadLocal 在线程池中为什么必须清理？
14. RabbitMQ 并发消费与 Java 线程池有什么关系？
15. 项目哪些状态是 per-JVM，哪些状态跨实例？

### JVM（7）

16. JVM 运行时区域哪些线程私有、哪些共享？
17. 对象分配和 TLAB 的简化过程？
18. GC Roots 有哪些？
19. G1 的 Region、Young/Mixed GC 和停顿目标？
20. 堆泄漏、Metaspace 泄漏、直接内存泄漏怎么区分？
21. 双亲委派解决什么问题，Spring Boot 打包有什么不同？
22. 项目有没有做过 JVM 参数调优？正确回答是什么？

### Spring（8）

23. IoC、DI、AOP 分别解决什么问题？
24. Bean 生命周期和 `@PostConstruct`/`SmartLifecycle` 的区别？
25. 构造器注入为什么更容易发现循环依赖？
26. `@Transactional` 的失效、回滚和传播边界？
27. `@CacheEvict` 能否保证数据库与 Redis 强一致？
28. Filter 与 Interceptor 的顺序和异常处理差异？
29. `@Profile`、`@ConditionalOnProperty`、`SmartLifecycle` 如何协作？
30. 如果面试官问“项目里 JVM/Spring 的不足”，你会怎么诚实回答？

---

## 十、禁止夸大的表述

### 不要这样说

> “项目已经完成 JVM 调优，堆和 GC 都经过优化。”

正确说法：

> “项目使用 JDK 25 和容器默认内存/GC 行为，Actuator/Micrometer 观测到一次压测中 G1 Young GC 最大暂停 27ms；我没有做参数选型和堆调优，如果要做会先建立基线并做对照实验。”

### 不要这样说

> “项目用 Redis 实现了分布式幂等和分布式限流。”

正确说法：

> “MQTT 字段级使用 Redis SETNX 做 24 小时幂等；SSE Registry 和 Guava RateLimiter 仍是单 JVM 状态，水平扩容需要额外的 Redis/pub-sub、集中限流或一致性存储。”

### 不要这样说

> “报警消费用 ConcurrentHashMap 保证消息只消费一次。”

正确说法：

> “当前 AlarmConsumer 只有进程内 deliveryTag 去重，Map 没有清理，不能跨实例；真正的跨实例幂等需要稳定业务消息 ID、持久化去重键和数据库唯一约束/状态机配合。”

### 不要这样说

> “Spring 的单例 Bean 天然线程安全，事务注解可以保证缓存和 MQ 一致。”

正确说法：

> “Spring 只管理生命周期和代理，线程安全取决于 Bean 状态；`@Transactional` 管关系型数据库事务，不能把 Redis、MQ 和缓存自动纳入同一个原子事务。”

### 不要这样说

> “项目用了 Qdrant，RAG 已经是生产级向量检索。”

正确说法：

> “当前 RAG 使用 `SimpleVectorStore` 加本地哈希 Embedding，适合单机链路验证；Qdrant 是后续演进，不应把接口抽象说成已接入。”

---

## 十一、项目相关追问的推荐回答顺序

当面试官问“你这个项目里 Java 基础体现在哪里”时，不要从 Day 1 讲路线，直接选一条证据链：

> “我举一个集合和并发的例子。后端有一个单 JVM 的 SSE 会话注册表，用 `ConcurrentHashMap` 维护 userId 到 emitter 的映射，查询时生成快照，删除依赖 completion/timeout/error 回调并配合 `@PreDestroy` 清理。它解决了单副本下多线程注册与推送的基本安全，但我也清楚它不是分布式注册表，多副本要做 Redis 广播。第二个例子是 MQTT 入库：QoS1 是至少一次，所以我没有假设只投递一次，而是用设备 ID、时间戳、字段类型组成 Redis SETNX 幂等键，24 小时 TTL；Redis 异常时策略是宁可重复不丢数据。第三个例子是登录审计的独立线程池，保证慢审计不阻塞登录，但审计失败只记日志，不能把它包装成可靠消息。”

这样回答同时覆盖集合、并发、Spring 配置和边界，比机械罗列“用了 HashMap、线程池、JVM”更可信。

---

> Day 112 说明：本文是面试复习与证据映射，不是对项目能力的额外承诺。所有项目数字以 Day 112 最终基线为准；若后续代码或报告变化，应先更新事实来源，再更新本文。
