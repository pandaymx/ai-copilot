package xyz.ppmblszdp.ai.compare.dto;

import java.util.List;

/**
 * 多模型对比请求体。
 *
 * @param prompt 对比提示词（必填）
 * @param models 参与对比的模型目标列表（2~3 个）
 * @param systemPrompt 可选系统提示词
 * @param temperature 可选温度参数 (0.0~2.0)
 * @param conversationId 可选关联会话 ID（若提供，将自动载入历史上下文增强对比）
 */
public record CompareRequest(
        String prompt, List<ModelTarget> models, String systemPrompt, Double temperature, String conversationId) {

    public record ModelTarget(String provider, String model) {}
}
