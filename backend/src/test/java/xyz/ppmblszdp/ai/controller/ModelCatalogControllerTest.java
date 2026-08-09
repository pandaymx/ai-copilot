package xyz.ppmblszdp.ai.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.web.server.ServerWebExchange;
import xyz.ppmblszdp.ai.dto.ModelCatalogResponse;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;
import xyz.ppmblszdp.ai.registry.ModelDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelCatalogControllerTest {

	private ProviderRegistry registry;
	private ModelCatalogController controller;
	private ServerWebExchange exchange;

	@BeforeEach
	void setUp() {
		ChatModel chatModel = mock(ChatModel.class);
		ModelDescriptor gpt4o = ModelDescriptor.builder()
				.id("gpt-4o")
				.modelName("gpt-4o")
				.displayName("GPT-4o")
				.inputPricePerK(BigDecimal.valueOf(0.015))
				.outputPricePerK(BigDecimal.valueOf(0.06))
				.build();

		ProviderDescriptor openai = ProviderDescriptor.builder()
				.providerId("openai")
				.displayName("OpenAI")
				.chatModel(chatModel)
				.defaultModelId("gpt-4o")
				.models(java.util.Map.of("gpt-4o", gpt4o))
				.build();

		registry = ProviderRegistry.builder()
				.register(openai)
				.defaultProviderId("openai")
				.defaultModelId("gpt-4o")
				.build();

		AuthProperties authProperties = mock(AuthProperties.class);
		when(authProperties.isStrict()).thenReturn(false);

		controller = new ModelCatalogController(registry, new xyz.ppmblszdp.ai.registry.ModelHealthTracker(), authProperties);

		exchange = mock(ServerWebExchange.class);
		Map<String, Object> attrs = new HashMap<>();
		attrs.put(UserIdentityFilter.ATTR_HEADER_VALUE, "user-1");
		attrs.put(UserIdentityFilter.ATTR_HEADER_PRESENT, true);
		when(exchange.getAttributes()).thenReturn(attrs);
	}

	@Test
	void testListReturnsProvidersAndModels() {
		ModelCatalogResponse response = controller.list(exchange);
		assertNotNull(response);
		assertEquals("openai", response.defaultProvider());
		assertEquals("gpt-4o", response.defaultModel());
		assertEquals(1, response.providers().size());

		ModelCatalogResponse.ProviderEntry provider = response.providers().get(0);
		assertEquals("openai", provider.id());
		assertEquals(1, provider.models().size());
		assertEquals("gpt-4o", provider.models().get(0).id());
		assertEquals(BigDecimal.valueOf(0.015), provider.models().get(0).inputPricePerK());
	}
}
