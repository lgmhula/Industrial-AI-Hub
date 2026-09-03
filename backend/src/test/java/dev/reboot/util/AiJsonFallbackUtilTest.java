package dev.reboot.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AiJsonFallbackUtil 单测（Day 89 Phase 4 AI 模块重构）。
 * 覆盖 5 场景：正常 JSON / JSON fence ```json / 非法 JSON→fallback 字段正确 / 空串→fallback / 超长→降级
 *      + 附加 unwrap 单独测 2 条 / parseOrFallback null mapper/klass/factory 防错 2 条
 * = 共 9 test methods。
 */
class AiJsonFallbackUtilTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    /** 目标 DTO：和 AiAlarmSummary 字段同构，便于测试。 */
    public static class SampleDto {
        public String priority;
        public String summary;
        public List<String> possibleCauses;
        public List<String> suggestedActions;

        public SampleDto() {}
    }

    @Test
    void parseOrFallback_normalJson() {
        String raw = "{\"priority\":\"高\",\"summary\":\"温度超标\",\"possibleCauses\":[\"散热不佳\"],\"suggestedActions\":[\"检查风扇\"]}";
        SampleDto r = AiJsonFallbackUtil.parseOrFallback(raw, SampleDto.class, mapper, c -> {
            SampleDto f = new SampleDto(); f.summary = "fallback:" + c; return f;
        });
        assertEquals("高", r.priority);
        assertEquals("温度超标", r.summary);
        assertEquals(List.of("散热不佳"), r.possibleCauses);
        assertEquals(List.of("检查风扇"), r.suggestedActions);
    }

    @Test
    void parseOrFallback_jsonFenceUnwrapped() {
        String raw = "```json\n" +
                "{\"priority\":\"中\",\"summary\":\"正常波动\"}\n" +
                "```";
        SampleDto r = AiJsonFallbackUtil.parseOrFallback(raw, SampleDto.class, mapper, c -> {
            SampleDto f = new SampleDto(); f.summary = "fallback"; return f;
        });
        assertEquals("中", r.priority);
        assertEquals("正常波动", r.summary);
    }

    /** 三 ``` fence 但模型忘记关闭，比如只有开头：不解析（不抛）直接降级。 */
    @Test
    void parseOrFallback_malformedFence_goesFallback() {
        String raw = "```json\n这是纯文本回答，不是合法 JSON";
        SampleDto r = AiJsonFallbackUtil.parseOrFallback(raw, SampleDto.class, mapper, c -> {
            SampleDto f = new SampleDto(); f.summary = "fb:" + c; return f;
        });
        assertNull(r.priority); // fallback 不填 priority
        assertEquals("fb:" + raw, r.summary); // 内容是原始 fence 文本
    }

    @Test
    void parseOrFallback_invalidJson_goesFallback() {
        String raw = "模型返回的纯文本摘要：设备很正常";
        SampleDto r = AiJsonFallbackUtil.parseOrFallback(raw, SampleDto.class, mapper, c -> {
            SampleDto f = new SampleDto(); f.summary = "fb:" + c;
            f.possibleCauses = List.of();
            f.suggestedActions = List.of();
            return f;
        });
        assertEquals("fb:模型返回的纯文本摘要：设备很正常", r.summary);
        assertEquals(List.of(), r.possibleCauses);
        assertEquals(List.of(), r.suggestedActions);
    }

    @Test
    void parseOrFallback_nullOrEmpty_goesFallbackEmpty() {
        SampleDto r1 = AiJsonFallbackUtil.parseOrFallback(null, SampleDto.class, mapper, c -> {
            SampleDto f = new SampleDto(); f.summary = "null"; return f;
        });
        assertEquals("null", r1.summary);

        SampleDto r2 = AiJsonFallbackUtil.parseOrFallback("", SampleDto.class, mapper, c -> {
            SampleDto f = new SampleDto(); f.summary = "empty"; return f;
        });
        assertEquals("empty", r2.summary);

        SampleDto r3 = AiJsonFallbackUtil.parseOrFallback("   ", SampleDto.class, mapper, c -> {
            SampleDto f = new SampleDto(); f.summary = "blank"; return f;
        });
        assertEquals("blank", r3.summary);
    }

    /** 超过 MAX_JSON_STRING_LEN (2MB) → 不抛异常，降级为 fallback。 */
    @Test
    void parseOrFallback_hugeJson_goesFallback() {
        // 构造 ~2.2MB 合法 JSON：{"priority":"重复字符"}——超过 2MB 上限即降级，不花时间解析 OOM。
        int size = 2 * 1024 * 1024 + 5000;
        StringBuilder sb = new StringBuilder(size + 32);
        sb.append("{\"summary\":\"");
        sb.append("x".repeat(size));
        sb.append("\"}");
        String huge = sb.toString();
        SampleDto r = AiJsonFallbackUtil.parseOrFallback(huge, SampleDto.class, mapper, c -> {
            SampleDto f = new SampleDto(); f.summary = "huge"; return f;
        });
        assertEquals("huge", r.summary);
    }

    @Test
    void unwrapJsonFence_plainText_noChange() {
        assertEquals("hello", AiJsonFallbackUtil.unwrapJsonFence("hello"));
    }

    @Test
    void unwrapJsonFence_stripsCodeFenceWithLanguage() {
        String fence = "```json\n{\"a\":1}\n```";
        assertEquals("{\"a\":1}", AiJsonFallbackUtil.unwrapJsonFence(fence));
    }

    /** 参数防御：null targetClass → 抛 IllegalArgumentException，跟 Javadoc 一致。 */
    @Test
    void parseOrFallback_nullTargetClass_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                AiJsonFallbackUtil.parseOrFallback("{}", null, mapper, c -> new SampleDto()));
        assertThrows(IllegalArgumentException.class, () ->
                AiJsonFallbackUtil.parseOrFallback("{}", SampleDto.class, null, c -> new SampleDto()));
        assertThrows(IllegalArgumentException.class, () ->
                AiJsonFallbackUtil.parseOrFallback("{}", SampleDto.class, mapper, null));
    }
}
