package xyz.ppmblszdp.ai.registry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import xyz.ppmblszdp.ai.exception.ProviderNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ProviderRegistryTest {

	private ProviderRegistry registry;
	private ChatModel openaiModel;
	private ChatModel deepseekModel;

	@BeforeEach
	void setUp() {
		openaiModel = mock(ChatModel.class);
		deepseekModel = mock(ChatModel.class);

		ModelDescriptor gpt4o = ModelDescriptor.builder()
				.id("gpt-4o")
				.modelName("gpt-4o")
				.build();

		ProviderDescriptor openai = ProviderDescriptor.builder()
				.providerId("openai")
				.chatModel(openaiModel)
				.defaultModelId("gpt-4o")
				.models(java.util.Map.of("gpt-4o", gpt4o))
				.build();

		ModelDescriptor deepseekChat = ModelDescriptor.builder()
				.id("deepseek-chat")
				.modelName("deepseek-chat")
				.build();

		ProviderDescriptor deepseek = ProviderDescriptor.builder()
				.providerId("deepseek")
				.chatModel(deepseekModel)
				.defaultModelId("deepseek-chat")
				.models(java.util.Map.of("deepseek-chat", deepseekChat))
				.build();

		registry = ProviderRegistry.builder()
				.register(openai)
				.register(deepseek)
				.defaultProviderId("openai")
				.defaultModelId("gpt-4o")
				.build();
	}

	@Test
	void testResolveExactProviderAndModel() {
		ResolvedModel resolved = registry.resolve("openai", "gpt-4o");
		assertNotNull(resolved);
		assertEquals("openai", resolved.provider().providerId());
		assertEquals("gpt-4o", resolved.model().id());
	}

	@Test
	void testResolveNullProviderFallsBackToDefault() {
		ResolvedModel resolved = registry.resolve(null, "gpt-4o");
		assertNotNull(resolved);
		assertEquals("openai", resolved.provider().providerId());
	}

	@Test
	void testResolveUnknownProviderThrowsException() {
		assertThrows(ProviderNotFoundException.class, () -> registry.resolve("unknown_provider", "gpt-4o"));
	}

	@Test
	void testResolveCustomModelId() {
		ResolvedModel resolved = registry.resolve("openai", "custom-gpt-5");
		assertNotNull(resolved);
		assertEquals("custom-gpt-5", resolved.model().id());
		assertEquals("自定义模型", resolved.model().description());
	}

	@Test
	void testResolveFallback() {
		ResolvedModel fallback = registry.resolveFallback("deepseek", "openai", "gpt-4o");
		assertNotNull(fallback);
		assertEquals("openai", fallback.provider().providerId());
		assertEquals("gpt-4o", fallback.model().id());
	}

	@Test
	void testResolveFallbackDefaultWhenCurrentIsDeepseek() {
		ResolvedModel fallback = registry.resolveFallback("deepseek", null, null);
		assertNotNull(fallback);
		assertEquals("openai", fallback.provider().providerId());
	}

	@Test
	void testResolveFallbackReturnsNullWhenNoDifferentProviderAvailable() {
		ProviderRegistry singleRegistry = ProviderRegistry.builder()
				.register(ProviderDescriptor.builder().providerId("openai").chatModel(openaiModel).build())
				.defaultProviderId("openai")
				.build();

		ResolvedModel fallback = singleRegistry.resolveFallback("openai", null, null);
		assertNull(fallback);
	}
}
