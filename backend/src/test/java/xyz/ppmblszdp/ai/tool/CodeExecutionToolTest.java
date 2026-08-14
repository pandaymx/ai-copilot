package xyz.ppmblszdp.ai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import reactor.core.publisher.Sinks;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.dto.ChatChunkDto;
import xyz.ppmblszdp.ai.service.CodeExecutionService;

class CodeExecutionToolTest {

    private CodeExecutionTool codeExecutionTool;
    private CodeExecutionService codeExecutionService;
    private ToolContext toolContext;
    private Sinks.Many<ChatChunkDto> sink;

    @BeforeEach
    void setUp() {
        codeExecutionService = mock(CodeExecutionService.class);
        codeExecutionTool = new CodeExecutionTool(codeExecutionService);

        AiProviderProperties props = mock(AiProviderProperties.class);
        AiProviderProperties.AgentConfig agentConfig = mock(AiProviderProperties.AgentConfig.class);
        when(props.resolveAgent()).thenReturn(agentConfig);
        when(agentConfig.resolveMaxToolCalls()).thenReturn(5);
        when(agentConfig.resolveTimeoutSeconds()).thenReturn(30);

        ToolEventEmitter emitter = new ToolEventEmitter(props);
        sink = emitter.newSink();

        Map<String, Object> ctxMap = new HashMap<>();
        ctxMap.put(ToolEventEmitter.CTX_EMITTER, emitter);
        ctxMap.put("eventSink", sink);
        toolContext = new ToolContext(ctxMap);
    }

    @Test
    @DisplayName("executeCode 应该正确调用 CodeExecutionService 并发出 tool_call 与 tool_result 事件帧")
    void shouldExecuteCodeAndEmitSseEvents() {
        CodeExecutionService.ExecutionResponse mockResponse = new CodeExecutionService.ExecutionResponse(
                "success", "python", "docker", 0, "Hello World\n", "", 120L, List.of(), false);
        when(codeExecutionService.execute(anyString(), anyString())).thenReturn(mockResponse);

        String resultJson = codeExecutionTool.executeCode("python", "print('Hello World')", toolContext);

        assertThat(resultJson).contains("Hello World");
        assertThat(resultJson).contains("\"status\":\"success\"");

        // 验证 tool_call 帧
        List<ChatChunkDto> events = sink.asFlux().take(2).collectList().block();
        assertThat(events).isNotNull().hasSize(2);

        ChatChunkDto callFrame = events.get(0);
        assertThat(callFrame.type()).isEqualTo("tool_call");
        assertThat(callFrame.toolName()).isEqualTo("code_execution");
        assertThat(callFrame.arguments()).contains("print('Hello World')");

        ChatChunkDto resultFrame = events.get(1);
        assertThat(resultFrame.type()).isEqualTo("tool_result");
        assertThat(resultFrame.toolName()).isEqualTo("code_execution");
        assertThat(resultFrame.isError()).isFalse();
        assertThat(resultFrame.result()).contains("Hello World");
    }
}
