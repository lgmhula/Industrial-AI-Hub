package dev.reboot.service;

import dev.reboot.agent.AgentRunResult;
import dev.reboot.agent.ToolCallingAgent;
import dev.reboot.client.DeepSeekClient;
import dev.reboot.dto.ai.AiInspectionReportResult;
import dev.reboot.enums.ErrorCode;
import dev.reboot.exception.BusinessException;
import dev.reboot.mcp.McpClientService;
import dev.reboot.mcp.McpInspectionSession;
import dev.reboot.mq.InspectionReportMessage;
import dev.reboot.mq.InspectionReportProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 设备巡检日报 Agent（Week 12 Day 83，ADR 0030 + Day 85 Phase 2，ADR 0031 + Day 86 AI→业务报警闭环）。
 *
 * <p>打通 Agent 与 MCP 客户端：一次巡检建立单个 MCP SSE 会话，把 MCP Server 的
 * 只读工具清单适配为 {@code ToolCallback[]} 交给通用 {@link ToolCallingAgent}，
 * 由 DeepSeek 自动列出设备、逐台查询基础信息/运行数据/告警，最后生成中文日报。
 * 入口为 ADMIN 级 JWT REST 端点，MCP 通道本身仍保持只读、无用户身份。</p>
 *
 * <h3>Day 85 Phase 2 — Agent 接入 MQ Producer（ADR 0031 §3.1 / §6）</h3>
 * <p>{@code generate()} 末尾将 {@link AiInspectionReportResult} 转换为
 * {@link InspectionReportMessage} 并经 {@link InspectionReportProducer} 投递到
 * {@code inspection.exchange}。<b>Agent 不感知 SSE / Push Gateway</b>：
 * 仅依赖 Producer（MQ 边界），不持有任何 emitter / userId → 连接映射。</p>
 *
 * <h4>失败策略（ADR 0031 §6 RabbitMQ 异常行）</h4>
 * <ul>
 *   <li>RabbitMQ 不可达时 {@link InspectionReportProducer#send} 抛 {@link AmqpException}，
 *       本类 catch 后降级日志，<b>不阻塞 Agent 主流程</b>，{@code generate()} 仍正常返回 result；</li>
 *   <li>Producer 在 test profile 下不存在（{@code @Profile("!test")}），
 *       构造器用 {@link Nullable} 注入；为 null 时跳过投递，不影响上下文加载。</li>
 * </ul>
 *
 * <h3>Day 86 — AI 与业务闭环：自动生成业务报警（AiAlarmAutoCreator）</h3>
 * <p>{@code generate()} 在日报生成后、MQ 投递<b>前</b>调用 {@link AiAlarmAutoCreator#createAlarms(Long, AiInspectionReportResult)}。
 * 选择 MQ 投递<b>前</b>的原因：让 alarm 写入与日报生成处于同一同步段，
 * 若 MQ 暂不可达（降级日志），AI 自动报警仍被落盘，不出现漏报警。</p>
 * <ul>
 *   <li>AI 识别到异常后写入 reboot.alarm（status=0 未处理），
 *       走已有告警列表/确认/解决/审计全链路；</li>
 *   <li>AiAlarmAutoCreator 不对外抛异常：设备反查失败、Redis 不可用、DB 异常全部
 *       内部降级不阻塞 Agent 主流程；</li>
 *   <li>AiAlarmAutoCreator 在 test profile 下可为 null（空 Service 未装配），
 *       构造器用 {@link Nullable} 注入，为 null 时跳过报警生成（对齐 test profile 下 MQ 装配策略）。</li>
 * </ul>
 *
 * @author AI 助手
 * @since 2026-08-30, Day 85 Phase 2 接入 MQ Producer 2026-08-31, Day 86 AI→ALARM 闭环 2026-09-01
 */
@Service
public class McpInspectionAgentService {

    private static final Logger log = LoggerFactory.getLogger(McpInspectionAgentService.class);

    /** 巡检跨设备多轮查询，显式放宽到 6 轮（ADR 0026 硬限治理）。 */
    private static final int MAX_TOOL_ROUNDS = 6;

    private static final String SYSTEM_PROMPT = """
            你是工业设备巡检 Agent，通过 MCP 只读工具访问真实设备数据。请按以下流程执行：
            1. 调用 mcp_list_devices（limit=20）获取设备清单；
            2. 对每台设备调用 mcp_get_device_basic 获取基础信息；
            3. 对每台设备调用 mcp_list_device_recent_data（limit=10）与
               mcp_list_device_recent_alarms（limit=10）获取最近运行数据与告警；
            4. 完成数据采集后生成简体中文巡检日报。
            日报必须包含：日期、设备总数、在线/离线/维护中统计、各设备关键运行指标与告警摘要、
            异常风险与处置建议。只依据工具返回的真实数据，不得臆造数值；工具返回 {"error":"..."}
            时如实说明受限项。设备超过 10 台时，优先巡检离线/维护中或带告警的设备，
            其余设备基于清单做总体统计，并在日报中注明未逐台巡检。""";

    private static final String USER_PROMPT =
            "请对当前全部设备执行一次自动巡检并生成今日巡检日报。日期：" + LocalDate.now();

    private final DeepSeekClient deepSeekClient;
    private final McpClientService mcpClientService;
    private final ToolCallingAgent toolCallingAgent;
    /**
     * 巡检日报 MQ 投递侧（ADR 0031 Phase 1）。
     * <p>test profile 下为 null（{@link InspectionReportProducer} 标注 {@code @Profile("!test")}），
     * 构造器用 {@link Nullable} 容忍；generate() 中 null 检查后调用，避免 NPE。</p>
     */
    @Nullable
    private final InspectionReportProducer inspectionReportProducer;
    /**
     * AI 巡检异常 → 业务报警 自动生成（Day 86，AiAlarmAutoCreator 非 profile 限制）。
     * <p>test profile 下 McpInspectionAgentServiceTest 若未提供 @MockBean，可显式传 null，
     * 构造器以 {@link Nullable} 容忍，generate() 中跳过。</p>
     */
    @Nullable
    private final AiAlarmAutoCreator aiAlarmAutoCreator;

    public McpInspectionAgentService(DeepSeekClient deepSeekClient,
                                     McpClientService mcpClientService,
                                     ToolCallingAgent toolCallingAgent,
                                     @Nullable InspectionReportProducer inspectionReportProducer,
                                     @Nullable AiAlarmAutoCreator aiAlarmAutoCreator) {
        this.deepSeekClient = deepSeekClient;
        this.mcpClientService = mcpClientService;
        this.toolCallingAgent = toolCallingAgent;
        this.inspectionReportProducer = inspectionReportProducer;
        this.aiAlarmAutoCreator = aiAlarmAutoCreator;
    }

    /** 执行一次设备巡检并生成日报（调用方需为 ADMIN，见 AiController）。 */
    public AiInspectionReportResult generate() {
        deepSeekClient.ensureAvailable();
        try (McpInspectionSession session = mcpClientService.openInspectionSession()) {
            AgentRunResult run = toolCallingAgent.run(SYSTEM_PROMPT, USER_PROMPT,
                    new ToolContext(Map.of()), session.toolCallbacks(), MAX_TOOL_ROUNDS);
            AiInspectionReportResult result = toResult(run, session);
            // Day 86：MQ 投递前先写业务报警（让 alarm 落盘不依赖 MQ 可达性）
            autoCreateAlarms(result);
            dispatchReport(result);
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("MCP 巡检失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "MCP 巡检失败: " + e.getMessage());
        }
    }

    /**
     * Day 86：AI 识别异常 → 业务报警自动生成（AiAlarmAutoCreator 永不抛异常）。
     *
     * <p>AutoCreator 为 null → 跳过（test profile 或未显式装配，和 MQ 策略一致）。
     * AiAlarmAutoCreator.createAlarms 内部对单条 issue / Redis / DB 异常已全部降级。</p>
     */
    private void autoCreateAlarms(AiInspectionReportResult result) {
        if (aiAlarmAutoCreator == null) {
            return;
        }
        try {
            aiAlarmAutoCreator.createAlarms(null, result);
        } catch (RuntimeException ex) {
            // 二次兜底（理论上 createAlarms 内部已全部 catch）：防未来实现疏漏导致主流程中断
            log.warn("AiAlarmAutoCreator 未预期异常，AI 自动报警阶段跳过（不影响日报/MQ/SSE）", ex);
        }
    }

    /**
     * 投递巡检日报到 MQ（Day 85 Phase 2，ADR 0031 §3.1 / §6）。
     *
     * <p><b>不阻塞主流程</b>：RabbitMQ 不可达时 catch {@link AmqpException} 降级日志，
     * 日报已生成的 {@link AiInspectionReportResult} 仍正常返回给调用方。
     * Producer 为 null（test profile）时跳过投递。</p>
     */
    private void dispatchReport(AiInspectionReportResult result) {
        if (inspectionReportProducer == null) {
            // test profile 下 Producer 不存在，跳过投递（不阻塞、不报错）
            return;
        }
        InspectionReportMessage message = toMessage(result);
        try {
            inspectionReportProducer.send(message);
        } catch (AmqpException e) {
            // ADR 0031 §6 RabbitMQ 异常行：日志记录待发日报，不阻塞 Agent 主流程
            log.warn("MQ 投递失败，日报已生成但未推送前端（可手动重投）: {}", message, e);
        }
    }

    /**
     * 将 {@link AiInspectionReportResult} 转换为 {@link InspectionReportMessage}（Day 85 Phase 2 / Day 87 字段扩充）。
     *
     * <p>字段映射对齐 ADR 0031 §3.1 消息契约 + Day 86 AI→ALARM 闭环新增段：
     * <ul>
     *   <li>{@code siteIds} = 空 List —— 当前 Agent 是 ADMIN 全站点巡检，
     *       空集表示全站点（ADR 0031 §5.4 ADMIN 语义）；</li>
     *   <li>{@code triggeredByUserId} = null —— 当前 generate() 无 userId 参数，
     *       由 Controller 层在 Phase 6 接入时补充（ADR 0031 §5.5：仅审计用，
     *       Consumer/Push Gateway 不得据此越权路由）；</li>
     *   <li>{@code autoAlarmCount / detectedIssues} —— Day 86 AiAlarmAutoCreator
     *       从 AI 结构化异常生成的报警数量与异常清单，前端 SSE 直接消费渲染。</li>
     *   <li>其余字段一一对应 {@link AiInspectionReportResult}。</li>
     * </ul>
     * </p>
     */
    private InspectionReportMessage toMessage(AiInspectionReportResult result) {
        InspectionReportMessage message = new InspectionReportMessage();
        message.setReportDate(result.getReportDate());
        message.setReport(result.getReport());
        message.setToolRounds(result.getToolRounds());
        message.setToolCalls(result.getToolCalls());
        message.setDeviceCount(result.getDeviceCount());
        message.setAlarmCount(result.getAlarmCount());
        message.setTruncated(result.isTruncated());
        // Day 87 前端 AI 展示补强：AI 结构化异常 + 自动生成报警数量
        message.setAutoAlarmCount(result.getAutoAlarmCount());
        message.setDetectedIssues(result.getDetectedIssues());
        message.setSiteIds(List.of()); // ADMIN 全站点语义
        message.setTriggeredByUserId(null); // Phase 6 由 Controller 层注入
        message.setGeneratedAt(LocalDateTime.now());
        return message;
    }

    private AiInspectionReportResult toResult(AgentRunResult run, McpInspectionSession session) {
        AiInspectionReportResult result = new AiInspectionReportResult();
        result.setReportDate(LocalDate.now());
        result.setReport(StringUtils.hasText(run.answer()) ? run.answer() : "AI 未返回有效巡检日报");
        result.setToolRounds(run.toolRounds());
        result.setToolCalls(run.toolCalls());
        result.setDeviceCount(session.deviceCount());
        result.setAlarmCount(session.alarmCount());
        result.setTruncated(run.truncated());
        result.setToolTrace(run.toolTrace());
        return result;
    }
}
