package xyz.ppmblszdp.ai.agent.multi.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * 多 Agent 协作全局执行方案 DTO。
 *
 * @param planId          计划唯一 ID
 * @param goal            用户原始目标
 * @param title           计划标题
 * @param status          状态：PLANNING | EXECUTING | WAITING_USER | COMPLETED | FAILED
 * @param nodes           DAG 任务节点列表
 * @param conflicts       检测到的冲突分歧项列表
 * @param synthesisResult 最终综合汇总报告
 * @param createdAtMs     创建时间戳 (ms)
 * @param updatedAtMs     最后更新时间戳 (ms)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MultiAgentPlanDto(
        String planId,
        String goal,
        String title,
        String status,
        List<SubTaskNodeDto> nodes,
        List<ConflictItemDto> conflicts,
        String synthesisResult,
        Long createdAtMs,
        Long updatedAtMs) {

    public MultiAgentPlanDto {
        if (nodes == null) {
            nodes = new ArrayList<>();
        }
        if (conflicts == null) {
            conflicts = new ArrayList<>();
        }
    }

    public static MultiAgentPlanDto create(String planId, String goal, String title, List<SubTaskNodeDto> nodes) {
        long now = System.currentTimeMillis();
        return new MultiAgentPlanDto(
                planId,
                goal,
                title != null ? title : "多 Agent 协作分析计划",
                "PLANNING",
                nodes != null ? new ArrayList<>(nodes) : new ArrayList<>(),
                new ArrayList<>(),
                null,
                now,
                now);
    }

    public MultiAgentPlanDto withStatus(String status) {
        return new MultiAgentPlanDto(
                this.planId,
                this.goal,
                this.title,
                status,
                this.nodes,
                this.conflicts,
                this.synthesisResult,
                this.createdAtMs,
                System.currentTimeMillis());
    }

    public MultiAgentPlanDto withNodes(List<SubTaskNodeDto> nodes) {
        return new MultiAgentPlanDto(
                this.planId,
                this.goal,
                this.title,
                this.status,
                nodes != null ? new ArrayList<>(nodes) : new ArrayList<>(),
                this.conflicts,
                this.synthesisResult,
                this.createdAtMs,
                System.currentTimeMillis());
    }

    public MultiAgentPlanDto withConflicts(List<ConflictItemDto> conflicts) {
        return new MultiAgentPlanDto(
                this.planId,
                this.goal,
                this.title,
                this.status,
                this.nodes,
                conflicts != null ? new ArrayList<>(conflicts) : new ArrayList<>(),
                this.synthesisResult,
                this.createdAtMs,
                System.currentTimeMillis());
    }

    public MultiAgentPlanDto withSynthesis(String synthesisResult) {
        return new MultiAgentPlanDto(
                this.planId,
                this.goal,
                this.title,
                "COMPLETED",
                this.nodes,
                this.conflicts,
                synthesisResult,
                this.createdAtMs,
                System.currentTimeMillis());
    }
}
