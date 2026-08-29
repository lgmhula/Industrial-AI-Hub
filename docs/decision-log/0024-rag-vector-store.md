# Decision 0024: RAG 向量库与 Embedding 策略（Week 11 知识库起步）

| 属性 | 值 |
|------|-----|
| **状态** | ✅ 已采纳 |
| **决策日期** | 2026-08-29 |
| **决策者** | hula0710 + AI 助手 |
| **关联** | Day 71 / Day 72 / ADR 0022 / DAILY_ROADMAP Week 11 |

## 1. 背景

Phase 4 第 11 周进入 RAG（检索增强生成）。已有 AI 能力（告警摘要/设备诊断/设备问答 Agent）
是“服务端预取上下文 → 单次补全”，而知识库场景需要：

1. 把私有文档切片并向量化；
2. 按语义检索最相关的片段；
3. 把片段作为上下文交给 LLM 回答。

需要先确定向量库选型与 embedding 策略，避免 Day 72-77 反复返工。

## 2. 决策

| 项 | 决策 |
|----|------|
| 向量库 | **Qdrant** 作为生产目标（单二进制、REST/gRPC、Java 客户端成熟、资源占用适中）；Milvus 偏重（依赖 etcd/MinIO），Chroma 偏 Python 生态，均不选 |
| 第一阶段存储 | Day 72 先实现项目内 `VectorStore` 接口 + **内存实现 `SimpleVectorStore`**（本地/测试零外部依赖）；Qdrant 适配器推迟到 Day 73/74 与 Docker 服务、集成测试一并落地 |
| Embedding | DeepSeek 当前不提供 `/embeddings` 端点，故第一版使用**本地确定性哈希向量 `LocalHashEmbeddingModel`**（实现 Spring AI `EmbeddingModel`）；未来可替换为 OpenAI 兼容 embedding 端点或本地 Ollama 模型，业务代码不变 |
| 切片策略 | `TextChunker`：按段落/句子拆分，`max-chars` + `overlap` 重叠，保留 source/chunkIndex 元数据 |
| 抽象边界 | 业务层只依赖 `VectorStore` / `EmbeddingModel` 接口，不依赖具体实现 |

### 2.1 向量库候选对比

| 候选 | 优点 | 未采纳原因 |
|------|------|-----------|
| Milvus | 大规模、召回能力强 | 部署重（etcd/MinIO/Pulsar），学习成本高，超出本项目体量 |
| Chroma | 上手快、Python 生态好 | Java 客户端弱，与现有 Spring Boot 技术栈不贴合 |
| **Qdrant** | 单二进制、REST/gRPC、Java/Spring AI 支持好、资源适中 | 采纳 |

### 2.2 Embedding 策略说明

`LocalHashEmbeddingModel` 采用字符 n-gram + 哈希投影 + L2 归一化，产生固定维度向量。
它能让“相同词面/相似片段”的余弦相似度更高，足以打通切片 → 向量化 → 入库 → 检索的
完整链路，且完全离线、可单测。它不是生产语义模型，切换真实 embedding 时只替换
`EmbeddingModel` Bean，不触碰切片与存储代码。

## 3. 备选方案（未采纳）

| 方案 | 未采纳原因 |
|------|-----------|
| 直接接 OpenAI-compatible embedding 端点 | 需要额外 Key/服务，且 DeepSeek 无 embeddings 能力；先用本地实现打通链路，未来按需替换 |
| 引入 Milvus/Chroma 并当天接 Java 客户端 | 增加外部服务与测试复杂度，Day 72 聚焦“切片+向量化+入库”主线 |
| 自研持久化文件向量库 | 检索性能与元数据管理不如成熟向量库，且偏离学习目标 |

## 4. 影响与验证

- 新增 `dev.reboot.rag` 包：`VectorStore` / `SimpleVectorStore` / `TextChunker` / `LocalHashEmbeddingModel`；
- 新增 `RagProperties`（`rag.embedding-dimensions` / `rag.chunk-size` / `rag.chunk-overlap`）；
- 新增 `RagIngestionService`：文档 → 切片 → embedding → 入库；
- 测试：切片、向量确定性、余弦检索排序、入库编排全链路单测；
- 文档：`docs/ai/phase4-ai-learning-notes.md` 或 Week 11 笔记同步，AGENTS/DAILY_ROADMAP 更新。

## 5. 风险

| 风险 | 缓解 |
|------|------|
| 哈希向量语义召回弱 | 明确标记为第一版离线实现；Day 73+ 检索效果不达标时替换真实 embedding |
| Qdrant 尚未实际接入 | ADR 明确生产目标与阶段边界，Day 73/74 补 Docker 服务与适配器 |
| 切片破坏上下文 | 使用 overlap 重叠保留跨边界语义，chunk-size 可配置 |

---

> 最后更新：2026-08-29 | 维护者：AI 助手 + hula0710
