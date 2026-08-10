package xyz.ppmblszdp.ai.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;

import io.netty.handler.timeout.TimeoutException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.dto.ChatChunkDto;

import java.time.Duration;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Agent 工具调用事件发射器：统一管理「工具上下文 → SSE 帧」的桥接逻辑。
 *
 * <h2>设计要点（回应生产隐患）</h2>
 * <ul>
 * <li><b>Sink 线程安全</b>：使用
 * {@link Sinks#many()#multicast()#onBackpressureBuffer()}，
 * 避免 Reactor 管道 resubscribe 时 unicast 抛
 * {@code IllegalStateException: UnicastProcessor allows only a single Subscriber}；
 * 配合 {@code doFinally} 在流结束时 {@code tryEmitComplete()} 防止 Flux 泄漏/阻塞。</li>
 * <li><b>Tool Loop Safety</b>：通过 ToolContext 携带统一的 {@code step} 计数器，配合
 * 配置项 {@code maxToolCalls} 上限与 {@code timeoutSeconds} 超时，防止 LLM 陷入死循环工具调用消耗
 * Token。</li>
 * <li><b>统一执行包装</b>：{@link #executeWithEvent(String, String, ToolContext, Supplier)}
 * 内聚
 * callId 生成、tool_call 发帧、执行、tool_result（成功/错误）发帧，消除各 @Tool 重复样板。</li>
 * </ul>
 *
 * <p>
 * 该组件作为单例 Bean 注入各工具类，工具类仅负责业务逻辑，发帧与配对交由本类托管。
 */
@Component
public class ToolEventEmitter {

	private static final Logger log = LoggerFactory.getLogger(ToolEventEmitter.class);

	/** 单次请求内允许连续调用工具的兜底上限（优先取配置 app.ai.agent.max-tool-calls）。 */
	public static final int DEFAULT_MAX_TOOL_CALLS = 5;

	/** 单次工具执行超时的兜底上限（优先取配置 app.ai.agent.timeout-seconds）。 */
	public static final Duration DEFAULT_TOOL_TIMEOUT = Duration.ofSeconds(30);

	/** 工具上下文键：用于在各 @Tool 间共享本发射器，避免重复注入。 */
	public static final String CTX_EMITTER = "toolEventEmitter";

	/** 工具上下文键：当前用户 id（知识库/文件隔离维度）。 */
	public static final String CTX_USER_ID = "userId";

	private final AiProviderProperties properties;

	public ToolEventEmitter(AiProviderProperties properties) {
		this.properties = properties;
	}

	/** 单次请求允许的工具调用上限（取配置，缺省兜底）。 */
	public int maxToolCalls() {
		return properties.resolveAgent().resolveMaxToolCalls();
	}

	/** 单次工具执行超时（取配置，缺省兜底）。 */
	public Duration toolTimeout() {
		return Duration.ofSeconds(properties.resolveAgent().resolveTimeoutSeconds());
	}

	/**
	 * 从 ToolContext 取出本发射器；若未注入则抛 IllegalStateException（应在 ChatService 装配时保证）。
	 */
	public static ToolEventEmitter from(ToolContext toolContext) {
		Object o = toolContext.getContext().get(CTX_EMITTER);
		if (o instanceof ToolEventEmitter e) {
			return e;
		}
		throw new IllegalStateException("ToolContext 中缺少 ToolEventEmitter，请检查 ChatService 的 .toolContext 装配");
	}

	/**
	 * 生成唯一调用标识，保证 tool_call 与 tool_result 配对（对齐 sse-protocol.md）。
	 */
	public static String newCallId() {
		return "call_" + UUID.randomUUID().toString().replace("-", "");
	}

	/**
	 * 创建线程安全的 multicast Sink（工具事件通道）。
	 * 调用方应在合并 Flux 的 doFinally 中调用返回的 {@link Many#tryEmitComplete()}。
	 */
	public Many<ChatChunkDto> newSink() {
		return Sinks.many().multicast().onBackpressureBuffer();
	}

	/**
	 * 统一执行包装器：执行工具业务逻辑并向 SSE 流推送 tool_call / tool_result 帧。
	 *
	 * @param toolName    工具名称（用于 tool_call/tool_result 帧展示）
	 * @param argsJson    已序列化的参数 JSON 字符串（必须合法 JSON Object）
	 * @param toolContext 工具上下文（携带 Sink、step 计数器、userId）
	 * @param executor    业务逻辑（返回结果字符串；抛异常将被捕获为错误帧）
	 * @return 工具执行结果（原样返回给 LLM 用于后续推理）
	 */
	public String executeWithEvent(String toolName, String argsJson, ToolContext toolContext,
			Supplier<String> executor) {
		Many<ChatChunkDto> sink = resolveSink(toolContext);
		String callId = newCallId();

		// Tool Loop 计数：超出上限直接拒绝，避免 LLM 死循环（用 get/put 兼容任意 Map 实现）
		Map<String, Object> ctx = toolContext.getContext();
		AtomicInteger stepCounter = (AtomicInteger) ctx.get("__step");
		if (stepCounter == null) {
			stepCounter = new AtomicInteger(0);
			ctx.put("__step", stepCounter);
		}
		int step = stepCounter.incrementAndGet();
		int maxCalls = maxToolCalls();
		if (step > maxCalls) {
			String errMsg = "工具调用次数超过上限（" + maxCalls + "），已终止 Agent 循环以防止 Token 耗尽";
			log.warn("[{}] {}", toolName, errMsg);
			emit(sink, ChatChunkDto.toolCall(callId, toolName, argsJson));
			emit(sink, ChatChunkDto.toolResult(callId, toolName, "{\"output\":\"" + escape(errMsg) + "\"}", true));
			return "{\"output\":\"工具调用被安全限流\"}";
		}

		emit(sink, ChatChunkDto.toolCall(callId, toolName, argsJson));
		log.debug("[{}] tool_call emitted, callId={}, step={}", toolName, callId, step);

		try {
			String result = runWithTimeout(executor);
			emit(sink, ChatChunkDto.toolResult(callId, toolName, result, false));
			log.debug("[{}] tool_result emitted (success), callId={}", toolName, callId);
			return result;
		} catch (Exception e) {
			// 对齐 AGENTS.md：不掩盖错误根因，但必须发送错误帧保证流完整性（异常不冒泡中断 SSE）
			String safeMsg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
			String errJson = "{\"output\":\"" + escape(safeMsg) + "\"}";
			emit(sink, ChatChunkDto.toolResult(callId, toolName, errJson, true));
			log.warn("[{}] tool_result emitted (error), callId={}, cause={}", toolName, callId, e.getMessage());
			return "{\"output\":\"工具执行失败\"}";
		}
	}

	private String runWithTimeout(Supplier<String> executor) throws Exception {
		// 在虚拟线程/Reactor 调度下做带超时的执行；超时则中断并抛异常转为错误帧
		Duration timeout = toolTimeout();
		CompletableFuture<String> future = CompletableFuture.supplyAsync(executor::get);
		try {
			return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
		} catch (TimeoutException te) {
			future.cancel(true);
			throw new RuntimeException("工具执行超时（>" + timeout.toSeconds() + "s）", te);
		}
	}

	private Many<ChatChunkDto> resolveSink(ToolContext toolContext) {
		Object o = toolContext.getContext().get("eventSink");
		if (o instanceof Many<?> sink) {
			@SuppressWarnings("unchecked")
			Many<ChatChunkDto> typed = (Many<ChatChunkDto>) sink;
			return typed;
		}
		throw new IllegalStateException("ToolContext 中缺少 eventSink");
	}

	private void emit(Sinks.Many<ChatChunkDto> sink, ChatChunkDto dto) {
		sink.tryEmitNext(dto);
	}

	private static String escape(String s) {
		if (s == null)
			return "";
		return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
	}

	/** 将工具事件 Flux 与入参 LLM 内容 Flux 合并（自然穿插，先 tool_call 后 tool_result）。 */
	public static Flux<ChatChunkDto> mergeWith(Flux<ChatChunkDto> contentFlux, Sinks.Many<ChatChunkDto> toolSink) {
		return Flux.merge(contentFlux, toolSink.asFlux());
	}
}
