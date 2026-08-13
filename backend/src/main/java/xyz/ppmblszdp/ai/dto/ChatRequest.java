package xyz.ppmblszdp.ai.dto;

import java.util.List;

/**
 * 聊天请求体。向后兼容前端现有 {@code {message, history}} 契约，并增量支持记忆驱动。
 *
 * <ul>
 *   <li>{@code message} 必填；</li>
 *   <li>{@code history} 可选；当无 conversationId 时走旧 ContextAssembler 路径（完全向后兼容）；</li>
 *   <li>{@code conversationId} 可选；有值时走记忆驱动路径（忽略/仅容错 history），实现多轮对话；</li>
 *   <li>{@code userId} 可选；长期记忆（pgvector）按 userId 维度隔离跨会话个性化；</li>
 *   <li>{@code provider} / {@code model} 可选，缺省回落全局默认；</li>
 *   <li>{@code systemPrompt} 可选，用于本次请求覆盖系统提示词。</li>
 * </ul>
 *
 * @param message       当前用户输入（必填）
 * @param history       历史对话
 * @param provider      供应商 id（可选）
 * @param model         模型 id（可选）
 * @param systemPrompt  本次请求覆盖的系统提示词（可选）
 * @param conversationId 会话 id（可选）；有值时走记忆驱动路径
 * @param userId        用户 id（可选）；长期记忆隔离维度
 * @param agentEnabled  Agent 模式开关（可选）；为 true 时装配工具调用并推送 tool_call/tool_result 帧
 */
public record ChatRequest(
        String message,
        List<ChatMessageDto> history,
        String provider,
        String model,
        String systemPrompt,
        String conversationId,
        String userId,
        List<MediaDto> media,
        Boolean agentEnabled) {
    public List<MediaDto> media() {
        return media == null ? List.of() : media;
    }

    public List<ChatMessageDto> history() {
        return history == null ? List.of() : history;
    }

    public String message() {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message 不能为空");
        }
        return message;
    }

    /** 是否走记忆驱动路径：有 conversationId 即启用（依赖 app.ai.memory.enabled 总开关）。 */
    public boolean hasConversation() {
        return conversationId != null && !conversationId.isBlank();
    }

    /** 返回一个带指定 conversationId 的副本（record 不可变，用于后端生成后回填）。 */
    public ChatRequest withConversationId(String id) {
        return new ChatRequest(message, history, provider, model, systemPrompt, id, userId, media, agentEnabled);
    }

    /**
     * 解析当前用户 id；空则回落匿名标识。
     *
     * @deprecated 服务端身份已从受信任 {@code X-User-Id} Header 解析（见 {@code UserIdentityFilter}）。
     * 该方法仅保留作为 dev 模式 fallback，生产环境不应信任请求体中的 userId。
     */
    @Deprecated(since = "auth-refactor", forRemoval = false)
    public String resolveUserId() {
        return (userId != null && !userId.isBlank()) ? userId : "anonymous";
    }
}
