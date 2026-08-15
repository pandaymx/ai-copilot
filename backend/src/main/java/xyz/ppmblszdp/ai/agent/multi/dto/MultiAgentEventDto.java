package xyz.ppmblszdp.ai.agent.multi.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 多 Agent 协作 SSE 实时事件帧 DTO。
 *
 * @param eventType   事件类型：plan_created | agent_started | agent_progress | agent_completed | agent_failed | conflict_detected | conflict_waiting_user | synthesis_started | synthesis_chunk | workflow_completed
 * @param planId      方案唯一 ID
 * @param nodeId      相关子代理节点 ID
 * @param role        相关子代理角色
 * @param title       相关子代理或事件标题
 * @param content     增量文本或说明
 * @param plan        最新全局规划快照（可选）
 * @param conflict    冲突项快照（可选）
 * @param durationMs  耗时 (ms)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MultiAgentEventDto(
        String eventType,
        String planId,
        String nodeId,
        String role,
        String title,
        String content,
        MultiAgentPlanDto plan,
        ConflictItemDto conflict,
        Long durationMs) {

    public static MultiAgentEventDto planCreated(String planId, MultiAgentPlanDto plan) {
        return new MultiAgentEventDto("plan_created", planId, null, null, plan.title(), null, plan, null, null);
    }

    public static MultiAgentEventDto agentStarted(String planId, String nodeId, String role, String title) {
        return new MultiAgentEventDto("agent_started", planId, nodeId, role, title, null, null, null, null);
    }

    public static MultiAgentEventDto agentProgress(String planId, String nodeId, String content) {
        return new MultiAgentEventDto("agent_progress", planId, nodeId, null, null, content, null, null, null);
    }

    public static MultiAgentEventDto agentCompleted(
            String planId, String nodeId, String role, String title, String output, long durationMs) {
        return new MultiAgentEventDto("agent_completed", planId, nodeId, role, title, output, null, null, durationMs);
    }

    public static MultiAgentEventDto agentFailed(
            String planId, String nodeId, String role, String title, String errorMessage, long durationMs) {
        return new MultiAgentEventDto(
                "agent_failed", planId, nodeId, role, title, errorMessage, null, null, durationMs);
    }

    public static MultiAgentEventDto conflictDetected(String planId, ConflictItemDto conflict) {
        return new MultiAgentEventDto(
                "conflict_detected", planId, null, null, conflict.topic(), null, null, conflict, null);
    }

    public static MultiAgentEventDto conflictWaitingUser(
            String planId, MultiAgentPlanDto plan, ConflictItemDto conflict) {
        return new MultiAgentEventDto(
                "conflict_waiting_user", planId, null, null, "检测到关键事实分歧，等待用户裁决", null, plan, conflict, null);
    }

    public static MultiAgentEventDto synthesisStarted(String planId) {
        return new MultiAgentEventDto(
                "synthesis_started", planId, "synthesis", "synthesis", "综合代理开始汇总各方结论", null, null, null, null);
    }

    public static MultiAgentEventDto synthesisChunk(String planId, String chunk) {
        return new MultiAgentEventDto(
                "synthesis_chunk", planId, "synthesis", "synthesis", null, chunk, null, null, null);
    }

    public static MultiAgentEventDto workflowCompleted(String planId, MultiAgentPlanDto plan, long totalDurationMs) {
        return new MultiAgentEventDto(
                "workflow_completed", planId, null, null, "多 Agent 协作工作流全部完成", null, plan, null, totalDurationMs);
    }
}
