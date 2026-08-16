package xyz.ppmblszdp.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import xyz.ppmblszdp.ai.rag.dto.DocumentCitationDto;

/**
 * 结构化 SSE 传输帧 DTO。
 *
 * <p>所有实例均应通过静态工厂方法构造（如 {@link #content(String)}、{@link #toolCall(String, String, String)}、
 * {@link #artifact(String, String, String, String)} 等），工厂方法内部统一将非相关字段置为 {@code null}，
 * 配合 {@code @JsonInclude(NON_NULL)} 保证序列化输出只携带当前帧关心的字段，避免多参构造传参错位。
 *
 * <p>帧类型 {@code type} 取值（详见 backend/docs/sse-protocol.md）：
 * <ul>
 *   <li>{@code conversation} - 会话建立帧</li>
 *   <li>{@code content}      - 文本增量（delta 追加）</li>
 *   <li>{@code reasoning}    - 推理/思考增量（delta 追加）</li>
 *   <li>{@code tool_call}    - 工具调用意图（单帧快照）</li>
 *   <li>{@code tool_result}  - 工具调用结果（单帧快照）</li>
 *   <li>{@code artifact}     - 可渲染产物（html/svg 等，可流式）</li>
 *   <li>{@code citations}    - 文档对话精准引用列表（单帧快照）</li>
 *   <li>{@code usage}        - Token 用量</li>
 *   <li>{@code error}        - 错误</li>
 *   <li>{@code done}         - 流结束</li>
 * </ul>
 *
 * @param type           帧类型（见上方枚举取值）
 * @param conversationId 会话 ID（仅 conversation 类型有）
 * @param content        增量文本内容（仅 content 类型有）
 * @param reasoning      推理解析/思考过程增量（仅 reasoning 类型有）
 * @param usage          Token 用量及费用信息（仅 usage / done 类型有）
 * @param code           错误代码（仅 error 类型有）
 * @param message        错误信息（仅 error 类型有）
 * @param provider       实际提供方（仅 conversation 类型有，fallback 时可能为派生值）
 * @param model          实际模型（仅 conversation 类型有）
 * @param isFallback     是否降级（仅 conversation 类型有）
 * @param toolName       工具名称（仅 tool_call 类型有）
 * @param toolCallId     工具调用标识，用于 tool_call 与 tool_result 配对（仅 tool_call / tool_result 类型有）
 * @param arguments      工具参数，必须是合法序列化的 JSON Object 字符串（仅 tool_call 类型有）
 * @param result         工具结果，推荐 JSON Object / Array，纯文本需包裹为 {"output":"..."}（仅 tool_result 类型有）
 * @param isError        工具结果是否为错误（仅 tool_result 类型有）
 * @param artifactId     产物唯一 ID（仅 artifact 类型有）
 * @param language       产物语言（如 "html" / "svg" / "markdown"，仅 artifact 类型有）
 * @param artifactType   产物类型（如 "code" / "document" / "chart"，仅 artifact 类型有）
 * @param title          产物标题（可选，仅 artifact 类型有）
 * @param html           产物内容，status=streaming 时为增量片段，status=final 时为完整内容（仅 artifact 类型有）
 * @param status         产物状态：drafting | streaming | final（可选，仅 artifact 类型有）
 * @param citations      精准引用列表（仅 citations 类型有）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatChunkDto(
        String type,
        String conversationId,
        String content,
        String reasoning,
        UsageDto usage,
        String code,
        String message,
        String provider,
        String model,
        Boolean isFallback,
        String toolName,
        String toolCallId,
        String arguments,
        String result,
        Boolean isError,
        String artifactId,
        String language,
        String artifactType,
        String title,
        String html,
        String status,
        String mimeType,
        String intent,
        String intentLabel,
        List<DocumentCitationDto> citations,
        StreamMetricsDto metrics,
        InteractionMetadataDto interaction,
        ModelRecommendationDto recommendation) {

    /** 兼容 27 参数旧构造函数（无 recommendation） */
    public ChatChunkDto(
            String type,
            String conversationId,
            String content,
            String reasoning,
            UsageDto usage,
            String code,
            String message,
            String provider,
            String model,
            Boolean isFallback,
            String toolName,
            String toolCallId,
            String arguments,
            String result,
            Boolean isError,
            String artifactId,
            String language,
            String artifactType,
            String title,
            String html,
            String status,
            String mimeType,
            String intent,
            String intentLabel,
            List<DocumentCitationDto> citations,
            StreamMetricsDto metrics) {
        this(
                type,
                conversationId,
                content,
                reasoning,
                usage,
                code,
                message,
                provider,
                model,
                isFallback,
                toolName,
                toolCallId,
                arguments,
                result,
                isError,
                artifactId,
                language,
                artifactType,
                title,
                html,
                status,
                mimeType,
                intent,
                intentLabel,
                citations,
                metrics,
                null,
                null);
    }

    /** 兼容 25 参数旧构造函数 */
    public ChatChunkDto(
            String type,
            String conversationId,
            String content,
            String reasoning,
            UsageDto usage,
            String code,
            String message,
            String provider,
            String model,
            Boolean isFallback,
            String toolName,
            String toolCallId,
            String arguments,
            String result,
            Boolean isError,
            String artifactId,
            String language,
            String artifactType,
            String title,
            String html,
            String status,
            String mimeType,
            String intent,
            String intentLabel,
            List<DocumentCitationDto> citations) {
        this(
                type,
                conversationId,
                content,
                reasoning,
                usage,
                code,
                message,
                provider,
                model,
                isFallback,
                toolName,
                toolCallId,
                arguments,
                result,
                isError,
                artifactId,
                language,
                artifactType,
                title,
                html,
                status,
                mimeType,
                intent,
                intentLabel,
                citations,
                null,
                null,
                null);
    }

    /** 兼容 24 参数旧构造函数 */
    public ChatChunkDto(
            String type,
            String conversationId,
            String content,
            String reasoning,
            UsageDto usage,
            String code,
            String message,
            String provider,
            String model,
            Boolean isFallback,
            String toolName,
            String toolCallId,
            String arguments,
            String result,
            Boolean isError,
            String artifactId,
            String language,
            String artifactType,
            String title,
            String html,
            String status,
            String mimeType,
            String intent,
            String intentLabel) {
        this(
                type,
                conversationId,
                content,
                reasoning,
                usage,
                code,
                message,
                provider,
                model,
                isFallback,
                toolName,
                toolCallId,
                arguments,
                result,
                isError,
                artifactId,
                language,
                artifactType,
                title,
                html,
                status,
                mimeType,
                intent,
                intentLabel,
                null,
                null,
                null,
                null);
    }

    /** 兼容 7 参数最简构造函数 */
    public ChatChunkDto(
            String type,
            String conversationId,
            String content,
            String reasoning,
            UsageDto usage,
            String code,
            String message) {
        this(
                type,
                conversationId,
                content,
                reasoning,
                usage,
                code,
                message,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public record UsageDto(
            int promptTokens,
            int completionTokens,
            int totalTokens,
            Double estimatedCostRmb,
            Long monthlyUsed,
            Long monthlyQuota,
            Double monthlyPercent) {
        public UsageDto(int promptTokens, int completionTokens, int totalTokens, Double estimatedCostRmb) {
            this(promptTokens, completionTokens, totalTokens, estimatedCostRmb, null, null, null);
        }
    }

    public record StreamMetricsDto(
            Long timeToFirstToken,
            Double tokensPerSecond,
            Long totalDuration,
            Long toolCallDuration,
            Boolean isEstimated) {
        public StreamMetricsDto(
                Long timeToFirstToken, Double tokensPerSecond, Long totalDuration, Long toolCallDuration) {
            this(timeToFirstToken, tokensPerSecond, totalDuration, toolCallDuration, false);
        }
    }

    public record InteractionMetadataDto(
            String state, String stateLabel, List<String> signals, List<String> strategies) {}

    public record ModelRecommendationDto(
            String providerId, String modelId, String displayName, String reason, Double estimatedCostRmb) {}

    public static ChatChunkDto conversation(String conversationId) {
        return new ChatChunkDto(
                "conversation",
                conversationId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public static ChatChunkDto conversation(String conversationId, String provider, String model, Boolean isFallback) {
        return new ChatChunkDto(
                "conversation",
                conversationId,
                null,
                null,
                null,
                null,
                null,
                provider,
                model,
                isFallback,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public static ChatChunkDto conversation(
            String conversationId,
            String provider,
            String model,
            Boolean isFallback,
            String intent,
            String intentLabel) {
        return conversation(conversationId, provider, model, isFallback, intent, intentLabel, null, null);
    }

    public static ChatChunkDto conversation(
            String conversationId,
            String provider,
            String model,
            Boolean isFallback,
            String intent,
            String intentLabel,
            InteractionMetadataDto interaction) {
        return conversation(conversationId, provider, model, isFallback, intent, intentLabel, interaction, null);
    }

    public static ChatChunkDto conversation(
            String conversationId,
            String provider,
            String model,
            Boolean isFallback,
            String intent,
            String intentLabel,
            InteractionMetadataDto interaction,
            ModelRecommendationDto recommendation) {
        return new ChatChunkDto(
                "conversation",
                conversationId,
                null,
                null,
                null,
                null,
                null,
                provider,
                model,
                isFallback,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                intent,
                intentLabel,
                null,
                null,
                interaction,
                recommendation);
    }

    public static ChatChunkDto content(String content) {
        return new ChatChunkDto("content", null, content, null, null, null, null);
    }

    public static ChatChunkDto reasoning(String reasoning) {
        return new ChatChunkDto("reasoning", null, null, reasoning, null, null, null);
    }

    public static ChatChunkDto usage(UsageDto usage) {
        return new ChatChunkDto("usage", null, null, null, usage, null, null);
    }

    public static ChatChunkDto error(String code, String message) {
        return new ChatChunkDto("error", null, null, null, null, code, message);
    }

    public static ChatChunkDto done() {
        return new ChatChunkDto("done", null, null, null, null, null, null);
    }

    public static ChatChunkDto done(UsageDto usage) {
        return new ChatChunkDto("done", null, null, null, usage, null, null);
    }

    /**
     * 工具调用意图帧（单帧快照）。
     *
     * @param toolCallId 调用标识，需与对应 tool_result 帧的 toolCallId 配对
     * @param toolName   工具名称
     * @param arguments  参数，必须是合法序列化的 JSON Object 字符串，例如 "{\"location\":\"Beijing\"}"
     */
    public static ChatChunkDto toolCall(String toolCallId, String toolName, String arguments) {
        return new ChatChunkDto(
                "tool_call",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                toolName,
                toolCallId,
                arguments,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /**
     * 工具调用结果帧（单帧快照）。
     *
     * @param toolCallId 调用标识，需与对应 tool_call 帧配对
     * @param toolName   工具名称（可选，便于前端展示）
     * @param result     结果，推荐 JSON Object / Array；纯文本/数字需包裹为 {"output":"..."}
     * @param isError    结果是否为错误
     */
    public static ChatChunkDto toolResult(String toolCallId, String toolName, String result, Boolean isError) {
        return new ChatChunkDto(
                "tool_result",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                toolName,
                toolCallId,
                null,
                result,
                isError,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /**
     * 可渲染产物帧（可流式）。当 status="streaming" 时 html 为增量片段，status="final" 时为完整内容。
     *
     * @param artifactId   产物唯一 ID
     * @param language     产物语言（如 "html" / "svg" / "markdown"）
     * @param artifactType 产物类型（如 "code" / "document" / "chart"）
     * @param html         产物内容
     */
    public static ChatChunkDto artifact(String artifactId, String language, String artifactType, String html) {
        return new ChatChunkDto(
                "artifact",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                artifactId,
                language,
                artifactType,
                null,
                html,
                null,
                null,
                null,
                null);
    }

    /**
     * 可渲染产物帧（带标题与状态，用于流式场景）。
     *
     * @param artifactId   产物唯一 ID
     * @param language     产物语言
     * @param artifactType 产物类型
     * @param title        产物标题（可选）
     * @param html         产物内容（streaming 时为增量片段，final 时为完整内容）
     * @param status       产物状态：drafting | streaming | final（可选）
     */
    public static ChatChunkDto artifact(
            String artifactId, String language, String artifactType, String title, String html, String status) {
        return new ChatChunkDto(
                "artifact",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                artifactId,
                language,
                artifactType,
                title,
                html,
                status,
                null,
                null,
                null);
    }

    /**
     * 可渲染产物帧（带标题、状态、MIME 类型）。
     */
    public static ChatChunkDto artifact(
            String artifactId,
            String language,
            String artifactType,
            String title,
            String html,
            String status,
            String mimeType) {
        return new ChatChunkDto(
                "artifact",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                artifactId,
                language,
                artifactType,
                title,
                html,
                status,
                mimeType,
                null,
                null);
    }

    /**
     * 任务规划总览帧：推送当前 TaskPlan 结构 JSON。
     */
    public static ChatChunkDto taskPlan(String planJson) {
        return new ChatChunkDto(
                "task_plan",
                null,
                planJson,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /**
     * 任务步骤执行与状态更新帧：推送 TaskStep 结构 JSON。
     */
    public static ChatChunkDto taskStep(String stepJson) {
        return new ChatChunkDto(
                "task_step",
                null,
                stepJson,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /**
     * 上下文压缩元数据帧：推送 ContextCompression 结构 JSON。
     */
    public static ChatChunkDto contextCompression(String compressionJson) {
        return new ChatChunkDto(
                "context_compression",
                null,
                compressionJson,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /**
     * 文档对话精准引用列表帧：推送 citations 列表。
     */
    public static ChatChunkDto citations(List<DocumentCitationDto> citations) {
        return new ChatChunkDto(
                "citations",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                citations);
    }

    /**
     * 实时流式性能指标帧（单帧快照，通常在内容生成完毕后、done 帧之前下发）。
     */
    public static ChatChunkDto metrics(StreamMetricsDto metrics) {
        return new ChatChunkDto(
                "metrics", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, metrics);
    }

    public static ChatChunkDto metrics(
            Long timeToFirstToken, Double tokensPerSecond, Long totalDuration, Long toolCallDuration) {
        return metrics(new StreamMetricsDto(timeToFirstToken, tokensPerSecond, totalDuration, toolCallDuration, false));
    }

    public static ChatChunkDto metrics(
            Long timeToFirstToken,
            Double tokensPerSecond,
            Long totalDuration,
            Long toolCallDuration,
            Boolean isEstimated) {
        return metrics(
                new StreamMetricsDto(timeToFirstToken, tokensPerSecond, totalDuration, toolCallDuration, isEstimated));
    }

    /**
     * 智能模型推荐帧（单帧快照，通常在会话建立后下发推荐模型信息）。
     */
    public static ChatChunkDto recommendation(ModelRecommendationDto recommendation) {
        return new ChatChunkDto(
                "recommendation",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                recommendation);
    }
}
