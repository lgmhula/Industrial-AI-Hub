# Week 11 复盘 — RAG + 知识库（Phase 4 第 11 周）

> 日期：2026-08-29 | 覆盖：Day 71 ~ Day 77

---

## 一、本周目标 vs 实际

| 目标 | 实际 | 状态 |
|------|------|:----:|
| Day 71: RAG 概念 + 向量库选型 | ADR 0024：Qdrant 生产目标 + 内存向量库第一阶段 + 本地哈希 embedding | ✅ |
| Day 72: 文档切片 + 向量化 + 入库 | `TextChunker` + `LocalHashEmbeddingModel` + `SimpleVectorStore` + `RagIngestionService` | ✅ |
| Day 73: 知识检索 | `RagRetrievalService` + `KnowledgeChunk`，Top-K 余弦检索 | ✅ |
| Day 74: PDF 设备手册导入 | PDFBox 3.0.8 + `RagController` 上传 + Flyway V11 审计 | ✅ |
| Day 75: AI 运维助手 | `AiService.answerWithRag` + `POST /api/rag/ask` | ✅ |
| Day 76: 前端 AI 助手页 | `RagAssistant.vue` + `/assistant` 路由 + Sidebar 入口 | ✅ |
| Day 77: 周复盘 + RAG 笔记 | 本复盘 + `docs/ai/rag-learning-notes.md` 更新 | ✅ |

## 二、关键收获

### 2.1 RAG 的价值边界是“私有知识 + 实时数据”

前一周的 Function Calling 让 AI 查实时数据，这一周 RAG 让 AI 查私有文档。
两者回答的是不同问题：前者“现在发生了什么”，后者“遇到问题该怎么处理”。
工业运维场景里，两者缺一不可。

### 2.2 切片和 overlap 是召回质量的第一道关

固定长度切片会切断语义。`chunk-size` + `chunk-overlap` 让相邻块保留尾部上下文，
避免“后半句回答前半句”的检索盲区。参数做成 `RagProperties`，后续可调。

### 2.3 抽象接口让“临时方案”不污染业务

第一版 embedding 用离线哈希向量，向量库用内存实现。因为业务只依赖
`EmbeddingModel` 与 `VectorStore` 接口，未来替换真实 embedding 或 Qdrant
时不需要改切片、检索、问答代码。这是本周最重要的工程决策。

### 2.4 无片段时不调 LLM，避免幻觉

`answerWithRag` 在检索结果为空时直接返回提示，不把空上下文交给模型。
否则模型会倾向于“自由发挥”，在工业场景尤其危险。

## 三、Week 11 演进全景

```text
Day 71-72  切片 + 向量化 + 入库      TextChunker / LocalHashEmbeddingModel / SimpleVectorStore
Day 73     检索服务                  RagRetrievalService → Top-K 余弦相似度
Day 74     PDF 导入                  PdfIngestionService + POST /api/rag/documents
Day 75     AI 运维助手               AiService.answerWithRag + POST /api/rag/ask
Day 76     前端对话页                RagAssistant.vue + /assistant
```

## 四、第四阶段第 11 周检查点

> 目标：把“设备手册知识库 → 检索 → 回答”的最小闭环跑通。

| 能力 | 证据 |
|------|------|
| 文档入库 | `POST /api/rag/documents`（PDFBox 文本提取） |
| 语义检索 | `RagRetrievalService.retrieve`（Top-K 余弦相似度） |
| 知识问答 | `POST /api/rag/ask`（检索片段注入 ChatClient） |
| 前端入口 | `/assistant` AI 助手对话页 + 引用片段 |
| 审计 | Flyway V11 `INGEST/KNOWLEDGE` + `CHAT/KNOWLEDGE` |

**检查点结论**：RAG 从“概念”走到“可上传、可检索、可问答”的业务闭环。

## 五、技术债务状态

| ID | 说明 | 状态 |
|----|------|------|
| TD-030 | V9 MySQL IT 缺口（历史） | ⏳ 仍遗留（P2） |
| 新 | 哈希 embedding 语义召回弱 | ⏳ 设计内暂缓，接真实 embedding 时替换 |
| 新 | 内存向量库不持久化 | ⏳ Day 73+ 换 Qdrant |
| 新 | PDF 扫描版无文本层 | ⏳ 提取不到时 400，OCR 未做 |

## 六、不足与改进

1. **向量库仍是内存实现**：重启即丢，Qdrant 适配器尚未落地；
2. **哈希向量不是语义检索**：词面相近才有效，同义改写召回弱；
3. **端到端未用真实 DeepSeek Key 验证**：单测覆盖 503/降级路径，真实回答质量待测；
4. **前端未提供 PDF 上传入口**：管理员仍需用 Postman/curl 导入文档。

## 七、下周展望（Week 12：Agent + MCP）

| 天 | 任务 |
|----|------|
| Day 78 | Agent 概念：ReAct 模式、工具调用循环 |
| Day 79 | 实现简单 Agent：多步推理 |
| Day 80 | MCP 协议概念 + MCP Server 开发 |
| Day 81-83 | Device MCP Server + Codex/MCP 集成 + 联调 |
| Day 84 | 周复盘 + Agent/MCP 笔记 |

> Week 11 收官。下一阶段从“检索增强”走向“Agent 与 MCP 工具生态”。
