package dev.reboot.rag;

import dev.reboot.config.RagProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LocalHashEmbeddingModel 单元测试。
 *
 * @author AI 助手
 * @since 2026-08-29
 */
class LocalHashEmbeddingModelTest {

    private LocalHashEmbeddingModel embeddingModel;

    @BeforeEach
    void setUp() {
        RagProperties properties = new RagProperties();
        properties.setEmbeddingDimensions(64);
        embeddingModel = new LocalHashEmbeddingModel(properties);
    }

    @Test
    void embed_shouldReturnVectorOfConfiguredDimension() {
        float[] vector = embeddingModel.embed("设备温度过高");

        assertEquals(64, vector.length);
    }

    @Test
    void embed_shouldBeDeterministic() {
        assertArrayEquals(embeddingModel.embed("温度过高"), embeddingModel.embed("温度过高"));
    }

    @Test
    void embed_similarText_shouldHaveHigherCosineThanUnrelatedText() {
        float[] left = embeddingModel.embed("设备温度过高传感器读数异常");
        float[] similar = embeddingModel.embed("温度过高传感器读数异常告警");
        float[] unrelated = embeddingModel.embed("用户登录成功角色权限配置");

        assertTrue(cosine(left, similar) > cosine(left, unrelated));
    }

    @Test
    void embed_blankText_shouldReturnZeroVector() {
        float[] vector = embeddingModel.embed("   ");

        assertEquals(64, vector.length);
        for (float value : vector) {
            assertEquals(0.0f, value);
        }
    }

    @Test
    void dimensions_shouldReturnConfiguredDimension() {
        assertEquals(64, embeddingModel.dimensions());
    }

    private double cosine(float[] left, float[] right) {
        double dot = 0.0;
        for (int i = 0; i < left.length; i++) {
            dot += (double) left[i] * right[i];
        }
        return dot;
    }
}
