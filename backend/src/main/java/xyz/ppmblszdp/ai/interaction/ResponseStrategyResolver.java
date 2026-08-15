package xyz.ppmblszdp.ai.interaction;

import java.util.EnumSet;
import java.util.Set;

/**
 * 响应策略决议器：将提取的原子信号与上下文状态映射为高层 {@link InteractionState} 与组合 {@link ResponseStrategy}。
 */
public class ResponseStrategyResolver {

    /**
     * 根据原子交互信号集合与置信度决议最终分析结果。
     *
     * @param signals    提取到的交互信号集合
     * @param confidence 置信度打分
     * @return 最终交互状态与组合响应策略
     */
    public InteractionAnalysis resolve(Set<InteractionSignal> signals, double confidence) {
        if (signals == null || signals.isEmpty()) {
            return InteractionAnalysis.neutral();
        }

        Set<ResponseStrategy> strategies = EnumSet.noneOf(ResponseStrategy.class);
        InteractionState state = InteractionState.NEUTRAL;

        // 1. 纠错与质疑信号优先 (CORRECTION_REQUIRED)
        if (signals.contains(InteractionSignal.REPORTS_ERROR)
                || signals.contains(InteractionSignal.CHALLENGES_PREVIOUS_ANSWER)) {
            state = InteractionState.CORRECTION_REQUIRED;
            strategies.add(ResponseStrategy.CORRECT_PREVIOUS_ANSWER);
            strategies.add(ResponseStrategy.DIRECT_ANSWER);
        }
        // 2. 强烈不满与直接指令信号 (DIRECTIVE / DISSATISFIED)
        else if (signals.contains(InteractionSignal.EXPRESSES_DISSATISFACTION)
                || signals.contains(InteractionSignal.REQUESTS_CONCISION)
                || signals.contains(InteractionSignal.REQUESTS_DIRECT_ANSWER)) {
            state = signals.contains(InteractionSignal.EXPRESSES_DISSATISFACTION)
                    ? InteractionState.DISSATISFIED
                    : InteractionState.DIRECTIVE;
            if (signals.contains(InteractionSignal.REQUESTS_CONCISION)) {
                strategies.add(ResponseStrategy.CONCISE);
            }
            if (signals.contains(InteractionSignal.REQUESTS_DIRECT_ANSWER)) {
                strategies.add(ResponseStrategy.DIRECT_ANSWER);
            }
        }
        // 3. 困惑与概念不解信号 (CONFUSED)
        else if (signals.contains(InteractionSignal.EXPRESSES_CONFUSION)) {
            state = InteractionState.CONFUSED;
            strategies.add(ResponseStrategy.EXPLANATORY);
            strategies.add(ResponseStrategy.STEP_BY_STEP);
        }
        // 4. 深度机制探究信号 (DEEP_DIVE)
        else if (signals.contains(InteractionSignal.REQUESTS_DEEP_DIVE)) {
            state = InteractionState.DEEP_DIVE;
            strategies.add(ResponseStrategy.DEEP_ANALYSIS);
        }
        // 5. 对比与技术选型信号 (EXPLORATORY)
        else if (signals.contains(InteractionSignal.REQUESTS_COMPARISON)) {
            state = InteractionState.EXPLORATORY;
            strategies.add(ResponseStrategy.COMPARATIVE);
        }
        // 6. 强任务/代码落地信号 (TASK_FOCUSED)
        else if (signals.contains(InteractionSignal.REQUESTS_CODE)) {
            state = InteractionState.TASK_FOCUSED;
            strategies.add(ResponseStrategy.CODE_FIRST);
        }
        // 7. 正面确认信号 (POSITIVE)
        else if (signals.contains(InteractionSignal.POSITIVE_FEEDBACK)) {
            state = InteractionState.POSITIVE;
            strategies.add(ResponseStrategy.DEFAULT);
        }

        // 附加交叉策略修饰
        if (signals.contains(InteractionSignal.REQUESTS_CODE)) {
            strategies.add(ResponseStrategy.CODE_FIRST);
        }
        if (signals.contains(InteractionSignal.REQUESTS_EXAMPLE)) {
            strategies.add(ResponseStrategy.EXAMPLE_FIRST);
        }
        if (signals.contains(InteractionSignal.REQUESTS_STEP_BY_STEP)) {
            strategies.add(ResponseStrategy.STEP_BY_STEP);
        }
        if (signals.contains(InteractionSignal.REQUESTS_CONCISION)) {
            strategies.add(ResponseStrategy.CONCISE);
        }

        if (strategies.isEmpty()) {
            strategies.add(ResponseStrategy.DEFAULT);
        }

        return new InteractionAnalysis(state, confidence, signals, strategies);
    }
}
