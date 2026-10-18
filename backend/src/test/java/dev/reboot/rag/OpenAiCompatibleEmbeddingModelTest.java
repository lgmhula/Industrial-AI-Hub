package dev.reboot.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.reboot.config.RagProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleEmbeddingModelTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void call_sendsOpenAiRequestAndOrdersVectorsByIndex() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/embeddings", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(objectMapper.readTree(exchange.getRequestBody()));
            byte[] response = """
                    {"data":[
                      {"index":1,"embedding":[4.0,5.0,6.0]},
                      {"index":0,"embedding":[1.0,2.0,3.0]}
                    ]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        RagProperties.Embedding properties = embeddingProperties(server.getAddress().getPort());
        OpenAiCompatibleEmbeddingModel model =
                new OpenAiCompatibleEmbeddingModel(properties, objectMapper);

        var response = model.call(new org.springframework.ai.embedding.EmbeddingRequest(
                java.util.List.of("first", "second"), null));

        assertEquals("Bearer test-key", authorization.get());
        assertEquals("test-model", requestBody.get().path("model").asText());
        assertEquals(3, requestBody.get().path("dimensions").asInt());
        assertEquals("first", requestBody.get().path("input").get(0).asText());
        assertArrayEquals(new float[]{1.0f, 2.0f, 3.0f},
                response.getResults().get(0).getOutput());
        assertArrayEquals(new float[]{4.0f, 5.0f, 6.0f},
                response.getResults().get(1).getOutput());
    }

    @Test
    void embedDocument_usesTextAndDimensions() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/embeddings", exchange -> {
            byte[] response = """
                    {"data":[{"index":0,"embedding":[0.1,0.2,0.3]}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        OpenAiCompatibleEmbeddingModel model = new OpenAiCompatibleEmbeddingModel(
                embeddingProperties(server.getAddress().getPort()), objectMapper);

        float[] vector = model.embed(Document.builder().id("1").text("设备温度").build());

        assertEquals(3, vector.length);
        assertEquals(3, model.dimensions());
    }

    @Test
    void constructor_rejectsMissingApiKey() {
        RagProperties.Embedding properties = embeddingProperties(6333);
        properties.setApiKey("");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new OpenAiCompatibleEmbeddingModel(properties, objectMapper));

        assertTrue(error.getMessage().contains("api-key"));
    }

    @Test
    void endpointFailure_includesStatusAndSnippet() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/embeddings", exchange -> {
            byte[] response = "{\"error\":\"bad request\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        OpenAiCompatibleEmbeddingModel model = new OpenAiCompatibleEmbeddingModel(
                embeddingProperties(server.getAddress().getPort()), objectMapper);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> model.embed("test"));

        assertTrue(error.getMessage().contains("HTTP 401"));
    }

    private RagProperties.Embedding embeddingProperties(int port) {
        RagProperties.Embedding properties = new RagProperties.Embedding();
        properties.setBaseUrl("http://127.0.0.1:" + port);
        properties.setApiKey("test-key");
        properties.setModel("test-model");
        properties.setDimensions(3);
        properties.setTimeoutSeconds(5);
        return properties;
    }
}
