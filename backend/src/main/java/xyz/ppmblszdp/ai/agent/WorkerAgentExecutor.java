package xyz.ppmblszdp.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.dto.ChatChunkDto;
import xyz.ppmblszdp.ai.factory.ChatOptionsFactory;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;
import xyz.ppmblszdp.ai.tool.ToolEventEmitter;

/**
 * Worker 代理执行器：在调度者（Orchestrator）工具调用时，作为嵌套 ChatModel 执行具体子任务。
 *
 * <h2>关键设计决策</h2>
 * <ol>
 *   <li><b>虚拟线程隔离</b>：Worker ChatModel 的阻塞式 {@code call()} 在独立虚拟线程
 *       {@link Executors#newVirtualThreadPerTaskExecutor()} 上执行，不阻塞主 WebFlux 事件循环
 *       或 Reactor boundedElastic 线程，防止 Orchestrator 等待 Worker 时主线程死锁。</li>
 *   <li><b>深度保护（Recursion Guard）</b>：入口检查 {@code ctx.depth() > maxDepth}，
 *       超限立即发射错误帧并返回，杜绝 Worker→Worker 无限递归。</li>
 *   <li><b>Stateless 单轮 Worker</b>：Worker 仅接收 {@code task} + 专属 systemPrompt，
 *       不继承 Orchestrator 任何历史消息，极大节省 Token 并保持注意力集中。</li>
 *   <li><b>帧发射</b>：直接向 {@link SubAgentWorkerContext#eventSink()} 推送
 *       {@code tool_call} 和 {@code tool_result} 帧，toolName 前缀 {@code sub_agent:xxx}
 *       便于前端渲染专属子代理卡片。</li>
 * </ol>
 */
