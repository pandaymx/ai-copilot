package xyz.ppmblszdp.ai.clarification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

class ClarificationEngineTest {

    private ClarificationProperties properties;
    private ClarificationEngine engine;

    @BeforeEach
    void setUp() {
        properties = new ClarificationProperties();
        properties.setEnabled(true);
        properties.setDefaultMode(ClarificationMode.SOFT);
        properties.setAgentMode(ClarificationMode.STRICT);
        engine = new ClarificationEngine(properties);
    }

    @Test
    @DisplayName("代码生成场景模糊提问识别 - 成功捕获并生成缺失要素")
    void testCodeAmbiguousQuestion() {
        ClarificationAssessment assessment = engine.evaluate("帮我写个脚本", List.of(), ClarificationMode.STRICT, false);

        assertThat(assessment.isAmbiguous()).isTrue();
        assertThat(assessment.mode()).isEqualTo(ClarificationMode.STRICT);
        assertThat(assessment.category()).isEqualTo("CODE_GENERATION");
        assertThat(assessment.missingAspects()).isNotEmpty();
        assertThat(assessment.missingAspects().get(0)).contains("目标编程语言");
        assertThat(assessment.clarificationMessage()).contains("为了更准确地为您提供高质量解答");
        assertThat(assessment.clarificationMessage()).contains(ClarificationAssessment.CLARIFICATION_MARKER);
    }

    @Test
    @DisplayName("报错排查场景模糊提问识别 - 成功捕获")
    void testErrorAmbiguousQuestion() {
        ClarificationAssessment assessment = engine.evaluate("报错了怎么解决", List.of(), ClarificationMode.STRICT, false);

        assertThat(assessment.isAmbiguous()).isTrue();
        assertThat(assessment.category()).isEqualTo("DEBUGGING_ERROR");
        assertThat(assessment.missingAspects()).isNotEmpty();
        assertThat(assessment.missingAspects().get(0)).contains("报错信息");
    }

    @Test
    @DisplayName("性能优化场景模糊提问识别 - 成功捕获")
    void testOptimizationAmbiguousQuestion() {
        ClarificationAssessment assessment = engine.evaluate("帮我优化一下", List.of(), ClarificationMode.SOFT, false);

        assertThat(assessment.isAmbiguous()).isTrue();
        assertThat(assessment.category()).isEqualTo("PERFORMANCE_OPTIMIZATION");
        assertThat(assessment.mode()).isEqualTo(ClarificationMode.SOFT);
    }

    @Test
    @DisplayName("部署运维场景模糊提问识别 - 成功捕获")
    void testDeployAmbiguousQuestion() {
        ClarificationAssessment assessment = engine.evaluate("怎么部署", List.of(), null, true); // isAgent = true

        assertThat(assessment.isAmbiguous()).isTrue();
        assertThat(assessment.category()).isEqualTo("DEPLOYMENT_OPS");
        assertThat(assessment.mode()).isEqualTo(ClarificationMode.STRICT); // agentMode default is STRICT
    }

    @Test
    @DisplayName("防循环追问死锁 (Anti-Clarification Loop) - 当用户正在回答上一轮澄清提问时无条件放行")
    void testAntiClarificationLoop() {
        // 上一轮 Assistant 给出了主动澄清提示
        AssistantMessage prevAssistantMessage = new AssistantMessage(
                "为了更准确地为您提供高质量解答，我需要向您先确认以下关键信息：\n1. 目标语言\n" + ClarificationAssessment.CLARIFICATION_MARKER);

        // 用户只回复了一个简短词 "Java"
        ClarificationAssessment assessment = engine.evaluate(
                "Java", List.of(new UserMessage("帮我写个脚本"), prevAssistantMessage), ClarificationMode.STRICT, false);

        // 必须直接放行，不能再次拦截造成死锁循环
        assertThat(assessment.isAmbiguous()).isFalse();
        assertThat(assessment.category()).isEqualTo("CLEAR");
    }

    @Test
    @DisplayName("明确且长文本提问 - 自动放行不拦截")
    void testClearDetailedQuestion() {
        String detailedPrompt = "请用 Python 3.11 和 Pandas 读取 data.csv 文件，按 user_id 分组计算交易总额，并输出前 10 名用户。";
        ClarificationAssessment assessment =
                engine.evaluate(detailedPrompt, List.of(), ClarificationMode.STRICT, false);

        assertThat(assessment.isAmbiguous()).isFalse();
    }

    @Test
    @DisplayName("显式斜杠命令 - 自动放行不拦截")
    void testSlashCommandsBypass() {
        ClarificationAssessment assessment =
                engine.evaluate("/code 实现快速排序", List.of(), ClarificationMode.STRICT, false);

        assertThat(assessment.isAmbiguous()).isFalse();
    }

    @Test
    @DisplayName("全局关闭功能 - 自动放行")
    void testDisabledFeature() {
        properties.setEnabled(false);
        ClarificationAssessment assessment = engine.evaluate("帮我写个脚本", List.of(), ClarificationMode.STRICT, false);

        assertThat(assessment.isAmbiguous()).isFalse();
    }
}
