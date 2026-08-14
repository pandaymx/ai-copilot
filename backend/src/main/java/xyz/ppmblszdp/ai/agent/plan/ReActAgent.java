package xyz.ppmblszdp.ai.agent.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;
import xyz.ppmblszdp.ai.agent.plan.dto.ReActStepAction;
import xyz.ppmblszdp.ai.agent.plan.dto.TaskPlanDto;
import xyz.ppmblszdp.ai.agent.plan.dto.TaskStepDto;
import xyz.ppmblszdp.ai.dto.ChatChunkDto;
import xyz.ppmblszdp.ai.tool.ToolEventEmitter;

/**
 * ReAct (Reason → Act → Observe) 闭环多步执行引擎。
 *
 * <p>核心能力：
 * <ul>
 *   <li>多步动态规划：执行初始计划，并在步骤异常时自适应重规划；</li>
 *   <li>上下文累加剪枝：历史步骤 Observation 自动截断摘要，防止 Token 爆炸；</li>
 *   <li>防死循环保护：最大步数（默认 8，上限 15）、单步重试限制与全局重试限制；</li>
 *   <li>异步用户取消感知：每步检测 {@code isAborted} 标志位，前端中断时秒级停止后端执行；</li>
 *   <li>全链路 SSE 帧：推送 {@code task_plan}、{@code task_step}、{@code reasoning} 与 {@code content}。</li>
 * </ul>
 */
@Component
public class ReActAgent {

