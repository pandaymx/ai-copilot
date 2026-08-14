package xyz.ppmblszdp.ai.agent.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import reactor.core.publisher.Sinks;
import xyz.ppmblszdp.ai.agent.plan.dto.TaskPlanDto;
import xyz.ppmblszdp.ai.agent.plan.dto.TaskStepDto;
import xyz.ppmblszdp.ai.dto.ChatChunkDto;

class ReActAgentTest {

    private ReActAgent reActAgent;
    private TaskPlanner mockTaskPlanner;
    private ChatClient mockChatClient;

    @BeforeEach
    void setUp() {
        mockTaskPlanner = mock(TaskPlanner.class);
        mockChatClient = mock(ChatClient.class);
        reActAgent = new ReActAgent(mockTaskPlanner);
    }

    @Test
    void run_shouldExecuteReActLoopAndEmitFrames() {
        // 准备初始规划
        TaskStepDto step1 = TaskStepDto.pending(1, "计算数据", "调用计算器", "calculator", "计算结果");
        TaskPlanDto plan = TaskPlanDto.of("plan_test", "多步计算", "1+1 等于几", List.of(step1));
        when(mockTaskPlanner.generatePlan(any(), any(), any(), any())).thenReturn(plan);

        // 模拟 LLM Reason 决策：调用计算器工具
        String mockDecisionJson = """
                {
                  "thought": "需要计算 1+1 的值",
                  "actionType": "TOOL_CALL",
                  "toolName": "calculator",
                  "toolArgs": {"expression": "1+1"}
                }
                """;

        String mockSummary = "任务执行完毕，计算结果为 2。";

        ChatClient.ChatClientRequestSpec reqSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);

        when(mockChatClient.prompt(any(Prompt.class))).thenReturn(reqSpec);
        when(reqSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(mockDecisionJson, mockSummary);

        // 准备 mock ToolCallback
        ToolCallback mockTool = mock(ToolCallback.class);
        ToolDefinition mockDef = mock(ToolDefinition.class);
        when(mockDef.name()).thenReturn("calculator");
        when(mockDef.description()).thenReturn("数学计算器");
        when(mockTool.getToolDefinition()).thenReturn(mockDef);
        when(mockTool.call(any())).thenReturn("{\"output\":\"2\"}");

        Sinks.Many<ChatChunkDto> sink = Sinks.many().multicast().onBackpressureBuffer();
        List<ChatChunkDto> capturedChunks = new ArrayList<>();
        sink.asFlux().subscribe(capturedChunks::add);

        AtomicBoolean isAborted = new AtomicBoolean(false);

        String answer = reActAgent.run(
                "1+1 等于几",
                null,
                List.of(mockTool),
                mockChatClient,
                sink,
                isAborted,
                5
        );

        assertThat(answer).contains("计算结果为 2");
        assertThat(capturedChunks).anyMatch(c -> "task_plan".equals(c.type()));
        assertThat(capturedChunks).anyMatch(c -> "task_step".equals(c.type()));
        assertThat(capturedChunks).anyMatch(c -> "tool_call".equals(c.type()));
        assertThat(capturedChunks).anyMatch(c -> "tool_result".equals(c.type()));
    }

    @Test
    void run_shouldAbortImmediately_whenIsAbortedIsTrue() {
        TaskStepDto step1 = TaskStepDto.pending(1, "长程任务", "耗时操作", "long_tool", "完成");
        TaskPlanDto plan = TaskPlanDto.of("plan_cancel", "取消测试", "任务", List.of(step1));
        when(mockTaskPlanner.generatePlan(any(), any(), any(), any())).thenReturn(plan);

        Sinks.Many<ChatChunkDto> sink = Sinks.many().multicast().onBackpressureBuffer();
        AtomicBoolean isAborted = new AtomicBoolean(true); // 模拟用户在开始前或首轮点击取消

        String answer = reActAgent.run(
                "耗时任务",
                null,
                List.of(),
                mockChatClient,
                sink,
                isAborted,
                5
        );

        assertThat(answer).contains("取消");
    }
}
