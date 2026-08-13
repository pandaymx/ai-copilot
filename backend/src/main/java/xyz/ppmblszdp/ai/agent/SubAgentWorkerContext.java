package xyz.ppmblszdp.ai.agent;

import reactor.core.publisher.Sinks.Many;
import xyz.ppmblszdp.ai.dto.ChatChunkDto;

/**
 * Worker 执行上下文：将 SSE Sink、用户身份、会话 ID 以及嵌套深度计数器轻量打包，
 * 通过 {@link org.springframework.ai.chat.model.ToolContext} 在 Orchestrator 工具调用链中传递给
 * {@link SubAgentTool}，从而避免在 {@code @Tool} 方法签名里直接携带非序列化字段。
 *
 * <h2>Depth 保护</h2>
 * <ul>
 *   <li>Orchestrator 层 depth = 0（由 ChatService 构造时传入）；</li>
 *   <li>每向下派发一层 Worker 时，{@link #incrementDepth()} 产生 depth+1 的新实例；</li>
 *   <li>{@link WorkerAgentExecutor} 入口强制校验 {@code depth > maxDepth} 时抛出异常，
 *       被 {@link xyz.ppmblszdp.ai.tool.ToolEventEmitter#executeWithEvent} 捕获为 {@code isError=true}
 *       的 tool_result 帧，不会中断主 SSE 流。</li>
 * </ul>
 *
 * <h2>线程安全</h2>
 * {@code eventSink} 使用 Reactor {@code multicast().onBackpressureBuffer()} 模式，
 * 多线程并发 {@code tryEmitNext} 是安全的（已由 ToolEventEmitter 保证）。
 * 该 record 本身不可变，无状态共享安全。
 *
 * @param eventSink      主 SSE 事件 Sink，Worker 将 tool_call / tool_result 帧推入此 Sink
 * @param userId         当前请求用户 id（用于隔离与审计）
 * @param conversationId 当前会话 id
 * @param depth          当前调度深度（0 = Orchestrator，1 = Worker，2+ = 禁止）
 */
public record SubAgentWorkerContext(Many<ChatChunkDto> eventSink, String userId, String conversationId, int depth) {

    /** ToolContext 中存储本对象的键名。 */
    public static final String CTX_KEY = "workerCtx";

    /**
     * 构造初始 Orchestrator 层上下文（depth = 0）。
     */
    public SubAgentWorkerContext(Many<ChatChunkDto> eventSink, String userId, String conversationId) {
        this(eventSink, userId, conversationId, 0);
    }

    /**
     * 产生 depth+1 的不可变副本，用于向下传递给下一层 Worker。
     *
     * @return depth 加 1 的新上下文实例
     */
    public SubAgentWorkerContext incrementDepth() {
        return new SubAgentWorkerContext(eventSink, userId, conversationId, depth + 1);
    }
}
