package xyz.ppmblszdp.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * API Key 验证测试响应 DTO。
 *
 * @param valid   Key 是否有效且可连通
 * @param status  最新状态（ACTIVE 或 INVALID）
 * @param message 结果详情或错误说明
 * @param balance 余额信息（若支持）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiKeyTestResultDto(boolean valid, String status, String message, String balance) {}
