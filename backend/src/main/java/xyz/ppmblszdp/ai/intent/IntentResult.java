package xyz.ppmblszdp.ai.intent;

/**
 * 意图识别与智能路由决策结果 DTO 记录。
 *
 * @param intent                识别出的意图类型
 * @param label                 意图中文显示标签
 * @param recommendedModel      推荐模型 ID（可选）
 * @param recommendedProvider   推荐供应商 ID（可选）
 * @param recommendationReason  推荐理由（可选）
 * @param enableRag             推荐是否开启 RAG 检索
 * @param enableTools           推荐是否开启 Agent 工具链
 * @param systemPromptTemplate  意图专属系统提示词补充模板（可为空）
 */
public record IntentResult(
        IntentType intent,
        String label,
        String recommendedModel,
        String recommendedProvider,
        String recommendationReason,
        boolean enableRag,
        boolean enableTools,
        String systemPromptTemplate) {
    public IntentResult(IntentType intent, boolean enableRag, boolean enableTools, String systemPromptTemplate) {
        this(
                intent,
                intent != null ? intent.getLabel() : "闲聊",
                null,
                null,
                null,
                enableRag,
                enableTools,
                systemPromptTemplate);
    }
}
