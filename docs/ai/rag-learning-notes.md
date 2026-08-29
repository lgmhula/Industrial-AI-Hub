# Week 11 RAG 学习笔记：从私有文档到可检索知识

> 日期：2026-08-29 | 覆盖：Day 71 ~ Day 77（ADR 0024 / ADR 0025）

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

| 阶段 | 本项目实现 |
|------|---------------------|
| 加载 | `PdfIngestionService`：PDFBox 提取 PDF 文本（Day 74） |
| 切片 | `TextChunker`：按句子聚合 + max-chars + overlap（Day 72） |
| 向量化 | `LocalHashEmbeddingModel`：字符 n-gram + 哈希投影 + L2 归一化 |
| 向量库 | `SimpleVectorStore`：内存实现 + 余弦相似度检索 |
| 检索 | `RagRetrievalService`：Top-K 余弦相似度（Day 73） |
| 问答 | `AiService.answerWithRag`：检索片段注入 ChatClient（Day 75） |
| 前端 | `RagAssistant.vue` + `/assistant`（Day 76） |

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

## 6. 完整链路

```text
PDF 上传 → PDFBox 文本提取 → TextChunker 切片
  → LocalHashEmbeddingModel 向量化 → SimpleVectorStore 入库
  → RagRetrievalService Top-K 检索 → answerWithRag 注入上下文
  → ChatClient 回答 + 前端展示引用片段
```

关键边界：

- 检索结果为空时不调用 LLM，直接返回“知识库中未找到相关内容”；
- 系统提示词限定“仅依据知识库片段回答”，降低编造；
- 上传与问答都走 RBAC（上传 ADMIN，问答 VIEWER+）。

## 7. 工程经验

1. **先用抽象接口打通链路，再替换具体实现**：内存向量库/哈希 embedding 是“可工作的占位”，
   接口稳定后换 Qdrant/真实 embedding 成本低；
2. **overlap 不是可选项**：跨块语义断裂会直接损害召回；
3. **AI 的边界要由代码守住**：空上下文、超长回答、异常解析都要有降级；
4. **RAG 是“引用”不是“记忆”**：来源片段要展示给用户，才能审计 AI 是否胡说。
