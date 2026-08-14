package xyz.ppmblszdp.ai.agent.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import xyz.ppmblszdp.ai.agent.plan.dto.TaskPlanDto;
import xyz.ppmblszdp.ai.agent.plan.dto.TaskStepDto;

class TaskPlannerTest {

    private TaskPlanner taskPlanner;
    private ChatClient mockChatClient;

    @BeforeEach
    void setUp() {
        taskPlanner = new TaskPlanner();
        mockChatClient = mock(ChatClient.class);
    }

    @Test
    void generatePlan_shouldParseLlmJsonProperly() {
        String mockJsonResponse = """
                ```json
                {
                  "title": "代码库多模块重构",
                  "steps": [
                    {
                      "stepId": 1,
                      "title": "克隆代码",
                      "description": "浅克隆主仓库",
                      "toolName": "git_clone",
                      "expectedOutput": "本地完成克隆"
                    },
                    {
                      "stepId": 2,
                      "title": "查找废弃接口",
                      "description": "正则检索 @Deprecated 符号",
                      "toolName": "code_find_symbols",
                      "expectedOutput": "提取所有符号列表"
                    }
                  ]
                }
                ```
                """;

        ChatClient.ChatClientRequestSpec reqSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);

        when(mockChatClient.prompt(any(Prompt.class))).thenReturn(reqSpec);
        when(reqSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(mockJsonResponse);

        TaskPlanDto plan = taskPlanner.generatePlan("重构废弃接口", null, "git_clone, code_find_symbols", mockChatClient);

        assertThat(plan).isNotNull();
        assertThat(plan.title()).isEqualTo("代码库多模块重构");
        assertThat(plan.steps()).hasSize(2);
        assertThat(plan.steps().get(0).toolName()).isEqualTo("git_clone");
        assertThat(plan.steps().get(1).toolName()).isEqualTo("code_find_symbols");
    }

    @Test
    void replan_shouldAdaptAndModifyRemainingSteps() {
        TaskStepDto s1 = TaskStepDto.pending(1, "克隆", "克隆成功", "git_clone", "完成")
                .withObservation("Cloned", true, null);
        TaskStepDto s2 = TaskStepDto.pending(2, "搜索代码", "执行正则搜索", "code_search_regex", "匹配文件");

        TaskPlanDto initialPlan = TaskPlanDto.of("plan_1", "原计划", "目标", List.of(s1, s2));

        String mockReplanJson = """
                {
                  "title": "调整为语义搜索",
                  "newSteps": [
                    {
                      "stepId": 2,
                      "title": "降级为语义代码搜索",
                      "description": "换用 code_search_semantic",
                      "toolName": "code_search_semantic",
                      "expectedOutput": "找到目标代码块"
                    }
                  ]
                }
                """;

        ChatClient.ChatClientRequestSpec reqSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);

        when(mockChatClient.prompt(any(Prompt.class))).thenReturn(reqSpec);
        when(reqSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(mockReplanJson);

        TaskPlanDto newPlan = taskPlanner.replan(
                initialPlan,
                2,
                "正则未匹配到任何文件",
                0,
                "code_search_semantic",
                mockChatClient
        );

        assertThat(newPlan).isNotNull();
        assertThat(newPlan.status()).isEqualTo("REPLANNING");
        assertThat(newPlan.steps()).hasSize(2);
        assertThat(newPlan.steps().get(1).toolName()).isEqualTo("code_search_semantic");
        assertThat(newPlan.steps().get(1).replanCount()).isEqualTo(1);
    }

    @Test
    void replan_shouldTriggerAntiLooping_whenLimitsExceeded() {
        TaskStepDto s1 = TaskStepDto.pending(1, "重复失败步骤", "描述", "tool_a", "完成")
                .incrementReplan("retry 1", "tool_a")
                .incrementReplan("retry 2", "tool_a"); // replanCount = 2

        TaskPlanDto initialPlan = TaskPlanDto.of("plan_1", "原计划", "目标", List.of(s1));

        // 当该步 replanCount >= 2 时，直接熔断跳过，不应继续调用 LLM
        TaskPlanDto plan = taskPlanner.replan(
                initialPlan,
                1,
                "持续超时",
                2,
                "tool_a",
                mockChatClient
        );

        assertThat(plan).isNotNull();
        assertThat(plan.steps().get(0).status()).isEqualTo("FAILED");
        assertThat(plan.steps()).anyMatch(s -> s.title().contains("异常降级总结"));
    }
}
