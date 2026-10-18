package dev.reboot.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.reboot.config.RagProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.document.Document;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 Qdrant 的 add/search/size 验证。
 *
 * <p>显式执行：
 * {@code RUN_QDRANT_IT=true ./mvnw test -Dtest=QdrantVectorStoreIT}。</p>
 */
@EnabledIfEnvironmentVariable(named = "RUN_QDRANT_IT", matches = "true")
class QdrantVectorStoreIT {

    private final String baseUrl = System.getenv().getOrDefault(
            "QDRANT_BASE_URL", "http://127.0.0.1:6333");

    @Test
    void addSearchAndSize_roundTripWithRealQdrant() throws Exception {
        String collection = "it_knowledge_" + UUID.randomUUID().toString().replace("-", "");
        try {
            RagProperties.Qdrant qdrant = new RagProperties.Qdrant();
            qdrant.setBaseUrl(baseUrl);
            qdrant.setCollection(collection);
            QdrantVectorStore store = new QdrantVectorStore(
                    qdrant, new FixedEmbeddingModel(), new ObjectMapper());

            store.add(List.of(
                    Document.builder().id("temp").text("设备温度过高")
                            .metadata(java.util.Map.of("source", "manual")).build(),
                    Document.builder().id("login").text("用户登录成功")
                            .metadata(java.util.Map.of("source", "guide")).build()));

            List<Document> results = store.similaritySearch("设备温度过高", 2);

            assertEquals(2, results.size());
            assertEquals("temp", results.get(0).getId());
            assertTrue(((Double) results.get(0).getMetadata().get("score")) > 0.9);
            assertEquals(2, store.size());
        } finally {
            deleteCollection(collection);
        }
    }

    private void deleteCollection(String collection) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        baseUrl + "/collections/" + collection))
                .timeout(java.time.Duration.ofSeconds(5))
                .DELETE()
                .build();
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
    }

    private static class FixedEmbeddingModel implements org.springframework.ai.embedding.EmbeddingModel {
        @Override
        public org.springframework.ai.embedding.EmbeddingResponse call(
                org.springframework.ai.embedding.EmbeddingRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public float[] embed(Document document) {
            return vector(document.getText());
        }

        @Override
        public float[] embed(String text) {
            return vector(text);
        }

        @Override
        public int dimensions() {
            return 4;
        }

        private float[] vector(String text) {
            return text.contains("温度")
                    ? new float[]{1.0f, 0.0f, 0.0f, 0.0f}
                    : new float[]{0.0f, 1.0f, 0.0f, 0.0f};
        }
    }
}
