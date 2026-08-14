package xyz.ppmblszdp.ai.agent.plan;

import java.util.List;
import xyz.ppmblszdp.ai.agent.plan.dto.TaskPlanDto;
import xyz.ppmblszdp.ai.agent.plan.dto.TaskStepDto;

/**
 * ReAct 上下文剪枝器：防止多步推理中历史 Observation 累加造成 Token 爆炸。
 *
 * <p>策略：
 * <ul>
 *   <li>当前步骤：保留完整 Raw Observation，便于 LLM 进行精确评估与下一步决策；</li>
 *   <li>历史已完成/跳过步骤：裁剪 Observation 为关键结论摘要（默认保留前 300 字符，超长折叠），
 *       过滤掉巨型 Git Diff / 大代码段 / 冗长日志；</li>
 *   <li>总体大小熔断：单次提示词中历史步骤观察结果总字符限制在 2000 字符内。</li>
 * </ul>
 */
public final class ContextPruner {

    private static final int MAX_HISTORICAL_OBSERVATION_CHARS = 300;
    private static final int MAX_TOTAL_HISTORY_CHARS = 2000;

    private ContextPruner() {}

    /**
     * 对历史步骤进行剪枝并格式化为 ReAct 执行历史上下文。
     *
     * @param plan          当前任务计划
     * @param currentStepId 当前正在执行的步骤序号
     * @return 剪枝后的结构化历史上下文文本
     */
    public static String pruneAndFormatHistory(TaskPlanDto plan, int currentStepId) {
        if (plan == null || plan.steps() == null || plan.steps().isEmpty()) {
            return "（暂无历史执行记录）";
        }

        StringBuilder sb = new StringBuilder();
        int totalChars = 0;

        List<TaskStepDto> steps = plan.steps();
        for (TaskStepDto step : steps) {
            if (step.stepId() == null || step.stepId() >= currentStepId) {
                continue; // 仅处理历史已执行步骤
            }

            sb.append(String.format("### 步骤 %d: %s [%s]\n", step.stepId(), step.title(), step.status()));
            if (step.thought() != null && !step.thought().isBlank()) {
                sb.append("• Thought: ").append(truncate(step.thought(), 150)).append("\n");
            }
            if (step.toolName() != null && !step.toolName().isBlank()) {
                sb.append("• Tool: ").append(step.toolName());
                if (step.actionArgs() != null && !step.actionArgs().isBlank()) {
                    sb.append(" (args: ").append(truncate(step.actionArgs(), 100)).append(")");
                }
                sb.append("\n");
            }

            String rawObs = step.observation();
            if (rawObs != null && !rawObs.isBlank()) {
                String prunedObs = pruneObservation(rawObs, MAX_HISTORICAL_OBSERVATION_CHARS);
                sb.append("• Observation: ").append(prunedObs).append("\n");
            } else if (step.errorMessage() != null && !step.errorMessage().isBlank()) {
                sb.append("• Error: ").append(truncate(step.errorMessage(), 150)).append("\n");
            }
            sb.append("\n");

            totalChars += sb.length();
            if (totalChars > MAX_TOTAL_HISTORY_CHARS) {
                sb.append("... (更早的历史步骤已自动省略，避免 Token 超限)\n");
                break;
            }
        }

        return sb.toString().trim();
    }

    /**
     * 裁剪单条历史 Observation 文本。
     */
    public static String pruneObservation(String text, int maxChars) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.length() <= maxChars) {
            return trimmed;
        }
        int omitted = trimmed.length() - maxChars;
        return trimmed.substring(0, maxChars) + String.format("... [已折叠裁剪剩余 %d 字符]", omitted);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        String t = s.trim().replaceAll("\\s+", " ");
        if (t.length() <= max) return t;
        return t.substring(0, max) + "…";
    }
}
