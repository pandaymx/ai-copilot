package xyz.ppmblszdp.ai.agent.plan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 任务规划单个步骤 DTO。
 *
 * @param stepId         步骤序号 (1, 2, 3...)
 * @param title          步骤简短标题（如"检出目标分支"）
 * @param description    步骤详细操作描述
 * @param toolName       预期使用的工具名称（如 git_status、code_search_regex 等，可选）
 * @param expectedOutput 预期产出目标
 * @param thought        本步执行前的 ReAct Thought 思考逻辑
 * @param actionArgs     实际调用的工具入参 JSON
 * @param observation    工具返回的观察结果（Raw 或 Pruned）
 * @param status         状态：PENDING | RUNNING | COMPLETED | FAILED | REPLANNING | SKIPPED
 * @param replanCount    该步骤已触发的重规划/重试次数
 * @param errorMessage   若执行失败的错误原因
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskStepDto(
        Integer stepId,
        String title,
        String description,
        String toolName,
        String expectedOutput,
        String thought,
        String actionArgs,
        String observation,
        String status,
        Integer replanCount,
        String errorMessage) {

    public static TaskStepDto pending(int stepId, String title, String description, String toolName, String expectedOutput) {
        return new TaskStepDto(
                stepId,
                title,
                description,
                toolName,
                expectedOutput,
                null,
                null,
                null,
                "PENDING",
                0,
                null);
    }

    public TaskStepDto withStatus(String status) {
        return new TaskStepDto(
                this.stepId,
                this.title,
                this.description,
                this.toolName,
                this.expectedOutput,
                this.thought,
                this.actionArgs,
                this.observation,
                status,
                this.replanCount,
                this.errorMessage);
    }

    public TaskStepDto withExecution(String thought, String toolName, String actionArgs) {
        return new TaskStepDto(
                this.stepId,
                this.title,
                this.description,
                toolName != null ? toolName : this.toolName,
                this.expectedOutput,
                thought,
                actionArgs,
                this.observation,
                "RUNNING",
                this.replanCount,
                null);
    }

    public TaskStepDto withObservation(String observation, boolean success, String errorMessage) {
        return new TaskStepDto(
                this.stepId,
                this.title,
                this.description,
                this.toolName,
                this.expectedOutput,
                this.thought,
                this.actionArgs,
                observation,
                success ? "COMPLETED" : "FAILED",
                this.replanCount,
                errorMessage);
    }

    public TaskStepDto incrementReplan(String newDescription, String newToolName) {
        return new TaskStepDto(
                this.stepId,
                this.title,
                newDescription != null ? newDescription : this.description,
                newToolName != null ? newToolName : this.toolName,
                this.expectedOutput,
                null,
                null,
                null,
                "REPLANNING",
                (this.replanCount != null ? this.replanCount : 0) + 1,
                null);
    }
}
