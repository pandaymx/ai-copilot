package xyz.ppmblszdp.ai.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.config.AiProviderProperties.AgentConfig;
import xyz.ppmblszdp.ai.dto.ChatChunkDto;

import xyz.ppmblszdp.ai.registry.ModelDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;
import xyz.ppmblszdp.ai.tool.ToolEventEmitter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * WorkerAgentExecutor 单元测试：验证帧发射正确性和深度限制保护。
 */
class WorkerAgentExecutorTest {

    private ProviderRegistry registry;
    private AiProviderProperties properties;
    private ToolEventEmitter toolEventEmitter;
    private WorkerAgentExecutor executor;

    @BeforeEach
    void setUp() {
        registry = mock(ProviderRegistry.class);
        properties = mock(AiProviderProperties.class);
        toolEventEmitter = mock(ToolEventEmitter.class);

        AgentConfig agentConfig = mock(AgentConfig.class);
        when(agentConfig.resolveMaxWorkerDepth()).thenReturn(1);
        when(agentConfig.resolveWorkerMaxTokens()).thenReturn(2048);
        when(agentConfig.resolveWorkerProvider()).thenReturn(null);
        when(agentConfig.resolveWorkerModel()).thenReturn(null);
        when(properties.resolveAgent()).thenReturn(agentConfig);
        when(toolEventEmitter.toolTimeout()).thenReturn(Duration.ofSeconds(30));

        executor = new WorkerAgentExecutor(registry, properties, toolEventEmitter);
    }

    @Test
    void execute_successPath_emitsToolCallAndToolResultFrames() throws Exception {
        // Arrange: Mock ChatModel.call(Prompt) returning "分析完成"
        Sinks.Many<ChatChunkDto> sink = Sinks.many().multicast().onBackpressureBuffer();
        List<ChatChunkDto> emitted = new ArrayList<>();
        sink.asFlux().subscribe(emitted::add);

        // WorkerAgentExecutor uses resolved.chatModel().call(prompt)
        ChatModel mockChatModel = mock(ChatModel.class);
        ChatResponse mockResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        AssistantMessage mockOutput = new AssistantMessage("分析完成：增长率 15%");

        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(mockGeneration.getOutput()).thenReturn(mockOutput);
        when(mockChatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(mockResponse);

        ModelDescriptor modelDesc = mock(ModelDescriptor.class);
        when(modelDesc.id()).thenReturn("test-model");
        when(modelDesc.modelName()).thenReturn("test-model");
        ProviderDescriptor provDesc = mock(ProviderDescriptor.class);
        when(provDesc.providerId()).thenReturn("test-provider");

        ResolvedModel resolved = mock(ResolvedModel.class);
        when(resolved.chatModel()).thenReturn(mockChatModel);
        when(resolved.model()).thenReturn(modelDesc);
        when(resolved.provider()).thenReturn(provDesc);
        when(registry.resolve(isNull(), isNull())).thenReturn(resolved);

        // depth=1 (= maxDepth), 不超限
        SubAgentWorkerContext ctx = new SubAgentWorkerContext(sink, "user1", "conv1", 1);

        // Act
        String result = executor.execute("analysis", "分析用户增长趋势", "你是分析助手。", ctx);

        // Assert: 结果正确返回
        assertTrue(result.contains("分析完成") || result.contains("15%"),
                "Worker 应返回 LLM 生成内容，实际: " + result);

        // 等待异步 Sink 推送
        Thread.sleep(200);

        // 应该至少有 tool_call 和 tool_result 两帧
        assertTrue(emitted.size() >= 2, "应发射至少 tool_call + tool_result 2 帧，实际发射: " + emitted.size());

        ChatChunkDto callFrame = emitted.stream()
                .filter(c -> "tool_call".equals(c.type()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少 tool_call 帧"));
        assertEquals("sub_agent:analysis", callFrame.toolName());
        assertNotNull(callFrame.toolCallId());
        assertNotNull(callFrame.arguments());

        ChatChunkDto resultFrame = emitted.stream()
                .filter(c -> "tool_result".equals(c.type()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少 tool_result 帧"));
        assertEquals("sub_agent:analysis", resultFrame.toolName());
        assertFalse(Boolean.TRUE.equals(resultFrame.isError()), "成功路径 isError 应为 false");
    }

    @Test
    void execute_depthExceeded_emitsErrorFrame() throws Exception {
        // Arrange: depth=2，超过 maxWorkerDepth=1
        Many<ChatChunkDto> sink = Sinks.many().multicast().onBackpressureBuffer();
        List<ChatChunkDto> emitted = new ArrayList<>();
        sink.asFlux().subscribe(emitted::add);

        SubAgentWorkerContext ctx = new SubAgentWorkerContext(sink, "user1", "conv1", 2);

        // Act
        String result = executor.execute("code", "生成一个排序算法", "你是代码助手。", ctx);

        // Assert: 结果应包含深度超限提示
        assertTrue(result.contains("超过上限") || result.contains("拒绝派发") || result.contains("递归"),
                "深度超限时应返回错误提示，实际: " + result);

        // 等待 Sink 推送
        Thread.sleep(100);

        // 应该发射了 tool_call + isError=true 的 tool_result
        assertTrue(emitted.size() >= 2, "深度超限也应发射帧，实际: " + emitted.size());
        boolean hasErrorResult = emitted.stream()
                .anyMatch(c -> "tool_result".equals(c.type()) && Boolean.TRUE.equals(c.isError()));
        assertTrue(hasErrorResult, "深度超限应发射 isError=true 的 tool_result 帧");
    }

    @Test
    void workerCtx_incrementDepth_createsNewInstanceWithDepthPlusOne() {
        Many<ChatChunkDto> sink = Sinks.many().multicast().onBackpressureBuffer();
        SubAgentWorkerContext ctx = new SubAgentWorkerContext(sink, "u1", "c1", 0);
        SubAgentWorkerContext incremented = ctx.incrementDepth();

        assertEquals(0, ctx.depth(), "原始 ctx depth 应为 0");
        assertEquals(1, incremented.depth(), "incrementDepth 后 depth 应为 1");
        assertSame(ctx.eventSink(), incremented.eventSink(), "Sink 应相同引用");
        assertEquals("u1", incremented.userId());
        assertEquals("c1", incremented.conversationId());
    }
}
