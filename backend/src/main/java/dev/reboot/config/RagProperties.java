package dev.reboot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 知识库配置属性（Week 11，ADR 0024）。
 *
 * <p>第一版默认使用离线哈希向量与内存向量库；后续接入真实 embedding 与
 * Qdrant 时，业务代码仍只依赖 {@code EmbeddingModel} 与 {@code VectorStore} 接口。</p>
 *
 * @author AI 助手
 * @since 2026-08-29
 */
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    /** 本地哈希向量的维度。 */
    private int embeddingDimensions = 256;

    /** 单块最大字符数。 */
    private int chunkSize = 500;

    /** 相邻块之间的重叠字符数。 */
    private int chunkOverlap = 50;

    public int getEmbeddingDimensions() { return embeddingDimensions; }
    public void setEmbeddingDimensions(int embeddingDimensions) { this.embeddingDimensions = embeddingDimensions; }
    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
    public int getChunkOverlap() { return chunkOverlap; }
    public void setChunkOverlap(int chunkOverlap) { this.chunkOverlap = chunkOverlap; }
}
