package dev.reboot.agent;

import dev.reboot.dto.ai.AiToolCallTrace;

import java.util.List;

/**
 * 通用 Agent 运行结果（Week 12 Day 79，ADR 0026）。
 *
 * @author AI 助手
 * @since 2026-08-29
 */
public record AgentRunResult(
        String answer,
        int toolRounds,
        int toolCalls,
        boolean referencedRealTime,
        boolean truncated,
        List<AiToolCallTrace> toolTrace) {
}
