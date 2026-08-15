package xyz.ppmblszdp.ai.interaction;

import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 高性能交互状态理解与信号提取引擎（0 LLM 调用，亚毫秒级正则与多维度特征提取）。
 */
@Service
public class InteractionAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(InteractionAnalyzer.class);

    private final ResponseStrategyResolver resolver = new ResponseStrategyResolver();

    // 预编译各维度正交信号特征正则
    private static final Pattern CONCISION_PATTERN =
            Pattern.compile("(?i)(说重点|废话|太长了|简短点|精简|太啰嗦|别废话|不要解释|少废话|太复杂了|简单点说|言简意赅|压缩一下|只要结果)");

    private static final Pattern DIRECT_ANSWER_PATTERN =
            Pattern.compile("(?i)(直接给|直接告诉|别铺垫|开门见山|直接说|直接给答案|别绕弯子|直接写|结论是)");

    private static final Pattern CODE_PATTERN =
            Pattern.compile("(?i)(写.*代码|给.*代码|代码实现|Java代码|Python代码|SQL代码|可执行|写个脚本|Demo代码|完整代码|```|函数实现|写个程序)");

    private static final Pattern CONFUSION_PATTERN =
            Pattern.compile("(?i)(什么意思|没看懂|没听懂|不是很理解|不是很懂|太抽象|看不明白|看不懂|到底是什么|怎么回事|\\?{2,}|？{2,})");

    private static final Pattern CHALLENGE_PATTERN =
            Pattern.compile("(?i)(不对|你这不对|回答有误|算错了|胡说|不正确|写错了|理解反了|逻辑不通|有bug|有漏洞|完全不对|瞎扯|搞反了|并不能运行)");

    private static final Pattern ERROR_REPORT_PATTERN = Pattern.compile(
            "(?i)(报错|异常|编译失败|NullPointerException|Exception|Error|failed|stacktrace|404|500|401|出错了|崩溃|无法运行|报这个错)");

    private static final Pattern DISSATISFACTION_PATTERN =
            Pattern.compile("(?i)(答非所问|跑题|没解决|什么垃圾|差劲|根本不行|浪费时间|烦死了|体验太差|说了半天)");

    private static final Pattern EXPLANATION_PATTERN = Pattern.compile("(?i)(解释|原理是什么|讲讲|详细说说|怎么理解|原理是|为啥|阐释|展开说说)");

    private static final Pattern EXAMPLE_PATTERN = Pattern.compile("(?i)(举个例子|给个例子|举例说明|来个案例|Demo|示例代码|样例|打个比方)");

    private static final Pattern STEP_BY_STEP_PATTERN = Pattern.compile("(?i)(分步|一步一步|步骤|分阶段|按流程|教程|手把手|按顺序)");

    private static final Pattern COMPARISON_PATTERN = Pattern.compile("(?i)(对比|区别|优缺点|哪个好|选型|优劣|异同|权衡|各有什么)");

    private static final Pattern DEEP_DIVE_PATTERN =
            Pattern.compile("(?i)(底层原理|内核机制|源码实现|内存模型|JVM|底层设计|深度剖析|高并发深水区|架构全貌)");

    private static final Pattern POSITIVE_PATTERN =
            Pattern.compile("(?i)(太棒了|完美|牛逼|太牛了|谢谢|好评|解决了|搞定了|厉害|点赞|赞|很清晰|非常感谢)");

    /**
     * 分析用户输入消息并结合对话上下文决议交互状态与响应策略。
     *
     * @param message 用户输入的最新文本
     * @return 交互状态理解分析结果
     */
    public InteractionAnalysis analyze(String message) {
        if (message == null || message.isBlank()) {
            return InteractionAnalysis.neutral();
        }

        String text = message.trim();
        Set<InteractionSignal> signals = EnumSet.noneOf(InteractionSignal.class);

        if (CONCISION_PATTERN.matcher(text).find()) {
            signals.add(InteractionSignal.REQUESTS_CONCISION);
        }
        if (DIRECT_ANSWER_PATTERN.matcher(text).find()) {
            signals.add(InteractionSignal.REQUESTS_DIRECT_ANSWER);
        }
        if (CODE_PATTERN.matcher(text).find()) {
            signals.add(InteractionSignal.REQUESTS_CODE);
        }
        if (CONFUSION_PATTERN.matcher(text).find()) {
            signals.add(InteractionSignal.EXPRESSES_CONFUSION);
        }
        if (CHALLENGE_PATTERN.matcher(text).find()) {
            signals.add(InteractionSignal.CHALLENGES_PREVIOUS_ANSWER);
        }
        if (ERROR_REPORT_PATTERN.matcher(text).find()) {
            signals.add(InteractionSignal.REPORTS_ERROR);
        }
        if (DISSATISFACTION_PATTERN.matcher(text).find()) {
            signals.add(InteractionSignal.EXPRESSES_DISSATISFACTION);
        }
        if (EXPLANATION_PATTERN.matcher(text).find()) {
            // 排除 "不要解释/别解释/无需解释" 等反向消歧
            if (!Pattern.compile("(?i)(不要解释|别解释|无需解释|不用解释)").matcher(text).find()) {
                signals.add(InteractionSignal.REQUESTS_EXPLANATION);
            }
        }
        if (EXAMPLE_PATTERN.matcher(text).find()) {
            signals.add(InteractionSignal.REQUESTS_EXAMPLE);
        }
        if (STEP_BY_STEP_PATTERN.matcher(text).find()) {
            signals.add(InteractionSignal.REQUESTS_STEP_BY_STEP);
        }
        if (COMPARISON_PATTERN.matcher(text).find()) {
            signals.add(InteractionSignal.REQUESTS_COMPARISON);
        }
        if (DEEP_DIVE_PATTERN.matcher(text).find()) {
            signals.add(InteractionSignal.REQUESTS_DEEP_DIVE);
        }
        if (POSITIVE_PATTERN.matcher(text).find()) {
            signals.add(InteractionSignal.POSITIVE_FEEDBACK);
        }

        // 计算置信度打分
        double confidence = signals.isEmpty() ? 1.0 : Math.min(1.0, 0.75 + signals.size() * 0.1);
        InteractionAnalysis result = resolver.resolve(signals, confidence);

        log.debug("交互状态理解完成 → 状态: {}, 信号: {}, 策略: {}", result.state(), result.signals(), result.strategies());
        return result;
    }
}
