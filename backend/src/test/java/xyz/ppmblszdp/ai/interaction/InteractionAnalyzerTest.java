package xyz.ppmblszdp.ai.interaction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InteractionAnalyzerTest {

    private InteractionAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new InteractionAnalyzer();
    }

    @Test
    @DisplayName("强指令与代码优先：'说了半天废话，直接给我一段 Java 代码'")
    void testDirectiveAndCodeFirst() {
        String msg = "说了半天废话，直接给我一段 Java 代码";
        InteractionAnalysis result = analyzer.analyze(msg);

        assertThat(result.state()).isIn(InteractionState.DISSATISFIED, InteractionState.DIRECTIVE);
        assertThat(result.signals())
                .contains(
                        InteractionSignal.EXPRESSES_DISSATISFACTION,
                        InteractionSignal.REQUESTS_CONCISION,
                        InteractionSignal.REQUESTS_DIRECT_ANSWER,
                        InteractionSignal.REQUESTS_CODE);
        assertThat(result.strategies())
                .contains(ResponseStrategy.CONCISE, ResponseStrategy.DIRECT_ANSWER, ResponseStrategy.CODE_FIRST);

        String promptPolicy = InteractionPromptPolicy.buildSystemPromptPolicy(result);
        assertThat(promptPolicy).contains("<interaction_policy>");
        assertThat(promptPolicy).contains("REQUESTS_CONCISION");
        assertThat(promptPolicy).contains("CODE_FIRST");
    }

    @Test
    @DisplayName("困惑不解与示例分步拆解：'这是什么意思？没看懂，给个例子分步解释'")
    void testConfusionAndExplanation() {
        String msg = "这是什么意思？没看懂，给个例子分步解释";
        InteractionAnalysis result = analyzer.analyze(msg);

        assertThat(result.state()).isEqualTo(InteractionState.CONFUSED);
        assertThat(result.signals())
                .contains(
                        InteractionSignal.EXPRESSES_CONFUSION,
                        InteractionSignal.REQUESTS_EXAMPLE,
                        InteractionSignal.REQUESTS_STEP_BY_STEP,
                        InteractionSignal.REQUESTS_EXPLANATION);
        assertThat(result.strategies())
                .contains(ResponseStrategy.EXPLANATORY, ResponseStrategy.STEP_BY_STEP, ResponseStrategy.EXAMPLE_FIRST);

        String promptPolicy = InteractionPromptPolicy.buildSystemPromptPolicy(result);
        assertThat(promptPolicy).contains("CONFUSED");
        assertThat(promptPolicy).contains("EXPLANATORY");
    }

    @Test
    @DisplayName("纠错与报错反馈：'你这个答案完全不对，报错 NullPointerException 了，重新给可执行代码'")
    void testCorrectionRequired() {
        String msg = "你这个答案完全不对，报错 NullPointerException 了，重新给可执行代码";
        InteractionAnalysis result = analyzer.analyze(msg);

        assertThat(result.state()).isEqualTo(InteractionState.CORRECTION_REQUIRED);
        assertThat(result.signals())
                .contains(
                        InteractionSignal.CHALLENGES_PREVIOUS_ANSWER,
                        InteractionSignal.REPORTS_ERROR,
                        InteractionSignal.REQUESTS_CODE);
        assertThat(result.strategies())
                .contains(
                        ResponseStrategy.CORRECT_PREVIOUS_ANSWER,
                        ResponseStrategy.DIRECT_ANSWER,
                        ResponseStrategy.CODE_FIRST);

        String promptPolicy = InteractionPromptPolicy.buildSystemPromptPolicy(result);
        assertThat(promptPolicy).contains("CORRECTION_REQUIRED");
        assertThat(promptPolicy).contains("CORRECT_PREVIOUS_ANSWER");
    }

    @Test
    @DisplayName("深度探究：'请深度剖析一下 JVM 底层原理与内存模型'")
    void testDeepDive() {
        String msg = "请深度剖析一下 JVM 底层原理与内存模型";
        InteractionAnalysis result = analyzer.analyze(msg);

        assertThat(result.state()).isEqualTo(InteractionState.DEEP_DIVE);
        assertThat(result.signals()).contains(InteractionSignal.REQUESTS_DEEP_DIVE);
        assertThat(result.strategies()).contains(ResponseStrategy.DEEP_ANALYSIS);
    }

    @Test
    @DisplayName("方案对比：'对比一下 MySQL 和 PostgreSQL 的优缺点与技术选型权衡'")
    void testComparison() {
        String msg = "对比一下 MySQL 和 PostgreSQL 的优缺点与技术选型权衡";
        InteractionAnalysis result = analyzer.analyze(msg);

        assertThat(result.state()).isEqualTo(InteractionState.EXPLORATORY);
        assertThat(result.signals()).contains(InteractionSignal.REQUESTS_COMPARISON);
        assertThat(result.strategies()).contains(ResponseStrategy.COMPARATIVE);
    }

    @Test
    @DisplayName("正面确认：'太棒了，完美解决了我的问题，非常感谢！'")
    void testPositiveFeedback() {
        String msg = "太棒了，完美解决了我的问题，非常感谢！";
        InteractionAnalysis result = analyzer.analyze(msg);

        assertThat(result.state()).isEqualTo(InteractionState.POSITIVE);
        assertThat(result.signals()).contains(InteractionSignal.POSITIVE_FEEDBACK);
    }

    @Test
    @DisplayName("中性常规查询与空输入兜底")
    void testNeutralFallback() {
        InteractionAnalysis emptyResult = analyzer.analyze("");
        assertThat(emptyResult.state()).isEqualTo(InteractionState.NEUTRAL);
        assertThat(emptyResult.strategies()).containsExactly(ResponseStrategy.DEFAULT);
        assertThat(InteractionPromptPolicy.buildSystemPromptPolicy(emptyResult)).isEmpty();

        InteractionAnalysis neutralMsg = analyzer.analyze("今天天气怎么样？");
        assertThat(neutralMsg.state()).isEqualTo(InteractionState.NEUTRAL);
    }
}
