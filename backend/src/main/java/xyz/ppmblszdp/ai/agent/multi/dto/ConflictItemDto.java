package xyz.ppmblszdp.ai.agent.multi.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 子代理之间检测到的事实冲突或意见分歧项 DTO。
 *
 * @param conflictId       冲突唯一标识
 * @param topic            争议主题（如"高并发吞吐量结论矛盾"）
 * @param agentA           子代理 A 信息与观点（如 "task_1 (Spring Boot 3): QPS 达 45,000"）
 * @param agentB           子代理 B 信息与观点（如 "task_2 (Quarkus): QPS 达 52,000"）
 * @param description      冲突详细分歧说明
 * @param resolutionStatus 裁决状态：UNRESOLVED | RESOLVED_BY_USER | RESOLVED_BY_SYNTHESIS
 * @param userDecision     用户裁决选定的倾向或补充说明
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConflictItemDto(
        String conflictId,
        String topic,
        String agentA,
        String agentB,
        String description,
        String resolutionStatus,
        String userDecision) {

    public static ConflictItemDto of(
            String conflictId, String topic, String agentA, String agentB, String description) {
        return new ConflictItemDto(conflictId, topic, agentA, agentB, description, "UNRESOLVED", null);
    }

    public ConflictItemDto withResolution(String resolutionStatus, String userDecision) {
        return new ConflictItemDto(
                this.conflictId,
                this.topic,
                this.agentA,
                this.agentB,
                this.description,
                resolutionStatus != null ? resolutionStatus : this.resolutionStatus,
                userDecision != null ? userDecision : this.userDecision);
    }
}
