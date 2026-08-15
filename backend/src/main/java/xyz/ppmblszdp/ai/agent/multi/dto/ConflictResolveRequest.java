package xyz.ppmblszdp.ai.agent.multi.dto;

/**
 * 用户提交冲突裁决请求 DTO。
 *
 * @param planId      方案 ID
 * @param conflictId  冲突 ID
 * @param decision    选定裁决选项或立场说明（如 "采用 task_1 的吞吐量结论，以真实压测数据为准"）
 * @param notes       额外补充备忘
 */
public record ConflictResolveRequest(String planId, String conflictId, String decision, String notes) {}
