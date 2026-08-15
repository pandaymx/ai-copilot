package xyz.ppmblszdp.ai.agent.multi.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 非流式多 Agent 协作聚合响应 DTO。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MultiAgentResponse(
        String planId,
        String status,
        MultiAgentPlanDto plan,
        String synthesisResult,
        List<ConflictItemDto> conflicts,
        Long totalDurationMs) {}
