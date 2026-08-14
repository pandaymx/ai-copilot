package xyz.ppmblszdp.ai.workflow;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * 工作流 SSE 流式事件帧 DTO。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowEvent(
        String type, // workflow_started | node_started | node_chunk | node_finished | node_skipped | node_failed |
        // workflow_completed | workflow_failed
        String executionId,
        String workflowId,
        String nodeId,
        String nodeName,
        String nodeType,
        String delta,
        Object output,
        String error,
        String skipReason,
        Long durationMs,
        Integer tokenUsage,
        Map<String, Object> finalOutputs) {

    public static WorkflowEvent workflowStarted(String executionId, String workflowId) {
        return new WorkflowEvent(
                "workflow_started",
                executionId,
                workflowId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public static WorkflowEvent nodeStarted(
            String executionId, String workflowId, String nodeId, String nodeName, String nodeType) {
        return new WorkflowEvent(
                "node_started",
                executionId,
                workflowId,
                nodeId,
                nodeName,
                nodeType,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public static WorkflowEvent nodeChunk(String executionId, String workflowId, String nodeId, String delta) {
        return new WorkflowEvent(
                "node_chunk", executionId, workflowId, nodeId, null, null, delta, null, null, null, null, null, null);
    }

    public static WorkflowEvent nodeFinished(
            String executionId, String workflowId, String nodeId, Object output, long durationMs, Integer tokenUsage) {
        return new WorkflowEvent(
                "node_finished",
                executionId,
                workflowId,
                nodeId,
                null,
                null,
                null,
                output,
                null,
                null,
                durationMs,
                tokenUsage,
                null);
    }

    public static WorkflowEvent nodeSkipped(String executionId, String workflowId, String nodeId, String reason) {
        return new WorkflowEvent(
                "node_skipped", executionId, workflowId, nodeId, null, null, null, null, null, reason, 0L, 0, null);
    }

    public static WorkflowEvent nodeFailed(
            String executionId, String workflowId, String nodeId, String error, long durationMs) {
        return new WorkflowEvent(
                "node_failed",
                executionId,
                workflowId,
                nodeId,
                null,
                null,
                null,
                null,
                error,
                null,
                durationMs,
                0,
                null);
    }

    public static WorkflowEvent workflowCompleted(
            String executionId, String workflowId, Map<String, Object> finalOutputs, long durationMs, int totalTokens) {
        return new WorkflowEvent(
                "workflow_completed",
                executionId,
                workflowId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                durationMs,
                totalTokens,
                finalOutputs);
    }

    public static WorkflowEvent workflowFailed(String executionId, String workflowId, String error, long durationMs) {
        return new WorkflowEvent(
                "workflow_failed",
                executionId,
                workflowId,
                null,
                null,
                null,
                null,
                null,
                error,
                null,
                durationMs,
                0,
                null);
    }
}
