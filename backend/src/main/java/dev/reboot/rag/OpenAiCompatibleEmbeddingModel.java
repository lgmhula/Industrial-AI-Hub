package dev.reboot.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.reboot.config.RagProperties;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * OpenAI-compatible {@code /embeddings} 适配器。
 *
 * <p>仅依赖 JDK HttpClient，兼容 OpenAI、SiliconFlow、Ollama 等提供
 * OpenAI 协议的服务。默认不启用；所选 provider 未配置 API Key 时启动失败。</p>
 */
public class OpenAiCompatibleEmbeddingModel implements EmbeddingModel {

    private static final int DEFAULT_DIMENSIONS = 1536;

    private final String endpoint;
    private final String apiKey;
    private final String model;
    private final int configuredDimensions;
    private final boolean sendDimensions;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Duration timeout;

    public OpenAiCompatibleEmbeddingModel(RagProperties.Embedding properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())))
                .build());
    }

    OpenAiCompatibleEmbeddingModel(RagProperties.Embedding properties,
                                   ObjectMapper objectMapper,
                                   HttpClient httpClient) {
        if (!StringUtils.hasText(properties.getBaseUrl())) {
            throw new IllegalArgumentException("rag.embedding.base-url must not be blank");
        }
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new IllegalArgumentException(
                    "rag.embedding.api-key is required when provider=openai-compatible");
        }
        if (!StringUtils.hasText(properties.getModel())) {
            throw new IllegalArgumentException("rag.embedding.model must not be blank");
        }
        this.endpoint = trimTrailingSlash(properties.getBaseUrl()) + "/embeddings";
        this.apiKey = properties.getApiKey();
        this.model = properties.getModel();
        this.configuredDimensions = properties.getDimensions();
        this.sendDimensions = properties.isSendDimensions();
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.timeout = Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds()));
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> instructions = request.getInstructions();
        if (instructions == null || instructions.isEmpty()) {
            return new EmbeddingResponse(List.of());
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            ArrayNode input = body.putArray("input");
            instructions.forEach(input::add);
            if (sendDimensions && configuredDimensions > 0) {
                body.put("dimensions", configuredDimensions);
            }

            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Embedding endpoint returned HTTP "
                        + response.statusCode() + ": " + abbreviate(response.body()));
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode data = root.path("data");
            if (!data.isArray() || data.size() != instructions.size()) {
                throw new IllegalStateException("Embedding response size mismatch: expected "
                        + instructions.size() + ", got " + data.size());
            }
            List<Embedding> ordered = new ArrayList<>(data.size());
            for (JsonNode item : data) {
                int index = item.path("index").asInt(ordered.size());
                float[] vector = parseVector(item.path("embedding"));
                validateDimensions(vector);
                ordered.add(new Embedding(vector, index));
            }
            ordered.sort(Comparator.comparing(Embedding::getIndex));
            return new EmbeddingResponse(ordered);
        } catch (IOException e) {
            throw new IllegalStateException("Embedding request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Embedding request interrupted", e);
        }
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public float[] embed(String text) {
        EmbeddingResponse response = call(new EmbeddingRequest(
                List.of(text == null ? "" : text), null));
        if (response.getResults().isEmpty()) {
            throw new IllegalStateException("Embedding endpoint returned no vectors");
        }
        return response.getResults().getFirst().getOutput();
    }

    @Override
    public int dimensions() {
        return configuredDimensions > 0 ? configuredDimensions : DEFAULT_DIMENSIONS;
    }

    private float[] parseVector(JsonNode embedding) {
        if (!embedding.isArray() || embedding.isEmpty()) {
            throw new IllegalStateException("Embedding response contains an empty vector");
        }
        float[] vector = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            vector[i] = (float) embedding.get(i).asDouble();
        }
        return vector;
    }

    private void validateDimensions(float[] vector) {
        if (configuredDimensions > 0 && vector.length != configuredDimensions) {
            throw new IllegalStateException("Embedding dimension mismatch: configured "
                    + configuredDimensions + ", got " + vector.length);
        }
    }

    private static String trimTrailingSlash(String value) {
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
