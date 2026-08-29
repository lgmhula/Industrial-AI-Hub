package dev.reboot.service;

import dev.reboot.agent.AgentRunResult;
import dev.reboot.agent.ToolCallingAgent;
import dev.reboot.client.DeepSeekClient;
import dev.reboot.dto.ai.AiDeviceStatusRequest;
import dev.reboot.dto.ai.AiDeviceStatusResult;
import dev.reboot.entity.Device;
import dev.reboot.enums.ErrorCode;
import dev.reboot.enums.RoleEnum;
import dev.reboot.exception.BusinessException;
import dev.reboot.mapper.DeviceMapper;
import dev.reboot.tool.DeviceAiTools;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 设备分析多步 Agent（Week 12 Day 79，ADR 0026）。
 *
 * <p>引导模型按“先查设备基础信息 → 再查最近运行数据 → 结合告警分析”的顺序
 * 完成多步推理，复用通用 {@link ToolCallingAgent} 手动工具循环。</p>
 *
 * @author AI 助手
 * @since 2026-08-29
 */
@Service
public class DeviceAnalysisAgentService {

    private static final String SYSTEM_PROMPT = """
            你是工业设备分析 Agent。用户要求分析某台设备时，请按以下顺序调用工具获取真实数据：
            1. get_device_basic 获取设备基础信息；
            2. list_device_recent_data 获取最近运行数据；
            3. list_device_recent_alarms 获取最近告警（可选）。
            完成数据获取后，基于真实数据给出运行状态分析、风险判断与建议。
            只依据工具返回的数据回答，不得臆造数值；工具返回 JSON，若含 {"error":"..."} 请如实说明。
            回答使用简体中文，简洁专业。""";

    private final DeepSeekClient deepSeekClient;
    private final DeviceMapper deviceMapper;
    private final SiteAccessService siteAccessService;
    private final DeviceAiTools deviceAiTools;
    private final ToolCallingAgent toolCallingAgent;
    private final ToolCallback[] toolCallbacks;

    public DeviceAnalysisAgentService(DeepSeekClient deepSeekClient,
                                      DeviceMapper deviceMapper,
                                      SiteAccessService siteAccessService,
                                      DeviceAiTools deviceAiTools,
                                      ToolCallingAgent toolCallingAgent) {
        this.deepSeekClient = deepSeekClient;
        this.deviceMapper = deviceMapper;
        this.siteAccessService = siteAccessService;
        this.deviceAiTools = deviceAiTools;
        this.toolCallingAgent = toolCallingAgent;
        this.toolCallbacks = ToolCallbacks.from(deviceAiTools);
    }

    public AiDeviceStatusResult analyze(AiDeviceStatusRequest request, Long userId) {
        Device device = requireDevice(request.getDeviceId());
        siteAccessService.assertSiteAccess(userId, device.getSiteId(), RoleEnum.VIEWER);
        deepSeekClient.ensureAvailable();

        Map<String, Object> context = new HashMap<>();
        context.put(DeviceAiTools.CONTEXT_USER_ID, userId);
        ToolContext toolContext = new ToolContext(context);

        String userPrompt = "待分析设备：ID=" + device.getId()
                + "，名称=" + device.getDeviceName()
                + "，编码=" + device.getDeviceCode()
                + "，站点ID=" + device.getSiteId()
                + "。\n用户问题：" + request.getQuestion();

        AgentRunResult run = toolCallingAgent.run(SYSTEM_PROMPT, userPrompt, toolContext,
                toolCallbacks, 4);
        return toResult(device.getId(), run);
    }

    private AiDeviceStatusResult toResult(Long deviceId, AgentRunResult run) {
        AiDeviceStatusResult result = new AiDeviceStatusResult();
        result.setDeviceId(deviceId);
        result.setAnswer(StringUtils.hasText(run.answer()) ? run.answer() : "AI 未返回有效回答");
        result.setToolRounds(run.toolRounds());
        result.setToolCalls(run.toolCalls());
        result.setReferencedRealTime(run.referencedRealTime());
        result.setTruncated(run.truncated());
        result.setToolTrace(run.toolTrace());
        return result;
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
