package xyz.ppmblszdp.ai.workflow;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * 工作流节点定义。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowNode(String id, String name, NodeType type, Map<String, Object> config, Position position) {

    public enum NodeType {
        INPUT,
        LLM,
        TOOL,
        CONDITION,
        PARALLEL,
        OUTPUT
    }

    public enum NodeStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        SKIPPED
    }

    public record Position(double x, double y) {}
}