    private static final Logger log = LoggerFactory.getLogger(ReActAgent.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ExecutorService VIRTUAL_THREAD_POOL = Executors.newVirtualThreadPerTaskExecutor();

    public static final int DEFAULT_MAX_STEPS = 8;
    public static final int ABSOLUTE_MAX_STEPS = 15;

    private final TaskPlanner taskPlanner;

    public ReActAgent(TaskPlanner taskPlanner) {
        this.taskPlanner = taskPlanner;
    }

    private static final String REACT_STEP_PROMPT = """
            你是一个高级 ReAct 自主推理代理（Reasoning & Action Agent）。
            你当前正在按任务规划逐步执行多步复杂任务。

            【总任务目标】: %s
            【当前步骤 (%d/%d)】: %s
            【步骤说明】: %s
            【预期产出】: %s

            【历史步骤执行结果 (已剪枝)】:
            %s

            【当前可用工具】:
            %s

            请严格按照 Reason → Act 模式进行决策：
            1. Thought: 深入分析当前步骤的需求，结合历史 Observation，决定本步采取什么行动；
            2. Action:
               - 如果需要调用工具，指定 actionType 为 "TOOL_CALL"，并给出工具名和严格合法的 JSON 参数；
               - 如果当前步骤已成功无需调用工具或已达成目标，指定 actionType 为 "FINISH"；
               - 如果发现当前步骤方向错误需要调整计划，指定 actionType 为 "REPLAN"；

            输出必须为纯 JSON 格式：
            {
              "thought": "你的思考逻辑与推理过程",
              "actionType": "TOOL_CALL | FINISH | REPLAN",
              "toolName": "调用的工具名（若 actionType=TOOL_CALL）",
              "toolArgs": { "参数键": "参数值" },
              "explanation": "简短说明"
            }
            """;

    private static final String FINAL_SYNTHESIS_PROMPT = """
            你是一个专业的高级 AI 助手。
            任务规划的所有多步推理与工具执行已经全部结束。

            【用户原始任务目标】: %s
            【所有步骤执行记录与观察】:
            %s

            请对上述所有步骤的产出与结果进行全面、专业、结构化的总结，直接向用户呈现最终解答与核心成果。
            要求：
            1. 条理清晰，使用精美的 Markdown 格式与要点排版；
            2. 准确引用执行产物（如代码片段、搜索发现、分析指标）；
            3. 如果有部分步骤跳过或降级，以友好的方式说明情况。
            """;

    /**
     * 运行 ReAct 规划与多步执行循环。
     *
     * @param goal               任务目标
     * @param context            用户附加上下文
     * @param availableCallbacks 系统已注册的 ToolCallback 列表
     * @param chatClient         用于 LLM 推理的 ChatClient
     * @param sink               SSE 事件流发射器
     * @param isAborted          用户取消/中断标志位
     * @param maxSteps           最大允许执行步数（防死循环）
     * @return 最终聚合生成的回答内容
     */
    public String run(
            String goal,
            String context,
            List<ToolCallback> availableCallbacks,
            ChatClient chatClient,
            Sinks.Many<ChatChunkDto> sink,
            AtomicBoolean isAborted,
            Integer maxSteps) {

        int stepLimit = (maxSteps != null && maxSteps > 0) ? Math.min(maxSteps, ABSOLUTE_MAX_STEPS) : DEFAULT_MAX_STEPS;
        Map<String, ToolCallback> toolMap = buildToolMap(availableCallbacks);
        String toolsDesc = formatToolsDescription(availableCallbacks);

        // 1. 生成初始任务计划并推送 SSE
        log.info("ReActAgent 开始任务规划: goal={}", goal);
        TaskPlanDto plan = taskPlanner.generatePlan(goal, context, toolsDesc, chatClient);
        plan = plan.withStatus("EXECUTING");
        emitPlan(sink, plan);

        int currentStepIndex = 1;
        int totalReplans = 0;
        int stepExecutionCount = 0;

        while (stepExecutionCount < stepLimit) {
            // 2. 检查用户是否主动中断
            if (isAborted != null && isAborted.get()) {
                log.info("检测到用户主动取消 ReAct 任务 (planId={})", plan.planId());
                plan = plan.withStatus("CANCELLED").withSummary("用户已中断任务执行");
                emitPlan(sink, plan);
                return "任务已被用户取消。";
            }

            // 检查计划是否所有步骤都已执行完毕
            TaskStepDto currentStep = findStep(plan, currentStepIndex);
            if (currentStep == null) {
                log.info("所有计划步骤执行完毕 (planId={})", plan.planId());
                break;
            }

            stepExecutionCount++;
            plan = plan.withCurrentStep(currentStepIndex);
            emitPlan(sink, plan);

            // 3. 上下文累加剪枝：构建历史执行记录
            String prunedHistory = ContextPruner.pruneAndFormatHistory(plan, currentStepIndex);

            // 4. LLM 进行 Reason → Act 决策
            String stepPrompt = String.format(
                    REACT_STEP_PROMPT,
                    plan.goal(),
                    currentStep.stepId(),
                    plan.steps().size(),
                    currentStep.title(),
                    currentStep.description(),
                    currentStep.expectedOutput() != null ? currentStep.expectedOutput() : "完成操作",
                    prunedHistory,
                    toolsDesc);

            ReActStepAction action = decideAction(chatClient, stepPrompt, currentStep);

            // 推送 Thought 思考过程与 Step RUNNING 状态
            if (action.thought() != null && !action.thought().isBlank()) {
                sink.tryEmitNext(
                        ChatChunkDto.reasoning("💭 [步骤 " + currentStepIndex + "] " + action.thought() + "\n\n"));
            }
            currentStep = currentStep.withExecution(action.thought(), action.toolName(), action.toolArgs());
            updateStepInPlan(plan, currentStep);
            emitStep(sink, currentStep);

            // 5. 根据决策动作执行
            if ("FINISH".equalsIgnoreCase(action.actionType())) {
                log.info("步骤 {} 标记完成: {}", currentStepIndex, action.explanation());
                currentStep = currentStep.withObservation(
                        action.explanation() != null ? action.explanation() : "步骤已成功完成", true, null);
                updateStepInPlan(plan, currentStep);
                emitStep(sink, currentStep);
                currentStepIndex++;
            } else if ("REPLAN".equalsIgnoreCase(action.actionType())) {
                log.info("步骤 {} 触发主动重规划", currentStepIndex);
                totalReplans++;
                plan = taskPlanner.replan(
                        plan, currentStepIndex, action.thought(), totalReplans, toolsDesc, chatClient);
                emitPlan(sink, plan);
            } else if ("SKIP".equalsIgnoreCase(action.actionType())) {
                log.info("步骤 {} 被跳过: {}", currentStepIndex, action.explanation());
                currentStep =
                        currentStep.withStatus("SKIPPED").withObservation("已跳过: " + action.explanation(), true, null);
                updateStepInPlan(plan, currentStep);
                emitStep(sink, currentStep);
                currentStepIndex++;
            } else {
                // TOOL_CALL 动作：执行具体工具
                String toolName = action.toolName();
                String toolArgs = action.toolArgs() != null ? action.toolArgs() : "{}";
                log.info("ReAct 步骤 {} 调用工具: {} (args={})", currentStepIndex, toolName, toolArgs);

                ToolExecutionResult execResult =
                        executeToolInVirtualThread(toolMap, toolName, toolArgs, sink, isAborted);

                if (execResult.success()) {
                    currentStep = currentStep.withObservation(execResult.output(), true, null);
                    updateStepInPlan(plan, currentStep);
                    emitStep(sink, currentStep);
                    currentStepIndex++;
                } else {
                    // 工具执行失败：触发动态重规划
                    log.warn("步骤 {} 工具执行异常: {}，准备触发自适应重规划", currentStepIndex, execResult.errorMessage());
                    totalReplans++;
                    plan = taskPlanner.replan(
                            plan, currentStepIndex, execResult.errorMessage(), totalReplans, toolsDesc, chatClient);
                    emitPlan(sink, plan);
                }
            }
        }

        // 6. 所有步骤完成，生成最终总结
        plan = plan.withStatus("COMPLETED");
        emitPlan(sink, plan);

        String fullHistory = ContextPruner.pruneAndFormatHistory(plan, Integer.MAX_VALUE);
        String finalSynthesisPrompt = String.format(FINAL_SYNTHESIS_PROMPT, plan.goal(), fullHistory);

        try {
            String finalAnswer = chatClient
                    .prompt(new Prompt(
                            List.of(new SystemMessage("你是一个专业的总结呈现助手。"), new UserMessage(finalSynthesisPrompt))))
                    .call()
                    .content();

            plan = plan.withSummary(finalAnswer);
            emitPlan(sink, plan);

            // 最终内容流式推送到前端
            sink.tryEmitNext(ChatChunkDto.content(finalAnswer));
            return finalAnswer;
        } catch (Exception ex) {
            String fallbackAnswer = "已完成任务的多步执行与分析。详细步骤结果请参考上方任务计划看板。";
            sink.tryEmitNext(ChatChunkDto.content(fallbackAnswer));
            return fallbackAnswer;
        }
    }

    private ReActStepAction decideAction(ChatClient chatClient, String stepPrompt, TaskStepDto step) {
        try {
            String rawJson = chatClient
                    .prompt(new Prompt(
                            List.of(new SystemMessage("你是一个 ReAct 任务决策器。严格输出 JSON。"), new UserMessage(stepPrompt))))
                    .call()
                    .content();

            String cleanJson = TaskPlanner.extractJsonBlock(rawJson);
            JsonNode root = MAPPER.readTree(cleanJson);

            String thought = root.path("thought").asText("执行当前步骤");
            String actionType = root.path("actionType").asText("TOOL_CALL");
            String toolName = root.path("toolName").asText(step.toolName() != null ? step.toolName() : "NONE");
            JsonNode toolArgsNode = root.path("toolArgs");
            String toolArgs = toolArgsNode.isObject()
                    ? MAPPER.writeValueAsString(toolArgsNode)
                    : root.path("toolArgs").asText("{}");
            String explanation = root.path("explanation").asText(null);

            if ("NONE".equalsIgnoreCase(toolName) && "TOOL_CALL".equalsIgnoreCase(actionType)) {
                actionType = "FINISH";
            }

            return new ReActStepAction(thought, actionType, toolName, toolArgs, explanation);
        } catch (Exception e) {
            log.warn("ReAct 决策 JSON 解析异常: {}，降级为默认工具调用", e.getMessage());
            return new ReActStepAction(
                    "根据预定计划执行步骤", step.toolName() != null ? "TOOL_CALL" : "FINISH", step.toolName(), "{}", "按默认步骤执行");
        }
    }

    private ToolExecutionResult executeToolInVirtualThread(
            Map<String, ToolCallback> toolMap,
            String toolName,
            String toolArgs,
            Sinks.Many<ChatChunkDto> sink,
            AtomicBoolean isAborted) {

        if (isAborted != null && isAborted.get()) {
            return new ToolExecutionResult(false, null, "用户已中止执行");
        }

        ToolCallback callback = toolMap.get(toolName);
        if (callback == null) {
            return new ToolExecutionResult(false, null, "未找到工具: " + toolName);
        }

        String callId = ToolEventEmitter.newCallId();
        sink.tryEmitNext(ChatChunkDto.toolCall(callId, toolName, toolArgs));

        CompletableFuture<String> future = CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return callback.call(toolArgs);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                VIRTUAL_THREAD_POOL);

        try {
            String result = future.get(45, TimeUnit.SECONDS);
            sink.tryEmitNext(ChatChunkDto.toolResult(callId, toolName, result, false));
            return new ToolExecutionResult(true, result, null);
        } catch (Exception ex) {
            future.cancel(true);
            String errMsg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
            sink.tryEmitNext(ChatChunkDto.toolResult(callId, toolName, "{\"error\":\"" + errMsg + "\"}", true));
            return new ToolExecutionResult(false, null, errMsg);
        }
    }

