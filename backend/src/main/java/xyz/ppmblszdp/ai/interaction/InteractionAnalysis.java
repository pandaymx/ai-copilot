package xyz.ppmblszdp.ai.interaction;

import java.util.Collections;
import java.util.Set;

/**
 * 交互状态理解分析结果 DTO。
 *
 * @param state       宏观交互认知状态
 * @param confidence  置信度评分 (0.0 ~ 1.0)
 * @param signals     提取到的正交原子信号集合
 * @param strategies  决议出的组合响应策略集合
 */
public record InteractionAnalysis(
        InteractionState state, double confidence, Set<InteractionSignal> signals, Set<ResponseStrategy> strategies) {

    public InteractionAnalysis {
        signals = signals != null ? Collections.unmodifiableSet(signals) : Collections.emptySet();
        strategies = strategies != null ? Collections.unmodifiableSet(strategies) : Collections.emptySet();
    }

    public static InteractionAnalysis neutral() {
        return new InteractionAnalysis(
                InteractionState.NEUTRAL, 1.0, Collections.emptySet(), Set.of(ResponseStrategy.DEFAULT));
    }
}
