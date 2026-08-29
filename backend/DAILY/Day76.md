# Day 76 — 前端 AI 助手对话页面（接入 /api/rag/ask）

> **日期**：2026-08-29
> **阶段**：Phase 4 AI 集成 · Week 11（RAG + 知识库）
> **分支**：`feat/rag-retrieval`
> **验收结果**：✅ **GO**（前端 build 850ms 0 errors + 页面渲染 0 console error）

---

## 一、今日产出

| 模块 | 文件 | 说明 |
| --- | --- | --- |
| AI 助手页面 | `views/RagAssistant.vue` | 问答对话流、引用片段展示、loading/error 状态 |
| API 封装 | `api/index.js` | 新增 `ragApi.ask(question)` |
| 路由 | `router/index.js` | 新增 `/assistant` 懒加载路由 |
| 导航 | `App.vue` / `main.js` | Sidebar 新增「AI 助手」入口与 `ChatDotRound` 图标 |

## 二、页面行为

- 输入问题发送 → 调 `POST /api/rag/ask`；
- 回答按会话气泡展示，来源片段以「引用片段」列表附在回答下方；
- 空问题不发送；请求中禁用输入并显示「正在检索知识库...」；
- 错误以红色气泡展示，不阻断后续提问。

## 三、验证

```
npm run build（frontend/）
✓ built in 850ms，0 errors
RagAssistant chunk 3.05 kB
```

Playwright：登录 → `/assistant`，页面标题、空状态、输入框、发送按钮正常渲染，控制台 0 error。

## 四、明日计划（Day 77）

| 优先级 | 内容 |
| :-: | --- |
| ★★★ | Week 11 周复盘 + RAG 笔记 |
| ★★☆ | 整理 `feat/rag-retrieval`（Day 73-76）准备 PR |
| ★☆☆ | 同步 AGENTS/ROADMAP 与 Day77 日志 |
