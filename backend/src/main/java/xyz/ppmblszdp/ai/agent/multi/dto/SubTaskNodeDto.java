package xyz.ppmblszdp.ai.agent.multi.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * 多 Agent 协作中的单个子任务节点 DTO。
 *
 * @param id           节点唯一标识（如 "task_1", "task_2", "synthesis"）
 * @param role         角色标识（research | code | analysis | review | synthesis）
 * @param title        子任务标题
 * @param description  子任务详细目标与输入要求
 * @param dependencies 前置依赖节点 ID 列表（只有所有前置节点 COMPLETED 后本节点才可执行）
 * @param status       节点状态：PENDING | RUNNING | COMPLETED | FAILED | SKIPPED
 * @param output       子代理最终输出结论
 * @param errorMessage 失败时的异常信息
 * @param startedAtMs  开始执行时间戳 (ms)
 * @param completedAtMs 完成时间戳 (ms)
 * @param durationMs   执行耗时 (ms)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SubTaskNodeDto(
        String id,
        String role,
        String title,
        String description,
        List<String> dependencies,
        String status,
        String output,
        String errorMessage,
        Long startedAtMs,
        Long completedAtMs,
        Long durationMs) {

    public SubTaskNodeDto {
        if (dependencies == null) {
            dependencies = new ArrayList<>();
        }
    }

    public static SubTaskNodeDto of(
            String id, String role, String title, String description, List<String> dependencies) {
        return new SubTaskNodeDto(
                id,
                role != null ? role : "analysis",
                title != null ? title : id,
                description != null ? description : "",
                dependencies != null ? new ArrayList<>(dependencies) : new ArrayList<>(),
                "PENDING",
                null,
                null,
                null,
                null,
                null);
    }

    public SubTaskNodeDto withRunning(long startedAtMs) {
        return new SubTaskNodeDto(
                this.id,
                this.role,
                this.title,
                this.description,
                this.dependencies,
                "RUNNING",
                this.output,
                null,
                startedAtMs,
                null,
                null);
    }

    public SubTaskNodeDto withCompleted(String output, long completedAtMs) {
        long start = this.startedAtMs != null ? this.startedAtMs : completedAtMs;
        return new SubTaskNodeDto(
                this.id,
                this.role,
                this.title,
                this.description,
                this.dependencies,
                "COMPLETED",
                output,
                null,
                this.startedAtMs,
                completedAtMs,
                Math.max(0, completedAtMs - start));
    }

    public SubTaskNodeDto withFailed(String errorMessage, long completedAtMs) {
        long start = this.startedAtMs != null ? this.startedAtMs : completedAtMs;
        return new SubTaskNodeDto(
                this.id,
                this.role,
                this.title,
                this.description,
                this.dependencies,
                "FAILED",
                this.output,
                errorMessage,
                this.startedAtMs,
                completedAtMs,
                Math.max(0, completedAtMs - start));
    }
}
