package dev.reboot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 知识库配置属性（Week 11，ADR 0024）。
 *
 * <p>默认使用离线哈希向量与文件持久化内存向量库；显式开启后可使用
 * OpenAI-compatible embedding 与 Qdrant，业务代码仍只依赖
 * {@code EmbeddingModel} 与 {@code VectorStore} 接口。</p>
 *
 * @author AI 助手
 * @since 2026-08-29
 */
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    /** simple = 本地文件向量库；qdrant = 外部 Qdrant。 */
    private String vectorStore = "simple";

    /** Embedding provider 配置。 */
    private final Embedding embedding = new Embedding();

    /** Qdrant 连接配置。 */
    private final Qdrant qdrant = new Qdrant();

    /** 单块最大字符数。 */
    private int chunkSize = 500;

    /** 相邻块之间的重叠字符数。 */
    private int chunkOverlap = 50;

    public String getVectorStore() { return vectorStore; }
    public void setVectorStore(String vectorStore) { this.vectorStore = vectorStore; }
    public Embedding getEmbedding() { return embedding; }
    public Qdrant getQdrant() { return qdrant; }
    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
    public int getChunkOverlap() { return chunkOverlap; }
    public void setChunkOverlap(int chunkOverlap) { this.chunkOverlap = chunkOverlap; }

    /**
     * 兼容旧测试与旧配置的属性入口。
     *
     * <p>新配置统一使用 {@code rag.embedding.dimensions}。</p>
     */
    public int getEmbeddingDimensions() { return embedding.getDimensions(); }
    public void setEmbeddingDimensions(int embeddingDimensions) {
        embedding.setDimensions(embeddingDimensions);
    }

    public static class Embedding {
        /** hash = 本地确定性向量；openai-compatible = /embeddings HTTP 端点。 */
        private String provider = "hash";
        /** 0 表示按 provider 选择默认维度：hash=256，openai-compatible=1536。 */
        private int dimensions;
        private String baseUrl = "https://api.openai.com/v1";
        private String apiKey = "";
        private String model = "text-embedding-3-small";
        private int timeoutSeconds = 30;
        /** OpenAI text-embedding-3 支持 dimensions；其他兼容服务可设为 false。 */
        private boolean sendDimensions = true;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public int getDimensions() { return dimensions; }
        public void setDimensions(int dimensions) { this.dimensions = dimensions; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public boolean isSendDimensions() { return sendDimensions; }
        public void setSendDimensions(boolean sendDimensions) { this.sendDimensions = sendDimensions; }
    }

    public static class Qdrant {
        private String baseUrl = "http://127.0.0.1:6333";
        private String collection = "industrial_ai_hub_knowledge";
        private String apiKey = "";
        private int timeoutSeconds = 10;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getCollection() { return collection; }
        public void setCollection(String collection) { this.collection = collection; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }
}
