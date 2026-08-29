# Day 75 — AI 运维助手：根据知识库回答设备运维问题

> **日期**：2026-08-29
> **阶段**：Phase 4 AI 集成 · Week 11（RAG + 知识库）
> **分支**：`feat/rag-retrieval`
> **配套 ADR**：[0024-rag-vector-store.md](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/docs/decision-log/0024-rag-vector-store.md) / [0025-rag-pdf-ingestion.md](file:///Users/air/Documents/%E9%87%8D%E5%90%91%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/docs/decision-log/0025-rag-pdf-ingestion.md)
> **验收结果**：✅ **GO**（RAG 知识问答闭环 + 2 个单测 + 后端 227 tests 0 failures）

---

## 一、今日产出

| 模块 | 文件 | 说明 |
| --- | --- | --- |
| 问答方法 | `service/AiService.java` | 新增 `answerWithRag(question)`：检索 → PromptTemplate 上下文注入 → ChatClient 回答 |
| 请求 DTO | `dto/ai/RagAskRequest.java` | `question` @NotBlank + @Size(2000) |
| 结果 DTO | `dto/ai/RagAnswerResult.java` | `answer` + `sources` |
| 问答端点 | `controller/RagController.java` | `POST /api/rag/ask`，VIEWER+ |
| 单测 | `service/AiServiceTest.java` | +2：有检索片段注入上下文 / 无片段降级不调 AI |

## 二、行为约定

- 先检索 Top-5 知识片段；无片段时直接返回提示，不调用 LLM（避免幻觉）；
- 有片段时注入 `知识库片段` 上下文，系统提示词限定“只依据片段回答”；
- `@OperationLog(operationType=CHAT, targetType=KNOWLEDGE)`，审计沿用既有约束。

## 三、接口契约

```text
POST /api/rag/ask
Authorization: Bearer <VIEWER+ JWT>
Body: { "question": "设备温度过高怎么处理？" }

Response 200 ApiResponse<RagAnswerResult>:
{ "answer": "请检查散热片...", "sources": [KnowledgeChunk...] }
```

## 四、测试

```
AiServiceTest  10/10（其中 RAG 问答 2 个新增用例）
```

全量后端回归：`Tests run: 227, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS。

## 五、明日计划（Day 76）

| 优先级 | 内容 |
| :-: | --- |
| ★★★ | 前端 AI 助手对话页面（侧边栏或独立页面），接入 `/api/rag/ask` |
| ★★☆ | 展示回答 + 引用来源片段 |
| ★☆☆ | 同步 AGENTS/ROADMAP 与 Day76 日志 |
