package xyz.ppmblszdp.ai.interaction;

import java.util.Set;

/**
 * 将 {@link InteractionAnalysis} 决议结果转化为高强度、结构化 System Prompt 约束指令。
 */
public final class InteractionPromptPolicy {

    private InteractionPromptPolicy() {}

    /**
     * 组装结构化 {@code <interaction_policy>} 指令块。
     *
     * @param analysis 交互理解结果
     * @return 注入系统提示词的约束字符串；若为中性默认状态则返回空字符串
     */
    public static String buildSystemPromptPolicy(InteractionAnalysis analysis) {
        if (analysis == null || analysis.state() == InteractionState.NEUTRAL) {
            if (analysis == null
                    || analysis.strategies().isEmpty()
                    || (analysis.strategies().size() == 1
                            && analysis.strategies().contains(ResponseStrategy.DEFAULT))) {
                return "";
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【🎯 用户交互状态与动态响应策略约束 / Interaction Policy】:\n");
        sb.append("<interaction_policy>\n");
        sb.append("Current interaction state: ").append(analysis.state().name());
        sb.append(" (")
                .append(analysis.state().getLabel())
                .append(" - ")
                .append(analysis.state().getDescription())
                .append(")\n\n");

        Set<InteractionSignal> signals = analysis.signals();
        if (!signals.isEmpty()) {
            sb.append("Detected user signals & constraints:\n");
            for (InteractionSignal sig : signals) {
                sb.append("- [")
                        .append(sig.name())
                        .append("] ")
                        .append(getSignalDirective(sig))
                        .append("\n");
            }
            sb.append("\n");
        }

        Set<ResponseStrategy> strategies = analysis.strategies();
        if (!strategies.isEmpty()) {
            sb.append("Mandatory execution guidelines for this turn:\n");
            for (ResponseStrategy strat : strategies) {
                if (strat != ResponseStrategy.DEFAULT) {
                    sb.append("• ")
                            .append(strat.name())
                            .append(": ")
                            .append(strat.getPolicyGuideline())
                            .append("\n");
                }
            }
        }

        sb.append("</interaction_policy>");
        return sb.toString();
    }

    private static String getSignalDirective(InteractionSignal signal) {
        return switch (signal) {
            case REQUESTS_CONCISION -> "用户强烈要求极简，严禁任何废话、寒暄、铺垫或冗长总结。";
            case REQUESTS_DIRECT_ANSWER -> "用户需要开门见山，首句直接给出明确答案、结论或执行命令。";
            case REQUESTS_CODE -> "用户需要代码实现，直接提供完整、可编译、带必要关键注释的代码块。";
            case REQUESTS_EXPLANATION -> "用户需要原理解析，使用通俗、结构化语言阐释内部机制。";
            case REQUESTS_EXAMPLE -> "用户需要具体案例，通过最小可复现示例或业务场景说明。";
            case REQUESTS_STEP_BY_STEP -> "用户需要分步指引，按照 1、2、3 清晰步骤结构化展开。";
            case EXPRESSES_CONFUSION -> "用户感到困惑或未理解，必须降低认知门槛，用通俗直白的方式重新阐释。";
            case CHALLENGES_PREVIOUS_ANSWER -> "用户指出上一轮回答有误或存在漏洞，必须坦诚面对，直接修正并给出正确答案。";
            case REPORTS_ERROR -> "用户提供了报错信息，优先定位根因并直接给出修复后方案。";
            case EXPRESSES_DISSATISFACTION -> "用户对回答模式或篇幅不满，立即调整策略直奔用户核心关注点。";
            case REQUESTS_COMPARISON -> "用户需要对比选型，采用 Markdown 表格或多维指标清晰对比优缺点与适用边界。";
            case REQUESTS_DEEP_DIVE -> "用户探究深水区原理，深入底层架构、内存模型、高并发或内核机制剖析。";
            case POSITIVE_FEEDBACK -> "用户对既往输出表示肯定，保持高效专业，做好后续承接准备。";
        };
    }
}
