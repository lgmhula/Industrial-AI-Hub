# Day 72 — 文档切片 + 向量化（embedding）+ 存入向量库

> **日期**：2026-08-29
> **阶段**：Phase 4 AI 集成 · Week 11（RAG + 知识库）
> **分支**：`feat/week11-rag`
> **配套 ADR**：[0024-rag-vector-store.md](file:///Users/air/Documents/%E9%87%8D%E5%90%AF%EF%BC%9A%E8%BD%AF%E4%BB%B6%E5%B7%A5%E7%A8%8B%E5%B8%88%EF%BC%88Industrial%20AI%20Hub%EF%BC%89/docs/decision-log/0024-rag-vector-store.md)
> **验收结果**：✅ **GO**（RAG 入库链路落地 + 11 个新单测 + 后端 219 tests 0 failures）

---

## 一、交付范围

| 模块 | 文件 | 说明 |
| --- | --- | --- |
| 配置属性 | `config/RagProperties.java` | `rag.embedding-dimensions` / `chunk-size` / `chunk-overlap` |
| 配置注册 | `config/RagConfig.java` | `@EnableConfigurationProperties(RagProperties.class)` |
| 切片 | `rag/TextChunker.java` | 句子聚合 + max-chars + overlap，输出 Spring AI `Document` |
| 向量化 | `rag/LocalHashEmbeddingModel.java` | 实现 Spring AI `EmbeddingModel`，字符 n-gram 哈希投影 + L2 归一化 |
| 向量库抽象 | `rag/VectorStore.java` | `add` / `similaritySearch` / `size` |
| 内存向量库 | `rag/SimpleVectorStore.java` | 线程安全 Map + 余弦相似度降序检索 |
| 入库编排 | `service/RagIngestionService.java` | 切片 → 向量化 → 入库，返回块数量 |
| 配置 | `application.yml` | 新增 `rag` 段默认值 |

## 二、测试

```
TextChunkerTest              3/3
LocalHashEmbeddingModelTest  5/5
SimpleVectorStoreTest        2/2
RagIngestionServiceTest      1/1
合计                         11/11
```

全量后端回归：`Tests run: 219, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS。

## 三、明日计划（Day 73）

| 优先级 | 内容 |
| :-: | --- |
| ★★★ | 知识检索实现：`RagRetrievalService` 根据问题检索 Top-K 相关片段 |
| ★★☆ | 起草检索接口契约，为 Day 74 PDF 导入与 Day 75 AI 运维助手铺路 |
| ★☆☆ | 同步 AGENTS/ROADMAP 与 Day73 日志 |
