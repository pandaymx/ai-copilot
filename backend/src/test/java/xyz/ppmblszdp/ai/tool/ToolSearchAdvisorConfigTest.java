package xyz.ppmblszdp.ai.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.ObjectProvider;
import xyz.ppmblszdp.ai.config.AiProviderProperties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ToolSearchAdvisorConfigTest {

	private AiProviderProperties properties;
	private ObjectProvider<EmbeddingModel> embeddingModelProvider;
	private ToolSearchAdvisorConfig.ToolSearchAdvisorFactory factory;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		properties = mock(AiProviderProperties.class);
		embeddingModelProvider = mock(ObjectProvider.class);

		AiProviderProperties.AgentConfig agentConfig = mock(AiProviderProperties.AgentConfig.class);
		AiProviderProperties.ToolSearchAdvisorPropertiesConfig toolSearchConfig =
				new AiProviderProperties.ToolSearchAdvisorPropertiesConfig(true, "regex", 30);

		when(agentConfig.resolveToolSearchAdvisor()).thenReturn(toolSearchConfig);
		when(properties.resolveAgent()).thenReturn(agentConfig);

		factory = new ToolSearchAdvisorConfig().toolSearchAdvisorFactory(
				properties, embeddingModelProvider, false, "regex", 30);
	}

	@Test
	void testShouldApplyWhenEnabledAndToolsCountExceedsThreshold() {
		ToolCallback[] tools = new ToolCallback[35];
		for (int i = 0; i < 35; i++) {
			ToolCallback callback = mock(ToolCallback.class);
			ToolDefinition def = mock(ToolDefinition.class);
			when(def.name()).thenReturn("tool_" + i);
			when(callback.getToolDefinition()).thenReturn(def);
			tools[i] = callback;
		}

		boolean shouldApply = factory.shouldApply(tools, 20, 15);
		assertTrue(shouldApply, "当工具总数(35) >= 阈值(30) 且开启时，shouldApply 应当返回 true");
	}

	@Test
	void testShouldApplyReturnsFalseWhenToolCountBelowThreshold() {
		ToolCallback[] tools = new ToolCallback[10];

		boolean shouldApply = factory.shouldApply(tools, 5, 5);
		assertFalse(shouldApply, "当工具总数(10) < 阈值(30) 时，shouldApply 应当返回 false");
	}

	@Test
	void testVectorIndexFallbackToRegexWhenEmbeddingModelMissing() {
		AiProviderProperties.AgentConfig agentConfig = mock(AiProviderProperties.AgentConfig.class);
		AiProviderProperties.ToolSearchAdvisorPropertiesConfig toolSearchConfig =
				new AiProviderProperties.ToolSearchAdvisorPropertiesConfig(true, "vector", 30);

		when(agentConfig.resolveToolSearchAdvisor()).thenReturn(toolSearchConfig);
		when(properties.resolveAgent()).thenReturn(agentConfig);
		when(embeddingModelProvider.getIfAvailable()).thenReturn(null);

		String resolvedType = factory.resolveIndexType();
		assertEquals("regex", resolvedType, "当 Vector 索引被配置但无可用 EmbeddingModel 时，应当降级为 regex");
	}

	@Test
	void testVectorIndexPreservedWhenEmbeddingModelAvailable() {
		AiProviderProperties.AgentConfig agentConfig = mock(AiProviderProperties.AgentConfig.class);
		AiProviderProperties.ToolSearchAdvisorPropertiesConfig toolSearchConfig =
				new AiProviderProperties.ToolSearchAdvisorPropertiesConfig(true, "vector", 30);

		when(agentConfig.resolveToolSearchAdvisor()).thenReturn(toolSearchConfig);
		when(properties.resolveAgent()).thenReturn(agentConfig);
		when(embeddingModelProvider.getIfAvailable()).thenReturn(mock(EmbeddingModel.class));

		String resolvedType = factory.resolveIndexType();
		assertEquals("vector", resolvedType, "当 Vector 索引被配置且 EmbeddingModel 可用时，应当保留 vector 索引");
	}

	@Test
	void testProcessToolsLogsAndStoresSession() {
		ToolCallback tool1 = mock(ToolCallback.class);
		ToolDefinition def1 = mock(ToolDefinition.class);
		when(def1.name()).thenReturn("search_web");
		when(tool1.getToolDefinition()).thenReturn(def1);

		ToolCallback[] tools = new ToolCallback[]{ tool1 };
		ToolCallback[] processed = factory.processTools(tools, 1, 0, "session-123");

		assertNotNull(processed);
		assertEquals(1, processed.length);
		assertEquals(tool1, processed[0]);
	}
}
