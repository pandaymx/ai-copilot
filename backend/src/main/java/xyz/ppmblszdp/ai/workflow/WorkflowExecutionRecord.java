package xyz.ppmblszdp.ai.workflow;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * 工作流单次执行记录与快照。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowExecutionRecord(
        String executionId,
        String workflowId,
        String workflowName,
        String status, // RUNNING | COMPLETED | FAILED | CANCELLED
        Long startTime,
        Long endTime,
        Long durationMs,
        Integer totalTokens,
        Map<String, Object> inputs,
        Map<String, Object> outputs,
        String error,
        Map<String, NodeExecutionSnapshot> nodeSnapshots) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NodeExecutionSnapshot(
            String nodeId,
            String nodeName,
            WorkflowNode.NodeType nodeType,
            WorkflowNode.NodeStatus status,
            Object inputState,
            Object outputState,
            String error,
            String skipReason,
            Long durationMs,
            Integer tokenUsage) {}
}
