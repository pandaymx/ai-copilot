package xyz.ppmblszdp.ai.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 子代理调度工具集：提供三个专用的 {@code @Tool} 方法，供 Orchestrator LLM 将复合任务路由给专用 Worker。
 *
 * <h2>设计约束</h2>
 * <ul>
 *   <li><b>WorkerCtx 提取</b>：从 {@link ToolContext} 中取出 {@link SubAgentWorkerContext}，
 *       并调用 {@link SubAgentWorkerContext#incrementDepth()} 生成子层上下文，防止递归；</li>
 *   <li><b>Stateless Worker</b>：Worker 仅接收 {@code task} 字符串，不继承 Orchestrator 历史消息；</li>
 *   <li><b>toolName 前缀约定</b>：Worker 帧的 {@code toolName = "sub_agent:xxx"}，
 *       前端可据此渲染专属"🤖 子代理"折叠卡片。</li>
 * </ul>
 *
 * <p>本类仅在 {@code app.ai.agent.orchestrator-enabled=true} 时由
 * {@link xyz.ppmblszdp.ai.tool.AgentToolRegistry} 注入工具集。
 */
@Component
public class SubAgentTool {

	private static final Logger log = LoggerFactory.getLogger(SubAgentTool.class);

	private static final String ANALYSIS_SYSTEM_PROMPT =
			"你是一个专注于数据分析、统计与推理的 AI 助手。" +
			"请认真完成用户给出的分析任务，输出简洁、结构化的分析结论。" +
			"不要进行闲聊，直接切入任务核心。";

	private static final String CODE_SYSTEM_PROMPT =
			"你是一个专注于代码生成、重构与调试的 AI 助手。" +
			"请根据用户需求输出高质量、可运行的代码，并附上简短说明。" +
			"不要进行闲聊，直接给出代码与必要注释。";

	private static final String SUMMARY_SYSTEM_PROMPT =
			"你是一个专注于信息提炼、文档摘要与知识整合的 AI 助手。" +
			"请将用户提供的内容压缩为精炼、结构化的摘要，抓住关键要点。" +
			"不要发散，保持简洁。";

	private final WorkerAgentExecutor executor;

	public SubAgentTool(WorkerAgentExecutor executor) {
		this.executor = executor;
	}

	/**
	 * 派发给「数据分析型」子代理。
	 * 适用于：数据分析、统计推断、计划拆解、逻辑推理等需要严谨分析的子任务。
	 *
	 * @param task        子任务描述（由 Orchestrator LLM 生成，将作为 Worker 唯一输入）
	 * @param toolContext Spring AI 工具上下文（携带 SubAgentWorkerContext）
	 * @return Worker 分析结论（原样返回给 Orchestrator LLM 进行后续整合）
	 */
	@Tool(description = "调度「数据分析型子代理」：适用于数据分析、统计、逻辑推理、计划拆解等任务。" +
	                    "请将需要分析的子任务完整描述在 task 中，子代理将独立执行并返回分析结论。")
	public String dispatchToAnalysisAgent(
			@ToolParam(description = "子任务描述：向子代理传递完整的分析需求，尽量提供充足背景信息") String task,
			ToolContext toolContext) {
		SubAgentWorkerContext workerCtx = resolveWorkerCtx(toolContext);
		return executor.execute("analysis", task, ANALYSIS_SYSTEM_PROMPT, workerCtx.incrementDepth());
	}

	/**
	 * 派发给「代码生成型」子代理。
	 * 适用于：代码生成、重构、解释、调试、API 设计等编程相关子任务。
	 *
	 * @param task        子任务描述（由 Orchestrator LLM 生成，将作为 Worker 唯一输入）
	 * @param toolContext Spring AI 工具上下文（携带 SubAgentWorkerContext）
	 * @return Worker 生成的代码与说明（原样返回给 Orchestrator LLM 进行后续整合）
	 */
	@Tool(description = "调度「代码生成型子代理」：适用于代码生成、重构、解释、调试等编程任务。" +
	                    "请将编程需求完整描述在 task 中，子代理将独立输出高质量代码。")
	public String dispatchToCodeAgent(
			@ToolParam(description = "子任务描述：向子代理传递完整的编程需求，包含语言、功能目标与约束条件") String task,
			ToolContext toolContext) {
		SubAgentWorkerContext workerCtx = resolveWorkerCtx(toolContext);
		return executor.execute("code", task, CODE_SYSTEM_PROMPT, workerCtx.incrementDepth());
	}

	/**
	 * 派发给「摘要整合型」子代理。
	 * 适用于：长文档摘要、知识提炼、多来源信息整合、报告撰写等任务。
	 *
	 * @param task        子任务描述（由 Orchestrator LLM 生成，将作为 Worker 唯一输入）
	 * @param toolContext Spring AI 工具上下文（携带 SubAgentWorkerContext）
	 * @return Worker 摘要结论（原样返回给 Orchestrator LLM 进行后续整合）
	 */
	@Tool(description = "调度「摘要整合型子代理」：适用于长文档摘要、知识提炼、信息整合等任务。" +
	                    "请将需要摘要整合的内容或任务描述完整放入 task，子代理将输出精炼的摘要。")
	public String dispatchToSummaryAgent(
			@ToolParam(description = "子任务描述：向子代理传递需要摘要或整合的内容与任务要求") String task,
			ToolContext toolContext) {
		SubAgentWorkerContext workerCtx = resolveWorkerCtx(toolContext);
		return executor.execute("summary", task, SUMMARY_SYSTEM_PROMPT, workerCtx.incrementDepth());
	}

	/**
	 * 从 ToolContext 中提取 {@link SubAgentWorkerContext}，入口不可空校验。
	 *
	 * @throws IllegalStateException 若 ToolContext 中未注入 workerCtx（ChatService 装配错误）
	 */
	private SubAgentWorkerContext resolveWorkerCtx(ToolContext toolContext) {
		if (toolContext == null || toolContext.getContext() == null) {
			throw new IllegalStateException("ToolContext 为空，SubAgentTool 必须在 Agent 模式且装配了 workerCtx 时使用");
		}
		Object ctx = toolContext.getContext().get(SubAgentWorkerContext.CTX_KEY);
		if (ctx instanceof SubAgentWorkerContext workerCtx) {
			return workerCtx;
		}
		throw new IllegalStateException(
				"ToolContext 中缺少 SubAgentWorkerContext（key=" + SubAgentWorkerContext.CTX_KEY +
				"），请检查 ChatService 的 toolContext 装配是否注入了 workerCtx");
	}
}
