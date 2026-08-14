package xyz.ppmblszdp.ai.workflow;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * 工作流模板与实例定义。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowDefinition(
        String id,
        String name,
        String description,
        String icon,
        String version,
        List<InputField> inputSchema,
        List<WorkflowNode> nodes,
        List<WorkflowEdge> edges,
        Map<String, Object> defaultInputs,
        Long createdAt,
        Long updatedAt) {

    public record InputField(
            String key,
            String label,
            String type, // "string" | "text" | "number" | "boolean" | "select"
            String defaultValue,
            String placeholder,
            List<String> options,
            boolean required) {}
}