@Component
public class WorkerAgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(WorkerAgentExecutor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Worker 专属虚拟线程池：每次 Worker 执行在独立虚拟线程中进行，防止阻塞 Reactor 线程。
     * <p>Java 21+ Virtual Threads 支持海量并发阻塞调用而无线程耗尽风险。
     */
    private static final ExecutorService VIRTUAL_THREAD_POOL = Executors.newVirtualThreadPerTaskExecutor();

    private final ProviderRegistry registry;
    private final AiProviderProperties properties;
    private final ToolEventEmitter toolEventEmitter;

    public WorkerAgentExecutor(
            ProviderRegistry registry, AiProviderProperties properties, ToolEventEmitter toolEventEmitter) {
        this.registry = registry;
        this.properties = properties;
        this.toolEventEmitter = toolEventEmitter;
    }

    /**
     * 执行一个子代理 Worker 任务。
     *
     * @param agentType          子代理类型（如 {@code "analysis"}），最终 toolName = {@code "sub_agent:" + agentType}
     * @param task               Orchestrator LLM 生成的子任务描述，作为 Worker 唯一 user 输入
     * @param workerSystemPrompt Worker 专属 system prompt（定义 Worker 角色与专注点）
     * @param ctx                Worker 上下文（携带 SSE Sink、userId、depth 等）
     * @return Worker 生成的文本（原样返回给 Orchestrator LLM 继续推理）
     */
    public String execute(String agentType, String task, String workerSystemPrompt, SubAgentWorkerContext ctx) {
        String toolName = "sub_agent:" + agentType;
        String argsJson = toArgsJson(task);

        int maxDepth = properties.resolveAgent().resolveMaxWorkerDepth();

        // ① 深度保护：超限直接发射错误帧，拒绝递归派发
        if (ctx.depth() > maxDepth) {
            String errMsg = String.format("Worker 嵌套深度 %d 超过上限 %d，已拒绝派发（防止递归调用）", ctx.depth(), maxDepth);
            log.warn("[{}] {}", toolName, errMsg);
            emitErrorFrame(toolName, argsJson, ctx, errMsg);
            return buildErrorJson(errMsg);
        }

        log.info(
                "[{}] 子代理任务开始 (depth={}, user={}, task={})",
                toolName,
                ctx.depth(),
                ctx.userId(),
                task.length() > 32 ? task.substring(0, 32) + "…" : task);

        // ② 发射 tool_call 帧（Worker 开始）
        String callId = ToolEventEmitter.newCallId();
        ctx.eventSink().tryEmitNext(ChatChunkDto.toolCall(callId, toolName, argsJson));

        // ③ 在独立虚拟线程上执行阻塞式 Worker 调用（不阻塞 Reactor 主线程）
        final String workerProvider = properties.resolveAgent().resolveWorkerProvider();
        final String workerModelId = properties.resolveAgent().resolveWorkerModel();
        final int workerMaxTokens = properties.resolveAgent().resolveWorkerMaxTokens();
        log.debug(
                "[{}] Worker 配置: provider={}, model={}, maxTokens={}",
                toolName,
                workerProvider,
                workerModelId,
                workerMaxTokens);

        CompletableFuture<String> future = CompletableFuture.supplyAsync(
                () -> {
                    try {
                        ResolvedModel resolved = registry.resolve(workerProvider, workerModelId);
                        // Worker 专属 ChatOptions（温度 0.3 偏稳定）
                        ChatOptions opts = ChatOptionsFactory.forProvider(resolved, 0.3);

                        // Stateless 单轮消息：仅包含专属 system + 本次 task，不携带任何历史对话
                        List<Message> messages = List.of(new SystemMessage(workerSystemPrompt), new UserMessage(task));
                        Prompt prompt = new Prompt(messages, opts);

                        ChatResponse response = resolved.chatModel().call(prompt);
                        if (response == null
                                || response.getResult() == null
                                || response.getResult().getOutput() == null) {
                            return "";
                        }
                        String text = response.getResult().getOutput().getText();
                        return text != null ? text.strip() : "";
                    } catch (Exception e) {
                        log.warn("[{}] Worker 执行异常: {}", toolName, e.getMessage());
                        throw new RuntimeException("Worker 执行失败: " + e.getMessage(), e);
                    }
                },
                VIRTUAL_THREAD_POOL);

        // Worker 超时 = tool timeout * 3（默认 90s），给 LLM 更充裕的生成时间
        Duration timeout = toolEventEmitter.toolTimeout().multipliedBy(3);

        try {
            String result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            ctx.eventSink().tryEmitNext(ChatChunkDto.toolResult(callId, toolName, buildResultJson(result), false));
            log.info("[{}] 子代理完成 (depth={}, 结果长度={})", toolName, ctx.depth(), result.length());
            return result;
        } catch (Exception e) {
            future.cancel(true);
            String safeMsg = (e.getCause() != null && e.getCause().getMessage() != null)
                    ? e.getCause().getMessage()
                    : (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            ctx.eventSink().tryEmitNext(ChatChunkDto.toolResult(callId, toolName, buildErrorJson(safeMsg), true));
            log.warn("[{}] 子代理失败 (depth={}): {}", toolName, ctx.depth(), safeMsg);
            return "子代理执行失败: " + safeMsg;
        }
    }

    // ---- 私有辅助 ----

    private void emitErrorFrame(String toolName, String argsJson, SubAgentWorkerContext ctx, String errMsg) {
        String callId = ToolEventEmitter.newCallId();
        ctx.eventSink().tryEmitNext(ChatChunkDto.toolCall(callId, toolName, argsJson));
        ctx.eventSink().tryEmitNext(ChatChunkDto.toolResult(callId, toolName, buildErrorJson(errMsg), true));
    }

    private static String toArgsJson(String task) {
        try {
            return MAPPER.writeValueAsString(Map.of("task", task != null ? task : ""));
        } catch (Exception e) {
            return "{\"task\":\"\"}";
        }
    }

    private static String buildResultJson(String result) {
        try {
            return MAPPER.writeValueAsString(Map.of("output", result != null ? result : ""));
        } catch (Exception e) {
            return "{\"output\":\"\"}";
        }
    }

    private static String buildErrorJson(String msg) {
        try {
            return MAPPER.writeValueAsString(Map.of("output", msg != null ? msg : "error"));
        } catch (Exception e) {
            return "{\"output\":\"error\"}";
        }
    }
}
