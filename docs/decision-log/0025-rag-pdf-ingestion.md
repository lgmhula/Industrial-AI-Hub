# Decision 0025: RAG PDF 知识文档导入（PDFBox + 上传端点）

| 属性 | 值 |
|------|-----|
| **状态** | ✅ 已采纳 |
| **决策日期** | 2026-08-29 |
| **决策者** | hula0710 + AI 助手 |
| **关联** | Day 74 / ADR 0024 / RagController / PdfIngestionService |

## 1. 背景

Week 11 知识库实战需要把设备手册等 PDF 导入向量库。ADR 0024 已确定切片、embedding、
向量库策略，但尚未定义“PDF 从哪里来、怎么解析、如何入库”。Day 74 引入 PDF 文本提取，
并通过受控上传端点完成入库闭环。

## 2. 决策

| 项 | 决策 |
|----|------|
| PDF 解析库 | Apache PDFBox `3.0.8`（纯 Java、无外部二进制依赖，`Loader.loadPDF` + `PDFTextStripper` 提取文本） |
| 入库端点 | `POST /api/rag/documents`，`multipart/form-data` 上传 `file`，仅 ADMIN |
| 解析与入库分离 | `PdfIngestionService` 负责字节 → 文本；`RagIngestionService` 负责文本 → 切片 → 向量化 → 入库 |
| 失败语义 | 空文件 / 非 PDF / 提取不到文本 → `BAD_REQUEST(400)`；解析 IO 异常 → 400 并保留原异常 |
| 审计 | `@OperationLog(operationType="INGEST", targetType="KNOWLEDGE", description="上传 RAG 知识文档 {ret}")`，Flyway V11 扩展 CHECK |

## 3. 备选方案（未采纳）

| 方案 | 未采纳原因 |
|------|-----------|
| 前端解析 PDF 后传文本 | 把解析逻辑推到前端，服务端失去单一入库入口与审计闭环 |
| Apache Tika | 能力更强但依赖面大，本项目仅需 PDF 文本提取 |
| 直接调 `RagIngestionService` 不做独立 PDF 服务 | 职责耦合，后续接 Word/Markdown 导入需重构 |

## 4. 影响与验证

- 依赖：`backend/pom.xml` 新增 `org.apache.pdfbox:pdfbox:3.0.8`；
- 代码：`RagController` + `PdfIngestionService` + `RagIngestResult`；
- 迁移：`V11__rag_knowledge_operation_types.sql`（INGEST / KNOWLEDGE）+ H2 schema 同步；
- 测试：PDF 字节生成 → 文本提取 → 切片入库，以及空文件 400；
- 文档：AGENTS/DAILY_ROADMAP/本 ADR 同步。

## 5. 风险

| 风险 | 缓解 |
|------|------|
| 扫描版 PDF 无文本层 | 提取不到文本时返回 400，明确提示用户改用文本型 PDF；OCR 不在 Day 74 范围 |
| 大文件内存占用 | 上传默认受 Spring multipart 限制；后续可加大小上限与流式处理 |
| 恶意 PDF | 仅 ADMIN 可上传；PDFBox 解析异常统一 400，不向外泄露堆栈 |

---

> 最后更新：2026-08-29 | 维护者：AI 助手 + hula0710
