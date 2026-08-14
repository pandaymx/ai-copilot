package xyz.ppmblszdp.ai.agent.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import xyz.ppmblszdp.ai.agent.plan.dto.TaskPlanDto;
import xyz.ppmblszdp.ai.agent.plan.dto.TaskStepDto;

/**
 * 结构化多步任务规划器（TaskPlanner）：
 * 负责将复杂目标拆解为逐步可执行的结构化计划，并在步骤异常时执行自适应动态重规划。
 *
 * <p>具备自适应防死循环（Anti-Looping）保护：
 * <ul>
 *   <li>单步最大重规划/重试次数 {@code MAX_REPLAN_PER_STEP = 2}</li>
 *   <li>全局最大重规划总次数 {@code MAX_TOTAL_REPLANS = 3}</li>
 * </ul>
 */
@Component
public class TaskPlanner {

    private static final Logger log = LoggerFactory.getLogger(TaskPlanner.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final int MAX_REPLAN_PER_STEP = 2;
    public static final int MAX_TOTAL_REPLANS = 3;

    private static final String PLAN_SYSTEM_PROMPT = """
            你是一个专业的高级 AI 任务规划专家（Task Planner）。
            你的职责是将用户的复杂任务目标分解为一个清晰、有逻辑、可逐步执行的多步计划（2 ~ 6 个步骤）。

            可供执行的工具列表如下：
            %s

            请严格以 JSON 格式输出规划结果，不要包含任何额外的自然语言说明，格式如下：
            {
              "title": "简短任务标题",
              "steps": [
                {
                  "stepId": 1,
                  "title": "步骤1标题",
                  "description": "详细操作与目标",
                  "toolName": "推荐使用的工具名称（若无需调用工具可填 NONE）",
                  "expectedOutput": "预期产生的具体结果或信息"
                }
              ]
            }
            """;

    private static final String REPLAN_SYSTEM_PROMPT = """
            你是一个高级 AI 任务自适应重规划专家（Replanner）。
            当前任务在执行步骤 %d 时遇到了错误或偏离了预期。

            【原始目标】: %s
            【失败步骤】: %s
            【错误/观察详情】: %s
            【可供使用的工具】: %s

            请根据已发生的错误，对当前未完成的步骤进行自适应修正、补救或替换。
            要求：
            1. 避免重复执行已经失败且无法成功的相同动作；
            2. 提出有效的替代方案（如换用其他工具、更换正则/关键词、或直接基于已有信息进行局部总结）；
            3. 输出 JSON 格式如下：
            {
              "title": "调整后的计划说明",
              "newSteps": [
                {
                  "stepId": %d,
                  "title": "修正后的步骤标题",
                  "description": "修正后的操作描述",
                  "toolName": "工具名称或 NONE",
                  "expectedOutput": "预期输出"
                }
              ]
            }
            """;

    /**
     * 生成初始任务计划。
     */
    public TaskPlanDto generatePlan(String goal, String context, String availableToolsDesc, ChatClient chatClient) {
        String planId = "plan_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String sysPrompt =
                String.format(PLAN_SYSTEM_PROMPT, availableToolsDesc != null ? availableToolsDesc : "（通用工具集）");
        String userPrompt = "【任务目标】: " + goal + (context != null && !context.isBlank() ? "\n【额外上下文】: " + context : "");

        try {
            String rawJson = chatClient
                    .prompt(new Prompt(List.of(new SystemMessage(sysPrompt), new UserMessage(userPrompt))))
                    .call()
                    .content();

            return parsePlanJson(rawJson, planId, goal);
        } catch (Exception ex) {
            log.warn("LLM 生成任务计划异常，启用单步保底计划: {}", ex.getMessage());
            return buildFallbackPlan(planId, goal);
        }
    }

    /**
     * 针对失败步骤执行动态自适应重规划。
     *
     * @param currentPlan        当前计划
     * @param failedStepId       失败的步骤序号
     * @param failureReason      失败错误或异常观察
     * @param totalReplansSoFar  当前全局已发生的重规划总数
     * @param availableToolsDesc 工具描述
     * @param chatClient         用于推理的 ChatClient
     * @return 调整后的新 TaskPlanDto
     */
    public TaskPlanDto replan(
            TaskPlanDto currentPlan,
            int failedStepId,
            String failureReason,
            int totalReplansSoFar,
            String availableToolsDesc,
            ChatClient chatClient) {

        if (currentPlan == null) {
            return buildFallbackPlan("plan_" + UUID.randomUUID(), "任务恢复执行");
        }

        // ① 防死循环熔断检查：单步重试上限 & 全局重试上限
        TaskStepDto failedStep = findStep(currentPlan, failedStepId);
        int stepReplanCount = (failedStep != null && failedStep.replanCount() != null) ? failedStep.replanCount() : 0;

        if (stepReplanCount >= MAX_REPLAN_PER_STEP || totalReplansSoFar >= MAX_TOTAL_REPLANS) {
            log.warn(
                    "触发重规划防死循环熔断 (stepReplan={}, totalReplans={})，标记步骤 {} 为 FAILED 并尝试优雅降级",
                    stepReplanCount,
                    totalReplansSoFar,
                    failedStepId);
            return handleReplanLoopExceeded(currentPlan, failedStepId, failureReason);
        }

        // ② 调用 LLM 进行自适应步骤修正
        String stepDesc = failedStep != null ? failedStep.description() : "步骤 " + failedStepId;
        String sysPrompt = String.format(
                REPLAN_SYSTEM_PROMPT,
                failedStepId,
                currentPlan.goal(),
                stepDesc,
                failureReason,
                availableToolsDesc != null ? availableToolsDesc : "（通用工具）",
                failedStepId);

        try {
            String rawJson = chatClient
                    .prompt(new Prompt(List.of(new SystemMessage(sysPrompt), new UserMessage("请立即生成重规划后的新步骤 JSON。"))))
                    .call()
                    .content();

            return mergeReplannedSteps(currentPlan, failedStepId, rawJson);
        } catch (Exception ex) {
            log.warn("动态重规划 LLM 调用失败: {}，执行就地跳过降级", ex.getMessage());
            return handleReplanLoopExceeded(currentPlan, failedStepId, failureReason);
        }
    }

    private TaskPlanDto parsePlanJson(String rawJson, String planId, String goal) {
        String cleanJson = extractJsonBlock(rawJson);
        try {
            JsonNode root = MAPPER.readTree(cleanJson);
            String title = root.path("title").asText("多步执行计划");
            JsonNode stepsNode = root.path("steps");

            List<TaskStepDto> steps = new ArrayList<>();
            if (stepsNode.isArray() && !stepsNode.isEmpty()) {
                for (int i = 0; i < stepsNode.size(); i++) {
                    JsonNode s = stepsNode.get(i);
                    int stepId = s.path("stepId").asInt(i + 1);
                    String stepTitle = s.path("title").asText("步骤 " + stepId);
                    String desc = s.path("description").asText("");
                    String tool = s.path("toolName").asText("NONE");
                    String exp = s.path("expectedOutput").asText("");
                    steps.add(TaskStepDto.pending(
                            stepId, stepTitle, desc, "NONE".equalsIgnoreCase(tool) ? null : tool, exp));
                }
            } else {
                steps.add(TaskStepDto.pending(1, "执行综合任务", goal, null, "完成任务目标"));
            }

            return TaskPlanDto.of(planId, title, goal, steps);
        } catch (Exception e) {
            log.warn("解析任务计划 JSON 失败 (raw: {}): {}", rawJson, e.getMessage());
            return buildFallbackPlan(planId, goal);
        }
    }

    private TaskPlanDto mergeReplannedSteps(TaskPlanDto currentPlan, int failedStepId, String rawJson) {
        String cleanJson = extractJsonBlock(rawJson);
        List<TaskStepDto> newStepsList = new ArrayList<>();

        // 保留 failedStepId 之前已经完成的步骤
        for (TaskStepDto step : currentPlan.steps()) {
            if (step.stepId() < failedStepId) {
                newStepsList.add(step);
            }
        }

        try {
            JsonNode root = MAPPER.readTree(cleanJson);
            JsonNode newStepsNode = root.path("newSteps");
            if (newStepsNode.isArray() && !newStepsNode.isEmpty()) {
                int nextId = failedStepId;
                for (JsonNode s : newStepsNode) {
                    String stepTitle = s.path("title").asText("修正步骤 " + nextId);
                    String desc = s.path("description").asText("");
                    String tool = s.path("toolName").asText("NONE");
                    String exp = s.path("expectedOutput").asText("");
                    TaskStepDto newStep = TaskStepDto.pending(
                                    nextId, stepTitle, desc, "NONE".equalsIgnoreCase(tool) ? null : tool, exp)
                            .incrementReplan(desc, tool);
                    newStepsList.add(newStep);
                    nextId++;
                }
            } else {
                newStepsList.add(TaskStepDto.pending(failedStepId, "替代执行", "尝试替代策略完成后续任务", null, ""));
            }
        } catch (Exception e) {
            newStepsList.add(TaskStepDto.pending(failedStepId, "降级总结", "总结已有信息并生成回复", null, ""));
        }

        return currentPlan.withSteps(newStepsList).withStatus("REPLANNING");
    }

    private TaskPlanDto handleReplanLoopExceeded(TaskPlanDto currentPlan, int failedStepId, String failureReason) {
        List<TaskStepDto> updatedSteps = new ArrayList<>();
        for (TaskStepDto step : currentPlan.steps()) {
            if (step.stepId() == failedStepId) {
                updatedSteps.add(step.withObservation("重试达到上限，已跳过此步骤: " + failureReason, false, failureReason)
                        .withStatus("FAILED"));
            } else if (step.stepId() > failedStepId && "PENDING".equalsIgnoreCase(step.status())) {
                updatedSteps.add(step.withStatus("SKIPPED"));
            } else {
                updatedSteps.add(step);
            }
        }
        // 追加一个兜底的总结步骤
        updatedSteps.add(
                TaskStepDto.pending(updatedSteps.size() + 1, "异常降级总结", "针对已获取的信息进行总结输出，并说明跳过步骤原因", null, "部分结果总结"));
        return currentPlan.withSteps(updatedSteps).withStatus("EXECUTING");
    }

    private TaskPlanDto buildFallbackPlan(String planId, String goal) {
        List<TaskStepDto> steps = List.of(TaskStepDto.pending(1, "执行核心任务", goal, null, "完成目标"));
        return TaskPlanDto.of(planId, "单步执行规划", goal, steps);
    }

    private TaskStepDto findStep(TaskPlanDto plan, int stepId) {
        for (TaskStepDto s : plan.steps()) {
            if (s.stepId() != null && s.stepId() == stepId) {
                return s;
            }
        }
        return null;
    }

    public static String extractJsonBlock(String raw) {
        if (raw == null) return "{}";
        String s = raw.trim();
        if (s.contains("```json")) {
            int start = s.indexOf("```json") + 7;
            int end = s.indexOf("```", start);
            if (end > start) {
                return s.substring(start, end).trim();
            }
        } else if (s.contains("```")) {
            int start = s.indexOf("```") + 3;
            int end = s.indexOf("```", start);
            if (end > start) {
                return s.substring(start, end).trim();
            }
        }
        int firstBrace = s.indexOf('{');
        int lastBrace = s.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return s.substring(firstBrace, lastBrace + 1);
        }
        return s;
    }
}
