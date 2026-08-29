package dev.reboot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.reboot.client.DeepSeekClient;
import dev.reboot.config.DeepSeekProperties;
import dev.reboot.dto.ai.AiAlarmSummary;
import dev.reboot.dto.ai.AiChatRequest;
import dev.reboot.dto.ai.AiChatResult;
import dev.reboot.dto.ai.AiDeviceDiagnosis;
import dev.reboot.dto.ai.KnowledgeChunk;
import dev.reboot.dto.ai.RagAnswerResult;
import dev.reboot.dto.ai.DeepSeekChatRequest;
import dev.reboot.dto.ai.DeepSeekChatResponse;
import dev.reboot.dto.ai.DeepSeekChoice;
import dev.reboot.dto.ai.DeepSeekMessage;
import dev.reboot.entity.Alarm;
import dev.reboot.entity.Device;
import dev.reboot.entity.DeviceData;
import dev.reboot.enums.ErrorCode;
import dev.reboot.enums.RoleEnum;
import dev.reboot.exception.BusinessException;
import dev.reboot.mapper.AlarmMapper;
import dev.reboot.mapper.DeviceDataMapper;
import dev.reboot.mapper.DeviceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 业务服务 —— ChatClient 提示词编排 + 告警摘要/设备诊断结构化解析。
 *
 * <p>告警摘要与设备诊断走 Spring AI {@link ChatClient}（ADR 0022），
 * 通用文本补全保留 {@link DeepSeekClient} 协议层以复用 token 用量/自定义模型能力。</p>
 *
 * <p>站点资源作用域与业务模块一致：单对象访问前调用
 * {@link SiteAccessService#assertSiteAccess}。</p>
 *
 * @author AI 助手
 * @since 2026-08-28
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private static final String ALARM_SYSTEM_PROMPT = "你是工业设备运维 AI 助手。"
            + "请根据设备与告警上下文输出简洁的中文诊断摘要。"
            + "只输出一个 JSON 对象，不要 Markdown 代码块，字段固定为："
            + "summary(摘要), possibleCauses(可能原因数组), suggestedActions(建议处理动作数组), priority(高/中/低)。";

    private static final String DIAGNOSIS_SYSTEM_PROMPT = "你是工业设备健康诊断 AI 助手。"
            + "请基于设备基础信息、最近运行数据和未处理告警评估设备健康状况。"
            + "只输出一个 JSON 对象，不要 Markdown 代码块，字段固定为："
            + "healthLevel(健康/关注/异常), summary(评估摘要), issues(发现的问题数组), suggestedActions(建议动作数组)。";

    private static final String ALARM_USER_TEMPLATE = """
            设备信息：
            - 名称：{deviceName}
            - 编码：{deviceCode}
            - 类型：{deviceType}
            - 位置：{location}
            告警信息：
            - 类型：{alarmType}
            - 等级：{alarmLevel}
            - 内容：{alarmMessage}
            - 触发时间：{triggeredAt}""";

    private static final String DIAGNOSIS_USER_TEMPLATE = """
            设备信息：
            - 名称：{deviceName}
            - 编码：{deviceCode}
            - 类型：{deviceType}
            - 状态：{status}（1=在线, 0=离线）
            - 位置：{location}

            最近运行数据（最多 10 条）：
            {recentData}

            最近告警（最多 5 条）：
            {recentAlarms}""";

    private static final String RAG_SYSTEM_PROMPT = "你是工业设备运维 AI 助手。"
            + "请仅根据提供的知识库片段回答用户问题，不要编造知识库之外的内容。"
            + "如果片段不足以回答，请明确说明知识库中缺少相关信息。";

    private static final String RAG_USER_TEMPLATE = """
            知识库片段：
            {sources}

            用户问题：
            {question}""";

    private static final int RAG_TOP_K = 5;

    private final ChatClient chatClient;
    private final DeepSeekClient deepSeekClient;
    private final DeepSeekProperties properties;
    private final ObjectMapper objectMapper;
    private final AlarmMapper alarmMapper;
    private final DeviceMapper deviceMapper;
    private final DeviceDataMapper deviceDataMapper;
    private final SiteAccessService siteAccessService;
    private final RagRetrievalService ragRetrievalService;

    public AiService(ChatClient chatClient,
                     DeepSeekClient deepSeekClient,
                     DeepSeekProperties properties,
                     ObjectMapper objectMapper,
                     AlarmMapper alarmMapper,
                     DeviceMapper deviceMapper,
                     DeviceDataMapper deviceDataMapper,
                     SiteAccessService siteAccessService,
                     RagRetrievalService ragRetrievalService) {
        this.chatClient = chatClient;
        this.deepSeekClient = deepSeekClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.alarmMapper = alarmMapper;
        this.deviceMapper = deviceMapper;
        this.deviceDataMapper = deviceDataMapper;
        this.siteAccessService = siteAccessService;
        this.ragRetrievalService = ragRetrievalService;
    }

    /** 通用文本补全（可自定义 system prompt / 模型）。 */
    public AiChatResult chat(AiChatRequest request) {
        List<DeepSeekMessage> messages = new ArrayList<>();
        if (StringUtils.hasText(request.getSystemPrompt())) {
            messages.add(DeepSeekMessage.system(request.getSystemPrompt()));
        }
        messages.add(DeepSeekMessage.user(request.getMessage()));

        DeepSeekChatRequest body = new DeepSeekChatRequest();
        body.setModel(StringUtils.hasText(request.getModel()) ? request.getModel() : properties.getModel());
        body.setMessages(messages);
        body.setTemperature(properties.getTemperature());
        body.setMaxTokens(properties.getMaxTokens());
        body.setStream(false);
        return toChatResult(deepSeekClient.chatCompletion(body));
    }

    /** 告警摘要：按告警 + 设备上下文生成诊断摘要与处理建议。 */
    public AiAlarmSummary summarizeAlarm(Long alarmId, Long userId) {
        Alarm alarm = requireAlarm(alarmId);
        Device device = requireDevice(alarm.getDeviceId());
        siteAccessService.assertSiteAccess(userId, device.getSiteId(), RoleEnum.VIEWER);

        String userPrompt = new PromptTemplate(ALARM_USER_TEMPLATE).render(Map.of(
                "deviceName", device.getDeviceName(),
                "deviceCode", device.getDeviceCode(),
                "deviceType", device.getDeviceType(),
                "location", device.getLocation() == null ? "未知" : device.getLocation(),
                "alarmType", alarm.getAlarmType(),
                "alarmLevel", alarm.getAlarmLevel(),
                "alarmMessage", alarm.getAlarmMessage(),
                "triggeredAt", alarm.getTriggeredAt()));

        String content = callJson(ALARM_SYSTEM_PROMPT, userPrompt);
        try {
            return objectMapper.readValue(unwrapJsonFence(content), AiAlarmSummary.class);
        } catch (JsonProcessingException e) {
            log.warn("告警摘要 JSON 解析失败，退回纯文本: {}", e.getMessage());
            AiAlarmSummary fallback = new AiAlarmSummary();
            fallback.setSummary(content);
            fallback.setPossibleCauses(List.of());
            fallback.setSuggestedActions(List.of());
            return fallback;
        }
    }

    /** 设备健康诊断：结合设备基础信息 + 最近数据 + 最近告警生成评估。 */
    public AiDeviceDiagnosis diagnoseDevice(Long deviceId, Long userId) {
        Device device = requireDevice(deviceId);
        siteAccessService.assertSiteAccess(userId, device.getSiteId(), RoleEnum.VIEWER);

        List<DeviceData> recentData = deviceDataMapper.findByDeviceId(deviceId).stream().limit(10).toList();
        List<Alarm> recentAlarms = alarmMapper.findByDeviceId(deviceId).stream().limit(5).toList();

        String userPrompt = new PromptTemplate(DIAGNOSIS_USER_TEMPLATE).render(Map.of(
                "deviceName", device.getDeviceName(),
                "deviceCode", device.getDeviceCode(),
                "deviceType", device.getDeviceType(),
                "status", device.getStatus(),
                "location", device.getLocation() == null ? "未知" : device.getLocation(),
                "recentData", renderRecentData(recentData),
                "recentAlarms", renderRecentAlarms(recentAlarms)));

        String content = callJson(DIAGNOSIS_SYSTEM_PROMPT, userPrompt);
        try {
            return objectMapper.readValue(unwrapJsonFence(content), AiDeviceDiagnosis.class);
        } catch (JsonProcessingException e) {
            log.warn("设备诊断 JSON 解析失败，退回纯文本: {}", e.getMessage());
            AiDeviceDiagnosis fallback = new AiDeviceDiagnosis();
            fallback.setSummary(content);
            fallback.setIssues(List.of());
            fallback.setSuggestedActions(List.of());
            return fallback;
        }
    }

    /** RAG 知识问答：检索相关片段注入上下文后由 ChatClient 回答。 */
    public RagAnswerResult answerWithRag(String question) {
        if (!StringUtils.hasText(question)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "问题不能为空");
        }
        List<KnowledgeChunk> chunks = ragRetrievalService.retrieve(question.trim(), RAG_TOP_K);
        RagAnswerResult result = new RagAnswerResult();
        result.setSources(chunks);
        if (chunks.isEmpty()) {
            result.setAnswer("知识库中未找到相关内容，请先导入设备手册或运维资料。");
            return result;
        }

        deepSeekClient.ensureAvailable();
        String userPrompt = new PromptTemplate(RAG_USER_TEMPLATE).render(Map.of(
                "sources", renderSources(chunks),
                "question", question.trim()));
        String content = chatClient.prompt()
                .system(RAG_SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "AI 服务返回空结果");
        }
        result.setAnswer(content);
        return result;
    }

    private String callJson(String systemPrompt, String userPrompt) {
        deepSeekClient.ensureAvailable();
        String content = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "AI 服务返回空结果");
        }
        return content;
    }

    private String renderRecentData(List<DeviceData> recentData) {
        if (recentData.isEmpty()) {
            return "无数据记录";
        }
        StringBuilder sb = new StringBuilder();
        recentData.forEach(d -> sb.append("- ")
                .append(d.getDataType()).append("=").append(d.getDataValue())
                .append(d.getUnit() == null ? "" : d.getUnit())
                .append(" @ ").append(d.getRecordedAt()).append('\n'));
        return sb.toString().stripTrailing();
    }

    private String renderRecentAlarms(List<Alarm> recentAlarms) {
        if (recentAlarms.isEmpty()) {
            return "无告警记录";
        }
        StringBuilder sb = new StringBuilder();
        recentAlarms.forEach(a -> sb.append("- [")
                .append(a.getAlarmType()).append("/等级").append(a.getAlarmLevel())
                .append("/状态").append(a.getStatus()).append("] ")
                .append(a.getAlarmMessage()).append(" @ ").append(a.getTriggeredAt()).append('\n'));
        return sb.toString().stripTrailing();
    }

    private String renderSources(List<KnowledgeChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunk chunk = chunks.get(i);
            String source = chunk.getSource() == null ? "未知来源" : chunk.getSource();
            Integer chunkIndex = chunk.getChunkIndex();
            sb.append("[").append(i + 1).append("] ")
                    .append(source)
                    .append(chunkIndex == null ? "" : " 片段" + (chunkIndex + 1))
                    .append(": ").append(chunk.getContent()).append('\n');
        }
        return sb.toString().stripTrailing();
    }

    private AiChatResult toChatResult(DeepSeekChatResponse response) {
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()
                || response.getChoices().get(0).getMessage() == null) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "DeepSeek 返回空结果");
        }
        DeepSeekChoice choice = response.getChoices().get(0);
        AiChatResult result = new AiChatResult();
        result.setContent(choice.getMessage().getContent());
        result.setModel(response.getModel());
        result.setFinishReason(choice.getFinishReason());
        if (response.getUsage() != null) {
            result.setPromptTokens(response.getUsage().getPromptTokens());
            result.setCompletionTokens(response.getUsage().getCompletionTokens());
            result.setTotalTokens(response.getUsage().getTotalTokens());
        }
        return result;
    }

    private String unwrapJsonFence(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
                return trimmed.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private Alarm requireAlarm(Long alarmId) {
        Alarm alarm = alarmMapper.findById(alarmId);
        if (alarm == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "告警不存在");
        }
        return alarm;
    }

    private Device requireDevice(Long deviceId) {
        Device device = deviceMapper.findById(deviceId);
        if (device == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "设备不存在");
        }
        return device;
    }
}
