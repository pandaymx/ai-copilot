package xyz.ppmblszdp.ai.workflow;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 工作流连线（边）定义。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowEdge(
        String id,
        String sourceNodeId,
        String targetNodeId,
        String sourceHandle, // e.g. "true" | "false" | "default" | "out"
        String targetHandle, // e.g. "in"
        String label) {}
