package xyz.ppmblszdp.ai.agent.plan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * 结构化多步任务规划 DTO。
 *
 * @param planId      计划唯一 ID
 * @param title       计划简述标题
 * @param goal        用户原始任务目标
 * @param status      计划全局状态：PLANNING | EXECUTING | COMPLETED | FAILED | REPLANNING | CANCELLED
 * @param currentStep 当前正在执行的步骤序号 (1-indexed)
 * @param totalSteps  总步骤数
 * @param steps       步骤列表
 * @param summary     最终执行完成或阶段性总结
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskPlanDto(
        String planId,
        String title,
        String goal,
        String status,
        Integer currentStep,
        Integer totalSteps,
        List<TaskStepDto> steps,
        String summary) {

    public TaskPlanDto {
        if (steps == null) {
            steps = new ArrayList<>();
        }
    }

    public static TaskPlanDto of(String planId, String title, String goal, List<TaskStepDto> steps) {
        return new TaskPlanDto(
                planId,
                title,
                goal,
                "PLANNING",
                1,
                steps != null ? steps.size() : 0,
                steps != null ? new ArrayList<>(steps) : new ArrayList<>(),
                null);
    }

    public TaskPlanDto withStatus(String newStatus) {
        return new TaskPlanDto(
                this.planId,
                this.title,
                this.goal,
                newStatus,
                this.currentStep,
                this.totalSteps,
                this.steps,
                this.summary);
    }

    public TaskPlanDto withCurrentStep(Integer currentStep) {
        return new TaskPlanDto(
                this.planId,
                this.title,
                this.goal,
                this.status,
                currentStep,
                this.totalSteps,
                this.steps,
                this.summary);
    }

    public TaskPlanDto withSteps(List<TaskStepDto> steps) {
        return new TaskPlanDto(
                this.planId,
                this.title,
                this.goal,
                this.status,
                this.currentStep,
                steps != null ? steps.size() : 0,
                steps != null ? new ArrayList<>(steps) : new ArrayList<>(),
                this.summary);
    }

    public TaskPlanDto withSummary(String summary) {
        return new TaskPlanDto(
                this.planId,
                this.title,
                this.goal,
                this.status,
                this.currentStep,
                this.totalSteps,
                this.steps,
                summary);
    }
}
