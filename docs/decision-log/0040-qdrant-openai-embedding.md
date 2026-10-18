# Decision 0040: Qdrant 与 OpenAI-compatible Embedding 可选接入

| 属性 | 值 |
|------|-----|
| **状态** | 已落地，默认关闭 |
| **决策日期** | 2026-10-18 |
| **决策者** | hula0710 + AI 助手 |
| **关联** | ADR 0024 / ADR 0025 / Compose v1.8 |

## 1. 背景

ADR 0024 的首版 RAG 使用 `SimpleVectorStore + LocalHashEmbeddingModel`，优点是离线、零外部依赖，
适合学习与单机演示；缺点是语义召回弱、向量文件只适合单机，不能作为可替换向量库的验证。

本轮目标不是强制项目依赖外部 AI 服务，而是在不改变业务接口的前提下，提供可切换的真实向量库与
OpenAI-compatible embedding 实现，并保持默认启动路径不变。

## 2. 决策

### 2.1 配置

| 配置 | 默认 | 可选 |
|------|------|------|
| `rag.vector-store` | `simple` | `qdrant` |
| `rag.embedding.provider` | `hash` | `openai-compatible` |
| `rag.embedding.dimensions` | `0`（provider 默认） | 正整数 |
| `rag.qdrant.base-url` | `http://127.0.0.1:6333` | Qdrant 地址 |

默认保持 `SimpleVectorStore + LocalHashEmbeddingModel`，未配置外部依赖时行为与之前一致。

### 2.2 OpenAI-compatible Embedding

`OpenAiCompatibleEmbeddingModel` 只依赖 JDK `HttpClient`，调用 `{base-url}/embeddings`：

- 支持 OpenAI、SiliconFlow、Ollama 等兼容 `/embeddings` 的服务；
- `send-dimensions=false` 时省略 `dimensions` 字段，兼容不支持该字段的服务；
- provider 为 `openai-compatible` 且 API Key 为空时启动失败，避免静默降级成错误向量；
- 对响应数量、索引排序和维度做校验。

### 2.3 Qdrant

`QdrantVectorStore` 通过 REST 实现 `VectorStore`：

- 首次使用时按 embedding 维度创建 collection，距离为 Cosine；
- 文档 ID 映射为稳定 UUID，原文与 metadata 存 payload；
- 支持 add、similaritySearch、size；
- collection 已存在但维度不一致时明确失败；
- 并发创建冲突（409）后重新读取 collection，避免多实例启动竞争。

Compose `full` profile 增加 Qdrant 1.12.4；默认 `core` 不启动它。容器内后端默认 Qdrant 地址为
`http://qdrant:6333`，宿主机默认地址为 `http://127.0.0.1:6333`。

## 3. 备选方案

| 方案 | 未采纳原因 |
|------|------------|
| 永远只保留哈希向量 | 离线方便，但无法验证真实语义检索和外部向量库 |
| 直接引入 Qdrant Java 客户端 | 本轮只覆盖最小 REST 能力，JDK HttpClient 减少依赖与版本耦合 |
| 把 Qdrant 设为默认 | 会让默认启动依赖外部服务，与项目离线演示目标冲突 |
| 使用 DeepSeek embedding | DeepSeek 当前没有项目所需的 `/embeddings` 能力 |

## 4. 后果与风险

- 两种实现的行为、召回质量和运维成本不同，切换前需要重建对应 collection/index。
- 外部 embedding 调用涉及 API Key、网络、超时和费用；Key 只来自环境变量，禁止提交。
- Qdrant collection 维度与 embedding 维度强绑定，变更模型后需要迁移或新建 collection。
- 仍保留 hash/simple 默认路径，不能把“可选实现已落地”写成“默认已使用 Qdrant/真实 embedding”。

## 5. 验证证据

- `QdrantVectorStoreTest`：REST 契约、collection 创建、add/search/size、维度冲突。
- `QdrantVectorStoreIT`：真实 Qdrant 容器链路。
- `OpenAiCompatibleEmbeddingModelTest`：请求字段、响应排序、错误状态、Key 缺失与维度校验。
- 后端全量测试与前端构建在本轮最终回归复跑。
