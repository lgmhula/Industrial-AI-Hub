# Day 74 — 设备手册知识库实战：PDF 解析 + 切片 + 入库

> **日期**：2026-08-29
> **阶段**：Phase 4 AI 集成 · Week 11（RAG + 知识库）
> **分支**：`feat/rag-retrieval`
> **配套 ADR**：[0025-rag-pdf-ingestion.md](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/docs/decision-log/0025-rag-pdf-ingestion.md)
> **验收结果**：✅ **GO**（PDF 上传入库闭环 + 2 个单测 + 后端 225 tests 0 failures）

---

## 一、今日产出

| 模块 | 文件 | 说明 |
| --- | --- | --- |
| PDF 解析依赖 | `pom.xml` | 新增 `org.apache.pdfbox:pdfbox:3.0.8`（ADR 0025） |
| 导入服务 | `service/PdfIngestionService.java` | PDF 字节 → 文本提取 → `RagIngestionService` 切片入库 |
| 上传端点 | `controller/RagController.java` | `POST /api/rag/documents`，`@RequireRole(ADMIN)` |
| 结果 DTO | `dto/ai/RagIngestResult.java` | fileName / characters / chunks |
| 审计迁移 | `V11__rag_knowledge_operation_types.sql` | `INGEST` / `KNOWLEDGE` 扩展 |
| 单测 | `service/PdfIngestionServiceTest.java` | 正常 PDF 提取入库 / 空 PDF 400 |

## 二、接口契约

```text
POST /api/rag/documents
Content-Type: multipart/form-data
Authorization: Bearer <ADMIN JWT>
file: <PDF 文件>

Response 200 ApiResponse<RagIngestResult>:
{ "fileName": "device-manual.pdf", "characters": 1234, "chunks": 3 }
```

错误语义：空文件 / 非 PDF / 提取不到文本 → 400；匿名 401；非 ADMIN 403。

## 三、测试

```
PdfIngestionServiceTest  2/2
FlywayProductionSeedIsolationTest（含 V11 清单） 4/4
```

全量后端回归：`Tests run: 225, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS。

## 四、明日计划（Day 75）

| 优先级 | 内容 |
| :-: | --- |
| ★★★ | AI 运维助手：检索片段 → PromptTemplate 上下文注入 → ChatClient 回答 |
| ★★☆ | `AiService.answerWithRag(question)` 组合 `RagRetrievalService` |
| ★☆☆ | 同步 AGENTS/ROADMAP 与 Day75 日志 |
