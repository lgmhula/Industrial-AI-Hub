# Week 11 RAG 学习笔记：从私有文档到可检索知识

> 日期：2026-08-29 | 覆盖：Day 71 ~ Day 72（ADR 0024）

---

## 1. RAG 是什么

RAG（Retrieval-Augmented Generation，检索增强生成）解决 LLM 的一个核心短板：
模型训练知识有时间与范围边界，无法知道项目私有的设备手册、运维规程。

RAG 的做法是：

```text
提问 → 检索私有知识库 → 把相关片段拼进提示词 → LLM 生成答案
```

这样答案既依赖模型的语言能力，又依赖私有数据的实时性与准确性，还能降低“编造”。

---

## 2. 标准流水线

```text
加载文档 → 切片 chunk → 向量化 embed → 存入向量库 → 查询向量检索 → 拼接上下文 → LLM 回答
```

| 阶段 | 本项目实现（Day 72） |
|------|---------------------|
| 切片 | `TextChunker`：按句子聚合 + max-chars + overlap |
| 向量化 | `LocalHashEmbeddingModel`：字符 n-gram + 哈希投影 + L2 归一化 |
| 向量库 | `SimpleVectorStore`：内存实现 + 余弦相似度检索 |
| 编排 | `RagIngestionService.ingest(source, content)` |

---

## 3. 为什么切片要 overlap

固定长度切片会把一句话从中间切断。overlap 让相邻块保留上一块尾部内容，
跨边界的语义得以延续，检索时更不容易漏掉“后半句回答前半句”的信息。

```text
chunk-size = 500，chunk-overlap = 50
块1: [ 0 ... 500 ]
块2: [ 450 ... 950 ]
```

---

## 4. 向量数据库选型（ADR 0024）

| 候选 | 结论 | 理由 |
|------|------|------|
| Milvus | 未选 | 部署重（etcd/MinIO），学习成本高 |
| Chroma | 未选 | Python 生态强，Java 客户端弱 |
| **Qdrant** | 生产目标 | 单二进制、REST/gRPC、Java/Spring AI 支持好 |

Day 72 先实现内存 `VectorStore`，Qdrant 适配器留到 Day 73/74 与 Docker 服务一起接入。
这样切片/向量化/检索的链路可以离线跑通，不被外部服务阻塞。

---

## 5. 哈希向量的取舍

DeepSeek 目前不提供 `/embeddings` 端点。为了离线可测，第一版用
`LocalHashEmbeddingModel` 生成确定性向量：

```text
词 token + 字符 1/2/3-gram → 哈希到固定维度（带符号） → L2 归一化
```

它能让词面相近的文本余弦相似度更高，足以验证 RAG 链路；但语义召回弱，
生产环境应替换为真实 embedding 模型。因为业务只依赖 `EmbeddingModel` 接口，
替换时不触碰切片、存储、检索代码。

---

## 6. 下一步（Day 73+）

- 检索服务：把 `similaritySearch` 暴露为业务检索能力；
- PDF 导入：Day 74 设备手册；
- AI 运维助手：Day 75 用检索片段作为 ChatClient 上下文回答。
