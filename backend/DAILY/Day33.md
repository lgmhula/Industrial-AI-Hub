# Day 33 — 2026-07-28

## 今日目标
- [x] @OperationLog 注解定义（operationType / targetType / description）
- [x] OperationLogAspect AOP 切面（Around 拦截 + 自动写入 DB）
- [x] userId 自动获取（从 JwtAuthFilter 注入的 request attribute）
- [x] IP 自动提取（X-Forwarded-For / X-Real-IP / RemoteAddr）
- [x] 关键 Controller 添加 @OperationLog：
  - DeviceController: create / update / delete
  - AuthController: login / register
  - AlarmController: acknowledge / resolve
- [x] OperationLogMapper 分页 + Service/Controller 分页
- [x] Postman Collection 新增 Alarm / OperationLog / DeviceData 3 个文件夹
- [x] pom.xml 新增 spring-boot-starter-aop 依赖
- [x] mvn clean compile 通过（111 files）
- [ ] Git commit

## 新增文件

```
dev/reboot/aop/
└── OperationLogAspect.java       # AOP 切面: @Around @OperationLog

dev/reboot/annotation/
└── OperationLog.java             # 标记注解: operationType/targetType/description
```

## 修改文件

| 文件 | 变更 |
|------|------|
| `pom.xml` | +spring-boot-starter-aop |
| `DeviceController.java` | +@OperationLog × 3 (create/update/delete) |
| `AuthController.java` | +@OperationLog × 2 (login/register) |
| `AlarmController.java` | +@OperationLog × 2 (acknowledge/resolve) |
| `OperationLogMapper.java` | +5 分页方法 + 3 计数 |
| `OperationLogService.java` | +listPaged / listByUserId |
| `OperationLogController.java` | 默认端点改为分页 |
| `Industrial_AI_Hub.postman_collection.json` | +3 文件夹 (Alarm/OpLog/DeviceData) |

## AOP 切面流程

```
Controller method (@OperationLog)
  → OperationLogAspect.around()
    → joinPoint.proceed()              // 执行原方法
    → recordLog():
        → 从 request.getAttribute("userId")  获取当前用户
        → 从 @OperationLog 注解获取操作类型/目标类型
        → getClientIp() 提取真实 IP
        → operationLogMapper.insert()         写入 DB
```

## 技术要点

- 使用 `java.lang.reflect.Method.getAnnotation()` 获取注解（避免 AspectJ MethodSignature 类在 Spring Boot 3.5 中的兼容性问题）
- 描述占位符使用 `{0}` `{1}` 而非参数名（避免 `-parameters` 编译要求）
- 日志记录失败被 try-catch 包裹，不影响业务主流程
- 操作日志仅 ADMIN 角色可查看

## Postman Collection 更新

| 文件夹 | 用例数 | 说明 |
|------|:---:|------|
| 05-Alarm 报警管理 | 10 | 分页查询 + 确认/解决 + 触发测试 |
| 06-OperationLog 操作日志 | 3 | 分页/按用户/最近100条 |
| 07-DeviceData 设备数据 | 7 | 查询 + 报表 + 上报 |

## 代码统计
- 新增：2 files
- 修改：8 files
- 编译：111 files

---

## 明日计划
- Day 34: 前端补全 — 报警页面 + 日志页面 + 全局联调
