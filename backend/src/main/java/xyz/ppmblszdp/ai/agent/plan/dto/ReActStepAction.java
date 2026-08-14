package xyz.ppmblszdp.ai.agent.plan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * ReAct 循环中单步推理输出动作。
 *
 * @param thought     思考逻辑（Reasoning）
 * @param actionType  动作类型：TOOL_CALL | FINISH | REPLAN | SKIP
 * @param toolName    要调用的工具名称（如 git_status、code_search_regex 等）
 * @param toolArgs    工具入参 JSON 字符串
 * @param explanation 决策说明或最终完成总结
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReActStepAction(
        String thought,
        String actionType,
        String toolName,
        String toolArgs,
        String explanation) {

    public static ReActStepAction toolCall(String thought, String toolName, String toolArgs) {
        return new ReActStepAction(thought, "TOOL_CALL", toolName, toolArgs, null);
    }

    public static ReActStepAction finish(String thought, String explanation) {
        return new ReActStepAction(thought, "FINISH", null, null, explanation);
    }

    public static ReActStepAction replan(String thought, String explanation) {
        return new ReActStepAction(thought, "REPLAN", null, null, explanation);
    }

    public static ReActStepAction skip(String thought, String explanation) {
        return new ReActStepAction(thought, "SKIP", null, null, explanation);
    }
}
