# ADR-0009: 三层架构 Controller → Service → Mapper

**日期:** 2026-07-20  
**状态:** Accepted  
**决策者:** hula0710

## 背景

Day 21 的 Controller 直接注入 Mapper（跳过 Service 层），虽然能跑，
但将业务逻辑与 HTTP 层耦合，不利于后续扩展（校验/事务/AOP）。

## 决策

Day 22 起，正式引入三层架构：

```
Controller (@RestController) → Service (@Service) → Mapper (@Mapper)
```

## 理由

1. 关注点分离：Controller 管 HTTP，Service 管业务，Mapper 管数据
2. `@Transactional` 在 Service 层声明，粒度合理
3. Service 可被多个 Controller 或定时任务复用
4. 方便单元测试（Mock Service 而不依赖 HTTP）
5. 符合 Spring 社区最佳实践

## 后果

- 新增 `dev.reboot.service.DeviceService`
- DeviceController 不再直接注入 DeviceMapper
- 异常从 Service 层抛出，GlobalExceptionHandler 统一捕获
