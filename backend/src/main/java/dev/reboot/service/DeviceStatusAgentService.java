package dev.reboot.service;

import dev.reboot.client.DeepSeekClient;
import dev.reboot.config.DeepSeekProperties;
import dev.reboot.dto.ai.AiDeviceStatusRequest;
import dev.reboot.dto.ai.AiDeviceStatusResult;
import dev.reboot.dto.ai.AiToolCallTrace;
import dev.reboot.entity.Device;
import dev.reboot.enums.ErrorCode;
import dev.reboot.enums.RoleEnum;
import dev.reboot.exception.BusinessException;
import dev.reboot.mapper.DeviceMapper;
import dev.reboot.tool.DeviceAiTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 设备状态问答 Agent —— Spring AI Function Calling 多轮工具调用循环（Day 68，ADR 0023）。
 *
 * <p>与 {@code AiService} 的告警摘要/设备诊断（单次 ChatClient 调用）不同，本服务实现
 * 「模型请求工具 → 执行工具 → 结果回填对话 → 再次调用」的 ReAct 风格循环：</p>
 * <ul>
 *   <li>工具经 {@code @Tool} 声明式注册（零手写 JSON Schema），由 {@link DeviceAiTools} 提供；</li>
 *   <li>每轮携带 {@code internalToolExecutionEnabled=false}，由本服务手动执行工具调用，从而
 *       精确控制轮次并计数（Spring AI 默认自动循环无上限，见 ADR 0023 §5）；</li>
 *   <li>最大 {@value #MAX_TOOL_ROUNDS} 轮工具调用硬限：达到上限后强制收尾（无工具再调用一次），
 *       结果标注 {@code truncated=true}；</li>
 *   <li>模型未调用任何工具直接回答时，结果标注 {@code referencedRealTime=false}（未参考实时数据）；</li>
 *   <li>当前用户 ID 经 {@link ToolContext} 传给工具，站点资源作用域与业务模块一致（ADR 0020）。</li>
 * </ul>
 *
 * @author AI 助手
 * @since 2026-08-29
 */
@Service
public class DeviceStatusAgentService {

    /** 最大工具调用轮数（硬限，达到后强制收尾）。 */
    public static final int MAX_TOOL_ROUNDS = 3;

    private static final Logger log = LoggerFactory.getLogger(DeviceStatusAgentService.class);

    private static final String SYSTEM_PROMPT = """
            你是工业设备运维 AI 助手。用户会询问某台设备的运行状态、告警情况或站点告警。
            回答前请先调用工具获取实时数据，不要编造：
            - get_device_basic：设备基础信息（状态/位置/网络）；
            - list_device_recent_alarms：单设备最近告警；
            - list_active_alarms_by_site：某站点未处理告警。
            工具返回 JSON；若返回 {"error": "..."}，请如实向用户说明。无法获取数据时明确说明，
            不要臆造数值。回答使用简体中文，简洁专业。""";

    private static final String FINALIZE_HINT = "已达到工具调用轮次上限（"
            + MAX_TOOL_ROUNDS + " 轮）。请仅基于以上已获取的数据与对话给出最终答案，不要再次请求调用工具。";

    private final ChatModel chatModel;
    private final DeepSeekClient deepSeekClient;
    private final DeepSeekProperties properties;
    private final DeviceMapper deviceMapper;
    private final SiteAccessService siteAccessService;
    private final ToolCallback[] toolCallbacks;

    public DeviceStatusAgentService(ChatModel chatModel,
                                    DeepSeekClient deepSeekClient,
                                    DeepSeekProperties properties,
                                    DeviceMapper deviceMapper,
                                    SiteAccessService siteAccessService,
                                    DeviceAiTools deviceAiTools) {
        this.chatModel = chatModel;
        this.deepSeekClient = deepSeekClient;
        this.properties = properties;
        this.deviceMapper = deviceMapper;
        this.siteAccessService = siteAccessService;
        // @Tool 声明式注册 → ToolCallback[]（零手写 JSON Schema，ADR 0023）
        this.toolCallbacks = ToolCallbacks.from(deviceAiTools);
    }

    /**
     * 设备状态问答主入口。
     *
     * <p>校验顺序（TD-033 修复）：先校验请求参数与资源访问权（404/403），
     * 再检查 AI 可用性（503），避免 AI 未启用时掩盖资源访问错误。</p>
     *
     * @throws BusinessException 404 设备不存在 / 403 无站点访问权 / 503 AI 未启用
     */
    public AiDeviceStatusResult answer(AiDeviceStatusRequest request, Long userId) {
        Device device = requireDevice(request.getDeviceId());
        siteAccessService.assertSiteAccess(userId, device.getSiteId(), RoleEnum.VIEWER);
        deepSeekClient.ensureAvailable();

        Map<String, Object> context = new HashMap<>();
        context.put(DeviceAiTools.CONTEXT_USER_ID, userId);
        ToolContext toolContext = new ToolContext(context);

        // 用户问题只带问题文本，模型需要设备 ID 才能填工具参数 → 预置设备上下文
        String userPrompt = "用户询问的设备：ID=" + device.getId()
                + "，名称=" + device.getDeviceName()
                + "，编码=" + device.getDeviceCode()
                + "，站点ID=" + device.getSiteId()
                + "。\n用户问题：" + request.getQuestion();

        List<Message> conversation = new ArrayList<>();
        conversation.add(new SystemMessage(SYSTEM_PROMPT));
        conversation.add(new UserMessage(userPrompt));

        int rounds = 0;
        int toolCallCount = 0;
        List<AiToolCallTrace> trace = new ArrayList<>();

        while (true) {
            ChatResponse response = chatModel.call(new Prompt(conversation, toolOptions(context, true)));
            if (!response.hasToolCalls()) {
                return buildResult(device.getId(), extractText(response),
                        rounds, toolCallCount, rounds > 0, false, trace);
            }
            if (rounds >= MAX_TOOL_ROUNDS) {
                // 硬限：不再执行新工具，强制收尾
                log.warn("AI 工具调用达到 {} 轮硬限，强制收尾（deviceId={}）", MAX_TOOL_ROUNDS, device.getId());
                String answer = forceFinalize(conversation);
                return buildResult(device.getId(), answer,
                        rounds, toolCallCount, rounds > 0, true, trace);
            }

            rounds++;
            AssistantMessage assistant = response.getResult() == null ? null : response.getResult().getOutput();
            if (assistant == null) {
                return buildResult(device.getId(), "",
                        rounds, toolCallCount, rounds > 0, false, trace);
            }
            List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
            for (AssistantMessage.ToolCall toolCall : assistant.getToolCalls()) {
                toolCallCount++;
                ToolCallback callback = findTool(toolCall.name());
                if (callback == null) {
                    log.warn("AI 请求未知工具: {}", toolCall.name());
                    trace.add(new AiToolCallTrace(toolCall.name(), false));
                    toolResponses.add(new ToolResponseMessage.ToolResponse(
                            toolCall.id(), toolCall.name(), "{\"error\":\"未知工具: " + toolCall.name() + "\"}"));
                    continue;
                }
                String result = callback.call(toolCall.arguments(), toolContext);
                trace.add(new AiToolCallTrace(toolCall.name(), isSuccess(result)));
                toolResponses.add(new ToolResponseMessage.ToolResponse(
                        toolCall.id(), toolCall.name(), result));
            }

            List<Message> history = new ArrayList<>(conversation);
            history.add(assistant);
            history.add(new ToolResponseMessage(toolResponses));
            conversation = history;
        }
    }

    /** 达到轮次硬限后：追加收尾提示并无工具调用一次，取最终文本。 */
    private String forceFinalize(List<Message> conversation) {
        List<Message> history = new ArrayList<>(conversation);
        history.add(new UserMessage(FINALIZE_HINT));
        ChatResponse response = chatModel.call(new Prompt(history, toolOptions(Map.of(), false)));
        return extractText(response);
    }

    private AiDeviceStatusResult buildResult(Long deviceId, String answer,
                                             int rounds, int toolCalls,
                                             boolean referencedRealTime, boolean truncated,
                                             List<AiToolCallTrace> trace) {
        AiDeviceStatusResult result = new AiDeviceStatusResult();
        result.setDeviceId(deviceId);
        result.setAnswer(StringUtils.hasText(answer) ? answer : "AI 未返回有效回答");
        result.setToolRounds(rounds);
        result.setToolCalls(toolCalls);
        result.setReferencedRealTime(referencedRealTime);
        result.setTruncated(truncated);
        result.setToolTrace(trace);
        return result;
    }

    /** 组装本轮 Chat 选项：模型参数 + 工具注册 + 关闭 Spring AI 自动工具循环（由本服务手动执行）。 */
    private OpenAiChatOptions toolOptions(Map<String, Object> context, boolean withTools) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(properties.getModel())
                .temperature(properties.getTemperature())
                .maxTokens(properties.getMaxTokens())
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormat.Type.TEXT)
                        .build())
                .internalToolExecutionEnabled(false)
                .toolContext(context);
        if (withTools) {
            builder.toolCallbacks(toolCallbacks);
        }
        return builder.build();
    }

    private ToolCallback findTool(String name) {
        if (name == null) {
            return null;
        }
        for (ToolCallback callback : toolCallbacks) {
            if (name.equals(callback.getToolDefinition().name())) {
                return callback;
            }
        }
        return null;
    }

    private boolean isSuccess(String result) {
        return result != null && !result.contains("\"error\"");
    }

    private String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }

    private Device requireDevice(Long deviceId) {
        if (deviceId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "设备 ID 不能为空");
        }
        Device device = deviceMapper.findById(deviceId);
        if (device == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "设备不存在");
        }
        return device;
    }
}
