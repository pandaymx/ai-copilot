package xyz.ppmblszdp.ai.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import reactor.core.publisher.Sinks;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.dto.ChatChunkDto;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AugmentedToolCallbackTest {

	private AugmentedToolCallbackProvider provider;

	@BeforeEach
	void setUp() {
		provider = new AugmentedToolCallbackProvider();
	}

	@Test
	@DisplayName("JSON Schema 应该被注入 innerThought 字段及其 required 强约束")
	void testAugmentSchema() {
		ToolCallback delegate = mock(ToolCallback.class);
		ToolDefinition origDef = ToolDefinition.builder()
				.name("calculator")
				.description("Math Calc")
				.inputSchema("{\"type\":\"object\",\"properties\":{\"expression\":{\"type\":\"string\"}},\"required\":[\"expression\"]}")
				.build();
		when(delegate.getToolDefinition()).thenReturn(origDef);

		ToolCallback augmented = provider.wrap(delegate);
		ToolDefinition augmentedDef = augmented.getToolDefinition();

		assertThat(augmentedDef.name()).isEqualTo("calculator");
		String schema = augmentedDef.inputSchema();
		assertThat(schema).contains("innerThought");
		assertThat(schema).contains("思考过程");
		assertThat(schema).contains("\"required\":[\"expression\",\"innerThought\"]");
	}

	@Test
	@DisplayName("执行 call 时应提取 innerThought 到 ToolContext，并剥离后传给底层 delegate")
	void testCallExtractsInnerThought() {
		ToolCallback delegate = mock(ToolCallback.class);
		ToolDefinition origDef = ToolDefinition.builder()
				.name("test_tool")
				.description("test")
				.inputSchema("{\"type\":\"object\",\"properties\":{}}")
				.build();
		when(delegate.getToolDefinition()).thenReturn(origDef);
		when(delegate.call(anyString(), any())).thenAnswer(inv -> {
			ToolContext tc = inv.getArgument(1);
			assertThat(AugmentedToolCallbackProvider.getInnerThought(tc)).isEqualTo("先计算1加1");
			return "{\"output\":2.0}";
		});

		ToolCallback augmented = provider.wrap(delegate);
		Map<String, Object> ctxMap = new HashMap<>();
		ToolContext toolContext = new ToolContext(ctxMap);

		String inputJson = "{\"innerThought\":\"先计算1加1\",\"expression\":\"1+1\"}";
		String result = augmented.call(inputJson, toolContext);

		assertThat(result).isEqualTo("{\"output\":2.0}");
		verify(delegate).call(anyString(), eq(toolContext));
	}

	@Test
	@DisplayName("非严格 JSON 防御：当 toolInput 为非标准 JSON 时，应优雅降级将原文传给 delegate，不阻断调用")
	void testCallFallbackOnMalformedJson() {
		ToolCallback delegate = mock(ToolCallback.class);
		ToolDefinition origDef = ToolDefinition.builder()
				.name("test_tool")
				.description("test")
				.inputSchema("{\"type\":\"object\",\"properties\":{}}")
				.build();
		when(delegate.getToolDefinition()).thenReturn(origDef);

		String malformedInput = "{innerThought: unquoted, expression: 1+1";
		when(delegate.call(eq(malformedInput), any())).thenReturn("{\"output\":\"ok\"}");

		ToolCallback augmented = provider.wrap(delegate);
		Map<String, Object> ctxMap = new HashMap<>();
		ToolContext toolContext = new ToolContext(ctxMap);

		String result = augmented.call(malformedInput, toolContext);

		assertThat(result).isEqualTo("{\"output\":\"ok\"}");
		verify(delegate).call(eq(malformedInput), eq(toolContext));
	}

	@Test
	@DisplayName("ToolEventEmitter 应该从 ToolContext 提取 innerThought 并合并到 tool_call SSE 帧中")
	void testToolEventEmitterMergesInnerThought() {
		AiProviderProperties props = mock(AiProviderProperties.class);
		AiProviderProperties.AgentConfig agentConfig = mock(AiProviderProperties.AgentConfig.class);
		when(props.resolveAgent()).thenReturn(agentConfig);
		when(agentConfig.resolveMaxToolCalls()).thenReturn(5);
		when(agentConfig.resolveTimeoutSeconds()).thenReturn(30);

		ToolEventEmitter emitter = new ToolEventEmitter(props);
		Sinks.Many<ChatChunkDto> sink = emitter.newSink();

		Map<String, Object> ctxMap = new HashMap<>();
		ctxMap.put("eventSink", sink);
		ctxMap.put(AugmentedToolCallbackProvider.INNER_THOUGHT_KEY, "尝试发送 HTTP 请求");
		ToolContext toolContext = new ToolContext(ctxMap);

		emitter.executeWithEvent("http_request", "{\"url\":\"https://example.com\"}", toolContext, () -> "{\"status\":200}");

		ChatChunkDto chunk = sink.asFlux().blockFirst();
		assertThat(chunk).isNotNull();
		assertThat(chunk.type()).isEqualTo("tool_call");
		assertThat(chunk.toolName()).isEqualTo("http_request");
		assertThat(chunk.arguments()).contains("innerThought");
		assertThat(chunk.arguments()).contains("尝试发送 HTTP 请求");
		assertThat(chunk.arguments()).contains("https://example.com");
	}

	@Test
	@DisplayName("wrapTools 应对本地工具强制包裹，且当 augmentMcp 为 true 时才包裹 MCP 工具")
	void testWrapToolsWithMcpOption() {
		ToolCallback local = mock(ToolCallback.class);
		ToolDefinition localDef = ToolDefinition.builder().name("local").description("local").inputSchema("{}").build();
		when(local.getToolDefinition()).thenReturn(localDef);

		ToolCallback mcp = mock(ToolCallback.class);
		ToolDefinition mcpDef = ToolDefinition.builder().name("mcp").description("mcp").inputSchema("{}").build();
		when(mcp.getToolDefinition()).thenReturn(mcpDef);

		// augmentMcp = false
		ToolCallback[] mergedWithoutMcp = provider.wrapTools(new ToolCallback[]{ local }, new ToolCallback[]{ mcp }, false);
		assertThat(mergedWithoutMcp).hasSize(2);
		assertThat(mergedWithoutMcp[0]).isInstanceOf(AugmentedToolCallbackProvider.AugmentedToolCallback.class);
		assertThat(mergedWithoutMcp[1]).isNotInstanceOf(AugmentedToolCallbackProvider.AugmentedToolCallback.class);

		// augmentMcp = true
		ToolCallback[] mergedWithMcp = provider.wrapTools(new ToolCallback[]{ local }, new ToolCallback[]{ mcp }, true);
		assertThat(mergedWithMcp).hasSize(2);
		assertThat(mergedWithMcp[0]).isInstanceOf(AugmentedToolCallbackProvider.AugmentedToolCallback.class);
		assertThat(mergedWithMcp[1]).isInstanceOf(AugmentedToolCallbackProvider.AugmentedToolCallback.class);
	}
}
