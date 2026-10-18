package dev.reboot.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.reboot.config.RagProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QdrantVectorStoreTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicBoolean collectionExists = new AtomicBoolean();
    private final AtomicBoolean conflictOnCreate = new AtomicBoolean();
    private final AtomicReference<JsonNode> addedPoints = new AtomicReference<>();
    private HttpServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/collections/test_knowledge", this::handleCollection);
        server.createContext("/collections/test_knowledge/points", this::handlePoints);
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void addSearchAndSize_useQdrantRestContract() {
        QdrantVectorStore store = new QdrantVectorStore(
                qdrantProperties(), new FixedEmbeddingModel(), objectMapper);

        store.add(Document.builder()
                .id("doc-1")
                .text("设备温度过高")
                .metadata(java.util.Map.of("source", "manual", "chunkIndex", 0))
                .build());

        JsonNode point = addedPoints.get().path("points").get(0);
        assertEquals(QdrantVectorStore.pointId("doc-1"), point.path("id").asText());
        assertEquals("设备温度过高", point.path("payload").path("content").asText());
        assertEquals("manual", point.path("payload").path("metadata").path("source").asText());

        List<Document> results = store.similaritySearch("温度", 2);

        assertEquals(1, results.size());
        assertEquals("doc-1", results.get(0).getId());
        assertEquals("设备温度过高", results.get(0).getText());
        assertNotNull(results.get(0).getMetadata().get("score"));
        assertEquals(2, store.size());
    }

    @Test
    void pointId_isStableUuid() {
        assertEquals(QdrantVectorStore.pointId("same-id"),
                QdrantVectorStore.pointId("same-id"));
        assertTrue(QdrantVectorStore.pointId("same-id")
                .matches("[0-9a-f-]{36}"));
    }

    @Test
    void ensureCollection_toleratesConcurrentCreateConflict() {
        conflictOnCreate.set(true);

        QdrantVectorStore store = new QdrantVectorStore(
                qdrantProperties(), new FixedEmbeddingModel(), objectMapper);

        assertNotNull(store);
        assertTrue(collectionExists.get());
    }

    private void handleCollection(HttpExchange exchange) throws IOException {
        if ("GET".equals(exchange.getRequestMethod())) {
            if (!collectionExists.get()) {
                respond(exchange, 404, "{\"status\":{\"error\":\"Not found\"}}");
                return;
            }
            respond(exchange, 200, """
                    {"result":{"config":{"params":{"vectors":{"size":3,"distance":"Cosine"}}}}}
                    """);
            return;
        }
        if ("PUT".equals(exchange.getRequestMethod())) {
            JsonNode body = objectMapper.readTree(exchange.getRequestBody());
            collectionExists.set(body.path("vectors").path("size").asInt() == 3);
            if (conflictOnCreate.getAndSet(false)) {
                respond(exchange, 409, "{\"status\":{\"error\":\"already exists\"}}");
                return;
            }
            respond(exchange, 200, "{\"result\":true}");
            return;
        }
        respond(exchange, 405, "{}");
    }

    private void handlePoints(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("PUT".equals(exchange.getRequestMethod()) && path.endsWith("/points")) {
            addedPoints.set(objectMapper.readTree(exchange.getRequestBody()));
            respond(exchange, 200, "{\"result\":{\"status\":\"completed\"}}");
            return;
        }
        if ("POST".equals(exchange.getRequestMethod()) && path.endsWith("/search")) {
            respond(exchange, 200, """
                    {"result":[{
                      "id":"6d9d8a63-c4f7-3e2f-9d40-000000000001",
                      "score":0.91,
                      "payload":{
                        "documentId":"doc-1",
                        "content":"设备温度过高",
                        "metadata":{"source":"manual","chunkIndex":0}
                      }
                    }]}
                    """);
            return;
        }
        if ("POST".equals(exchange.getRequestMethod()) && path.endsWith("/count")) {
            respond(exchange, 200, "{\"result\":{\"count\":2}}");
            return;
        }
        respond(exchange, 404, "{}");
    }

    private RagProperties.Qdrant qdrantProperties() {
        RagProperties.Qdrant properties = new RagProperties.Qdrant();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setCollection("test_knowledge");
        properties.setTimeoutSeconds(5);
        return properties;
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static class FixedEmbeddingModel implements org.springframework.ai.embedding.EmbeddingModel {
        @Override
        public org.springframework.ai.embedding.EmbeddingResponse call(
                org.springframework.ai.embedding.EmbeddingRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public float[] embed(Document document) {
            return new float[]{1.0f, 0.0f, 0.0f};
        }

        @Override
        public float[] embed(String text) {
            return new float[]{1.0f, 0.0f, 0.0f};
        }

        @Override
        public int dimensions() {
            return 3;
        }
    }
}
