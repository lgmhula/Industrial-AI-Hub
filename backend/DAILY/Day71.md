# Day 71 — RAG 概念 + 向量数据库选型（ADR 0024）

> **日期**：2026-08-29
> **阶段**：Phase 4 AI 集成 · Week 11（RAG + 知识库）
> **分支**：`feat/week11-rag`
> **配套 ADR**：[0024-rag-vector-store.md](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/docs/decision-log/0024-rag-vector-store.md)
> **配套笔记**：[rag-learning-notes.md](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/docs/ai/rag-learning-notes.md)
> **验收结果**：✅ **GO**（ADR 0024 决策闭环 + RAG 概念/选型笔记成稿）

---

## 一、今日产出

| 项 | 内容 |
| --- | --- |
| ADR 0024 | 向量库选 Qdrant（生产目标），Milvus/Chroma 未采纳；第一阶段内存 `VectorStore`；embedding 用本地哈希模型实现 Spring AI `EmbeddingModel` |
| 学习笔记 | `docs/ai/rag-learning-notes.md`：RAG 概念、流水线、overlap、选型对比、哈希向量取舍 |
| 状态同步 | AGENTS §3 / DAILY_ROADMAP Day 71 标记完成 |

## 二、关键结论

1. 向量库生产目标 = Qdrant，但 Day 72 先做内存实现打通链路；
2. DeepSeek 无 embeddings 端点，先用离线哈希向量，真实 embedding 可无痛替换；
3. 切片必须带 overlap，避免跨块语义断裂。

## 三、明日计划（Day 72）

| 优先级 | 内容 |
| :-: | --- |
| ★★★ | 文档切片 `TextChunker` + 哈希向量 `LocalHashEmbeddingModel` + 内存向量库 `SimpleVectorStore` + 入库编排 `RagIngestionService` |
| ★★☆ | 11 个单元测试覆盖切片/向量/检索/编排，后端全量回归 |
| ★☆☆ | 同步 AGENTS/ROADMAP 与 Day72 日志 |
