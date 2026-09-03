package dev.reboot.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * AI 自由文本 → 目标 DTO 解析的公共兜底工具（Day 89 Phase 4 AI 模块重构）。
 *
 * <p>工业 AI 模块中大量「调用 LLM → 要求返回 JSON → 解析成 Java DTO → 失败退回纯文本」
 * 的代码模式（AiService.summarizeAlarm / AiService.diagnoseDevice / rag / agent 总结 / …）。
 * 在 Day 89 之前，每处各写一份 try/catch/warn/fallback，行为不完全一致：
 * <ul>
 *     <li>有的 unwrap ```json fence，有的不；</li>
 *     <li>有的 fallback 时所有 list 字段设为空 List，有的干脆 null；</li>
 *     <li>有的 warn 级别日志含模型输出片段，有的不含。</li>
 * </ul>
 * 本工具统一上述所有差异为一个入口：{@link #parseOrFallback(String, Class, ObjectMapper, Function, BiConsumer)}。</p>
 *
 * <h3>XSS 备注</h3>
 * <p>本工具不做 HTML 转义。因为：① AI 输出在后端可能被多种通道消费（DB/HTTP/Email/钉钉），只有最终渲染端才知道应该转义成 HTML/Markdown/JSON；
 * ② 前端统一由 escapeHtml.js 做转义。如果后端要把 AI 输出直接 <b>嵌入 HTML 模板</b>（例如未来的邮件通知），
 * 消费方需要额外走 org.springframework.web.util.HtmlUtils.htmlEscape。</p>
 *
 * @author AI 助手 + hula0710
 * @since Day 89（Phase 4 AI 模块重构，消去 4 处重复 JSON 解析降级）
 */
public final class AiJsonFallbackUtil {

    private static final Logger log = LoggerFactory.getLogger(AiJsonFallbackUtil.class);

    /**
     * {@code readValue()} 的 {@link StreamReadConstraints} 默认最大字符串长度
     * （Jackson 2.15+ 默认 5 MB，此处显式锁 2 MB 避免 AI 超长输出 OOM）。
     */
    public static final int MAX_JSON_STRING_LEN = 2 * 1024 * 1024;

    private AiJsonFallbackUtil() {
    }

    /**
     * 把 AI 返回的自由文本解析成目标类型 {@code targetClass}，并统一处理：
     * <ol>
     *     <li>trim + {@linkplain #unwrapJsonFence(String) 移除 ```json 代码块围栏}；</li>
     *     <li>Jackson 解析；</li>
     *     <li>解析失败：WARN 日志（含异常消息，不含完整 AI 输出防止日志爆炸）
     *         → 调用 {@code fallbackFactory.apply(rawContent)} 返回兜底对象。</li>
     * </ol>
     *
     * @param content             AI 返回的原始内容（可能含 ```json fence）
     * @param targetClass         目标类型，不能为 null
     * @param mapper              Jackson ObjectMapper（用项目已配置的，避免新造）
     * @param fallbackFactory     兜底工厂：输入 = 原始未解析 content，输出 = 填充好的 fallback DTO（通常把 content 塞到 summary 字段）
     * @param warnHandler         可选，非 null 时在降级场景追加执行（如写 operation_log detail 的 note）
     * @return 成功时返回解析结果；失败时返回 fallbackFactory 的产出；若 content 为空字符串
     *         也返回 fallbackFactory.apply("")
     */
    public static <T> T parseOrFallback(@Nullable String content,
                                        Class<T> targetClass,
                                        ObjectMapper mapper,
                                        Function<String, T> fallbackFactory,
                                        @Nullable BiConsumer<String, Exception> warnHandler) {
        if (targetClass == null) throw new IllegalArgumentException("targetClass 不能为 null");
        if (mapper == null) throw new IllegalArgumentException("ObjectMapper 不能为 null");
        if (fallbackFactory == null) throw new IllegalArgumentException("fallbackFactory 不能为 null");

        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            T f = fallbackFactory.apply("");
            if (warnHandler != null) warnHandler.accept("AI 返回空内容", null);
            return f;
        }
        String json = unwrapJsonFence(trimmed);
        try {
            // 防御：单次 readValue 字节长度上限（2MB），防止 AI 输出过大导致 GC 抖动
            if (json.length() > MAX_JSON_STRING_LEN) {
                log.warn("AI JSON 输出过长（{} 字符，上限 {}），退回纯文本降级", json.length(), MAX_JSON_STRING_LEN);
                T f = fallbackFactory.apply(trimmed);
                if (warnHandler != null) warnHandler.accept("AI JSON 输出过长", null);
                return f;
            }
            return mapper.readValue(json, targetClass);
        } catch (JsonProcessingException e) {
            log.warn("AI JSON 解析失败（{}），退回纯文本降级: {}", targetClass.getSimpleName(), e.getOriginalMessage());
            T f = fallbackFactory.apply(trimmed);
            if (warnHandler != null) warnHandler.accept(e.getOriginalMessage(), e);
            return f;
        }
    }

    /**
     * 3 参数便捷版（不自定义 warnHandler）。
     *
     * @see #parseOrFallback(String, Class, ObjectMapper, Function, BiConsumer)
     */
    public static <T> T parseOrFallback(@Nullable String content,
                                        Class<T> targetClass,
                                        ObjectMapper mapper,
                                        Function<String, T> fallbackFactory) {
        return parseOrFallback(content, targetClass, mapper, fallbackFactory, null);
    }

    /**
     * 去除 AI 常见的 markdown JSON fence：<pre>{@code
     * ```json
     * { "priority": "高" }
     * ```
     * }</pre>
     *
     * 若开头没有 fence，或找不到闭合 fence，原样返回 trim 后文本。
     *
     * @param trimmed 非空、已 trim 的字符串
     */
    public static String unwrapJsonFence(String trimmed) {
        if (trimmed == null) return "";
        if (!trimmed.startsWith("```")) return trimmed;
        int firstLineEnd = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
            return trimmed.substring(firstLineEnd + 1, lastFence).trim();
        }
        return trimmed;
    }
}
