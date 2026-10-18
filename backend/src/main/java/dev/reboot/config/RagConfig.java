package dev.reboot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.reboot.rag.LocalHashEmbeddingModel;
import dev.reboot.rag.OpenAiCompatibleEmbeddingModel;
import dev.reboot.rag.QdrantVectorStore;
import dev.reboot.rag.SimpleVectorStore;
import dev.reboot.rag.VectorStore;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 基础设施配置注册（ADR 0024 / ADR 0040）。
 *
 * <p>Embedding 与向量库都改为显式条件装配，同一类型只存在一个 Bean。
 * 默认保持离线哈希 + SimpleVectorStore，不引入启动期外部依赖。</p>
 *
 * @author AI 助手
 * @since 2026-08-29
 */
@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class RagConfig {

    @Bean
    @ConditionalOnProperty(prefix = "rag.embedding", name = "provider",
            havingValue = "hash", matchIfMissing = true)
    public EmbeddingModel localHashEmbeddingModel(RagProperties properties) {
        return new LocalHashEmbeddingModel(properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "rag.embedding", name = "provider",
            havingValue = "openai-compatible")
    public EmbeddingModel openAiCompatibleEmbeddingModel(RagProperties properties,
                                                         ObjectMapper objectMapper) {
        return new OpenAiCompatibleEmbeddingModel(properties.getEmbedding(), objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "rag", name = "vector-store",
            havingValue = "simple", matchIfMissing = true)
    public VectorStore simpleVectorStore(EmbeddingModel embeddingModel,
                                         ObjectMapper objectMapper,
                                         @Value("${iah.vectorstore.path:${APP_BASE_DIR:./}/data/vectorstore.json}")
                                         String persistPath) {
        return new SimpleVectorStore(embeddingModel, objectMapper, persistPath);
    }

    @Bean
    @ConditionalOnProperty(prefix = "rag", name = "vector-store",
            havingValue = "qdrant")
    public VectorStore qdrantVectorStore(RagProperties properties,
                                         EmbeddingModel embeddingModel,
                                         ObjectMapper objectMapper) {
        return new QdrantVectorStore(properties.getQdrant(), embeddingModel, objectMapper);
    }
}
