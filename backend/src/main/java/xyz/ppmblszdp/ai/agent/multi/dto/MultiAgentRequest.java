package xyz.ppmblszdp.ai.agent.multi.dto;

import java.util.List;

/**
 * 发起多 Agent 协作请求 DTO。
 *
 * @param goal                           用户目标描述（如"对比 Quarkus, Spring Boot 3 与 Micronaut 的性能与开发体验"）
 * @param provider                       指定的 LLM 提供商（可选）
 * @param model                          指定的模型（可选）
 * @param roles                          建议参与的子代理角色列表（可选，如 ["research", "analysis", "synthesis"]）
 * @param maxParallelAgents              最大并发子代理数（默认 4）
 * @param interactiveConflictResolution  是否开启冲突时人工裁决挂起（默认 false，开启时遇到冲突进入 WAITING_USER 状态等待用户 POST 裁决）
 * @param conversationId                 关联的会话 ID
 */
public record MultiAgentRequest(
        String goal,
        String provider,
        String model,
        List<String> roles,
        Integer maxParallelAgents,
        Boolean interactiveConflictResolution,
        String conversationId) {

    public MultiAgentRequest {
        if (maxParallelAgents == null || maxParallelAgents < 1) {
            maxParallelAgents = 4;
        }
        if (interactiveConflictResolution == null) {
            interactiveConflictResolution = false;
        }
    }
}
