package dev.reboot.rag;

import dev.reboot.config.RagProperties;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 本地确定性哈希向量模型（ADR 0024）。
 *
 * <p>实现 Spring AI {@link EmbeddingModel}，让知识库链路可离线单测。字符 n-gram
 * 与词 token 经哈希投影到固定维度并 L2 归一化，相似片段余弦相似度更高。生产环境
 * 可替换为真实 embedding 模型，不影响切片/存储/检索代码。</p>
 *
 * @author AI 助手
 * @since 2026-08-29
 */
@Component
public class LocalHashEmbeddingModel implements EmbeddingModel {

    private final int dimensions;

    public LocalHashEmbeddingModel(RagProperties properties) {
        this.dimensions = Math.max(1, properties.getEmbeddingDimensions());
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> inputs = request.getInstructions();
        List<Embedding> embeddings = new ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            embeddings.add(new Embedding(embed(inputs.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public float[] embed(String text) {
        return hashEmbed(text == null ? "" : text, dimensions);
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    private float[] hashEmbed(String text, int dimension) {
        float[] vector = new float[dimension];
        String normalized = text.toLowerCase().trim();
        if (normalized.isEmpty()) {
            return vector;
        }

        for (String token : normalized.split("[^\\p{L}\\p{N}]+")) {
            if (!token.isEmpty()) {
                addHash(vector, token, 1.0f);
            }
        }

        String compact = normalized.replaceAll("\\s+", "");
        for (int n = 1; n <= 3 && n <= compact.length(); n++) {
            for (int i = 0; i <= compact.length() - n; i++) {
                addHash(vector, compact.substring(i, i + n), 0.5f);
            }
        }
        return normalize(vector);
    }

    private void addHash(float[] vector, String token, float weight) {
        int hash = token.hashCode();
        int index = Math.floorMod(hash, vector.length);
        int sign = (hash & 1) == 0 ? 1 : -1;
        vector[index] += sign * weight;
    }

    private float[] normalize(float[] vector) {
        double norm = 0.0;
        for (float value : vector) {
            norm += (double) value * value;
        }
        if (norm > 0) {
            float length = (float) Math.sqrt(norm);
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= length;
            }
        }
        return vector;
    }
}
