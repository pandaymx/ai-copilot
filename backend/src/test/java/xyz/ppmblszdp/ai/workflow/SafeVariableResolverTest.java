package xyz.ppmblszdp.ai.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SafeVariableResolverTest {

    @Test
    @DisplayName("应该正确替换 input 与 nodes 简单变量")
    void testResolveSimpleVariables() {
        Map<String, Object> inputs = Map.of("query", "Spring AI", "author", "Alice");
        Map<String, Object> nodeOutputs = Map.of("step1", "检索结果数据");

        String template = "主题是 ${input.query}，作者是 ${input.author}，上游输出为：${nodes.step1.output}";
        String resolved = SafeVariableResolver.resolveTemplate(template, inputs, nodeOutputs);

        assertThat(resolved).isEqualTo("主题是 Spring AI，作者是 Alice，上游输出为：检索结果数据");
    }

    @Test
    @DisplayName("应该支持提取嵌套 JSON 对象的字段")
    void testResolveNestedProperties() {
        Map<String, Object> inputs = Map.of("user", Map.of("profile", Map.of("name", "Bob", "role", "admin")));
        Map<String, Object> nodeOutputs = Map.of("calc", "{\"score\": 95, \"details\": {\"level\": \"A+\"}}");

        String t1 = "用户角色: ${input.user.profile.role}";
        String t2 = "得分: ${nodes.calc.output.score}, 等级: ${nodes.calc.output.details.level}";

        assertThat(SafeVariableResolver.resolveTemplate(t1, inputs, nodeOutputs))
                .isEqualTo("用户角色: admin");
        assertThat(SafeVariableResolver.resolveTemplate(t2, inputs, nodeOutputs))
                .isEqualTo("得分: 95, 等级: A+");
    }

    @Test
    @DisplayName("应该正确求值各类安全条件表达式")
    void testEvaluateCondition() {
        Map<String, Object> inputs = Map.of("score", 85, "status", "active");
        Map<String, Object> nodeOutputs = Map.of("audit", "AUDIT_PASS: 文案合规", "calc", 100);

        assertThat(SafeVariableResolver.evaluateCondition("${input.score} > 80", inputs, nodeOutputs))
                .isTrue();
        assertThat(SafeVariableResolver.evaluateCondition("${input.score} < 60", inputs, nodeOutputs))
                .isFalse();
        assertThat(SafeVariableResolver.evaluateCondition(
                        "${nodes.audit.output}.contains('AUDIT_PASS')", inputs, nodeOutputs))
                .isTrue();
        assertThat(SafeVariableResolver.evaluateCondition(
                        "${nodes.audit.output}.contains('AUDIT_FAIL')", inputs, nodeOutputs))
                .isFalse();
        assertThat(SafeVariableResolver.evaluateCondition("${input.status} == 'active'", inputs, nodeOutputs))
                .isTrue();
        assertThat(SafeVariableResolver.evaluateCondition("${input.status} != 'inactive'", inputs, nodeOutputs))
                .isTrue();
    }
}
