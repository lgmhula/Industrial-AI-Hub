# ADR 0013 — API 文档工具选型：Knife4j (springdoc + 增强 UI)

| 属性 | 值 |
|------|-----|
| **状态** | ✅ 已采纳 |
| **决策日期** | 2026-08-01 (Day 39) |
| **决策者** | hula0710 |
| **Phase 3-A 归档** | 2026-08-04 (T5) |

---

## 1. 背景

项目需要 API 文档机制，确保前后端协作时接口定义清晰、可交互测试。

## 2. 候选方案

| 方案 | 描述 |
|------|------|
| A — springdoc-openapi | Spring Boot 官方推荐，零侵入注解，自动生成 OpenAPI 3.0 |
| B — Knife4j | 基于 springdoc，增强 UI（支持导出、离线文档、参数分组） |
| C — Spring REST Docs | 测试驱动文档，AsciiDoc 输出 |
| D — 手工维护 Markdown | 手动编写 API-Reference.md |

## 3. 决策

**选择 B — Knife4j (4.5.0)**，理由：

- 底层基于 springdoc-openapi，与 Spring Boot 3.5 完全兼容
- 增强 UI 提供 Markdown 离线导出、全局参数设置、调试体验优于原生 Swagger UI
- 内网/开发环境零配置可用，`/doc.html` 替代 `/swagger-ui.html`
- pom.xml 仅需一个依赖，无需额外适配

## 4. Profile 策略

| Profile | springdoc 状态 | Knife4j 状态 |
|---------|:---:|:---:|
| dev | 启用 | `/doc.html` 可访问 |
| prod | 关闭 | 404（安全） |
| test | 关闭（默认） | 不暴露 |

相关配置：
- `application-dev.yml`: `springdoc.swagger-ui.enabled: true`, `springdoc.api-docs.enabled: true`
- `application-prod.yml`: `springdoc.swagger-ui.enabled: false`, `springdoc.api-docs.enabled: false`

## 5. 影响

- 新增 `Knife4jConfig.java` — 定义 OpenAPI Info（标题/版本/作者）
- `API-Reference.md` 标记为废弃，保留作为快速索引
- Controller 可渐进添加 `@Tag` / `@Operation` 增强文档可读性
- 无需额外维护成本（注解驱动的文档自动生成）

## 6. 备选方案未采纳原因

- **springdoc 裸 UI**: 功能等价但调试体验不如 Knife4j
- **Spring REST Docs**: 需要编写测试，文档生成步骤多，学习成本高
- **手工 Markdown**: 与代码脱节，容易过时，维护成本高

---

> 关联: `Knife4jConfig.java`, `application-dev.yml`, `application-prod.yml`
