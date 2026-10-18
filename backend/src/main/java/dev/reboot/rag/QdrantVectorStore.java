package dev.reboot.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.reboot.config.RagProperties;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Qdrant REST 向量库适配器（ADR 0040）。
 *
 * <p>只依赖 JDK HttpClient，避免为单一向量库引入完整客户端栈。集合在首次使用时
 * 按 embedding 维度创建；文档 ID 映射为稳定 UUID，原文与业务 metadata 存入 payload。</p>
 */
public class QdrantVectorStore implements VectorStore {

    private final ObjectMapper objectMapper;
    private final EmbeddingModel embeddingModel;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String collection;
    private final String apiKey;
    private final Duration timeout;
    private final int dimensions;

    public QdrantVectorStore(RagProperties.Qdrant properties,
                             EmbeddingModel embeddingModel,
                             ObjectMapper objectMapper) {
        this(properties, embeddingModel, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
                .build());
    }

    QdrantVectorStore(RagProperties.Qdrant properties,
                      EmbeddingModel embeddingModel,
                      ObjectMapper objectMapper,
                      HttpClient httpClient) {
        String collectionName = properties.getCollection();
        if (collectionName == null || !collectionName.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException(
                    "rag.qdrant.collection must contain only letters, digits, '_' or '-'");
        }
        this.objectMapper = objectMapper;
        this.embeddingModel = embeddingModel;
        this.httpClient = httpClient;
        this.baseUrl = trimTrailingSlash(properties.getBaseUrl());
        this.collection = collectionName;
        this.apiKey = properties.getApiKey() == null ? "" : properties.getApiKey().trim();
        this.timeout = Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds()));
        this.dimensions = embeddingModel.dimensions();
        ensureCollection();
    }

    @Override
    public void add(List<Document> documents) {
        if (documents == null || documents.isEmpty()) return;
        ensureCollection();
        ArrayNode points = objectMapper.createArrayNode();
        for (Document document : documents) {
            float[] vector = embeddingModel.embed(document);
            validateDimensions(vector);
            ObjectNode point = points.addObject();
            point.put("id", pointId(document.getId()));
            addVector(point.putArray("vector"), vector);
            ObjectNode payload = point.putObject("payload");
            payload.put("documentId", document.getId());
            payload.put("content", document.getText());
            payload.set("metadata", objectMapper.valueToTree(document.getMetadata()));
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.set("points", points);
        request("PUT", "/collections/" + collection + "/points?wait=true", body, 200);
    }

    @Override
    public void add(Document document) {
        add(List.of(document));
    }

    @Override
    public List<Document> similaritySearch(String query, int topK) {
        if (topK <= 0) return List.of();
        ensureCollection();
        float[] queryVector = embeddingModel.embed(query);
        validateDimensions(queryVector);

        ObjectNode body = objectMapper.createObjectNode();
        addVector(body.putArray("vector"), queryVector);
        body.put("limit", topK);
        body.put("with_payload", true);
        JsonNode root = request(
                "POST", "/collections/" + collection + "/points/search", body, 200);
        JsonNode result = root.path("result");
        if (!result.isArray()) {
            throw new IllegalStateException("Qdrant search response is missing result[]");
        }

        List<Document> documents = new ArrayList<>(result.size());
        for (JsonNode hit : result) {
            JsonNode payload = hit.path("payload");
            String text = payload.path("content").asText("");
            String documentId = payload.path("documentId").asText(hit.path("id").asText());
            Map<String, Object> metadata = payload.path("metadata").isObject()
                    ? objectMapper.convertValue(payload.path("metadata"),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {})
                    : new LinkedHashMap<>();
            metadata.put("score", hit.path("score").asDouble());
            documents.add(Document.builder()
                    .id(documentId)
                    .text(text)
                    .metadata(metadata)
                    .build());
        }
        return documents;
    }

    @Override
    public int size() {
        ensureCollection();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("exact", true);
        JsonNode root = request(
                "POST", "/collections/" + collection + "/points/count", body, 200);
        return root.path("result").path("count").asInt();
    }

    private synchronized void ensureCollection() {
        HttpResponse<String> response = rawRequest(
                "GET", "/collections/" + collection, null);
        if (response.statusCode() == 404) {
            ObjectNode body = objectMapper.createObjectNode();
            ObjectNode vectors = body.putObject("vectors");
            vectors.put("size", dimensions);
            vectors.put("distance", "Cosine");
            HttpResponse<String> createResponse = rawRequest(
                    "PUT", "/collections/" + collection, body);
            if (isSuccessful(createResponse)) {
                return;
            }
            if (createResponse.statusCode() != 409) {
                throw failure("PUT collection", createResponse);
            }
            // Another instance created the same collection between GET and PUT.
            response = rawRequest("GET", "/collections/" + collection, null);
            if (!isSuccessful(response)) {
                throw failure("GET collection after create conflict", response);
            }
        }
        if (!isSuccessful(response)) {
            throw failure("GET collection", response);
        }
        try {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode vectorParams = root.path("result").path("config").path("params").path("vectors");
            if (vectorParams.has("size") && vectorParams.path("size").asInt() != dimensions) {
                throw new IllegalStateException("Qdrant collection " + collection
                        + " dimension mismatch: collection=" + vectorParams.path("size").asInt()
                        + ", embedding=" + dimensions);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Invalid Qdrant collection response", e);
        }
    }

    private boolean isSuccessful(HttpResponse<String> response) {
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }

    private JsonNode request(String method, String path, JsonNode body, int expectedStatus) {
        HttpResponse<String> response = rawRequest(method, path, body);
        if (response.statusCode() != expectedStatus) {
            throw failure(method + " " + path, response);
        }
        if (response.body() == null || response.body().isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("Invalid Qdrant JSON response", e);
        }
    }

    private HttpResponse<String> rawRequest(String method, String path, JsonNode body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(timeout)
                    .header("Content-Type", "application/json");
            if (!apiKey.isBlank()) {
                builder.header("api-key", apiKey);
            }
            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.method(method, HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body)));
            }
            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("Qdrant request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Qdrant request interrupted", e);
        }
    }

    private IllegalStateException failure(String operation, HttpResponse<String> response) {
        return new IllegalStateException("Qdrant " + operation + " returned HTTP "
                + response.statusCode() + ": " + abbreviate(response.body()));
    }

    private void addVector(ArrayNode array, float[] vector) {
        for (float value : vector) {
            array.add(value);
        }
    }

    private void validateDimensions(float[] vector) {
        if (vector == null || vector.length != dimensions) {
            throw new IllegalStateException("Embedding vector dimension mismatch: expected "
                    + dimensions + ", got " + (vector == null ? 0 : vector.length));
        }
    }

    static String pointId(String documentId) {
        return UUID.nameUUIDFromBytes(documentId.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("rag.qdrant.base-url must not be blank");
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String abbreviate(String body) {
        if (body == null) return "";
        return body.length() <= 300 ? body : body.substring(0, 300) + "...";
    }
}