    private Map<String, ToolCallback> buildToolMap(List<ToolCallback> callbacks) {
        Map<String, ToolCallback> map = new HashMap<>();
        if (callbacks != null) {
            for (ToolCallback cb : callbacks) {
                if (cb.getToolDefinition() != null) {
                    map.put(cb.getToolDefinition().name(), cb);
                }
            }
        }
        return map;
    }

    private String formatToolsDescription(List<ToolCallback> callbacks) {
        if (callbacks == null || callbacks.isEmpty()) return "无可用工具";
        StringBuilder sb = new StringBuilder();
        for (ToolCallback cb : callbacks) {
            if (cb.getToolDefinition() != null) {
                sb.append("• `")
                        .append(cb.getToolDefinition().name())
                        .append("`: ")
                        .append(cb.getToolDefinition().description())
                        .append("\n");
            }
        }
        return sb.toString();
    }

    private TaskStepDto findStep(TaskPlanDto plan, int stepId) {
        for (TaskStepDto s : plan.steps()) {
            if (s.stepId() != null && s.stepId() == stepId) {
                return s;
            }
        }
        return null;
    }

    private void updateStepInPlan(TaskPlanDto plan, TaskStepDto updatedStep) {
        for (int i = 0; i < plan.steps().size(); i++) {
            if (plan.steps().get(i).stepId().equals(updatedStep.stepId())) {
                plan.steps().set(i, updatedStep);
                return;
            }
        }
    }

    private void emitPlan(Sinks.Many<ChatChunkDto> sink, TaskPlanDto plan) {
        try {
            sink.tryEmitNext(ChatChunkDto.taskPlan(MAPPER.writeValueAsString(plan)));
        } catch (Exception ignored) {
        }
    }

    private void emitStep(Sinks.Many<ChatChunkDto> sink, TaskStepDto step) {
        try {
            sink.tryEmitNext(ChatChunkDto.taskStep(MAPPER.writeValueAsString(step)));
        } catch (Exception ignored) {
        }
    }

    private record ToolExecutionResult(boolean success, String output, String errorMessage) {}
}
