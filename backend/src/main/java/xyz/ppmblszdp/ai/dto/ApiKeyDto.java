package xyz.ppmblszdp.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * API Key 展示 DTO（脱敏传输）。
 *
 * @param id           主键 ID
 * @param userId       所属用户 ID
 * @param provider     供应商标识（如 openai, deepseek, anthropic, google, qwen 等）
 * @param maskedKey    脱敏 Key（例如 "sk-proj-****abcd"）
 * @param status       状态：ACTIVE (可用), INVALID (失效/测试未通过), UNTESTED (未验证)
 * @param balance      余额信息（如支持则展示 "$12.50" 或 "¥50.00"，否则为空）
 * @param errorMessage 错误信息（校验失败原因）
 * @param createdAt    创建时间戳（毫秒）
 * @param updatedAt    更新时间戳（毫秒）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiKeyDto(
        String id,
        String userId,
        String provider,
        String maskedKey,
        String status,
        String balance,
        String errorMessage,
        long createdAt,
        long updatedAt) {}
