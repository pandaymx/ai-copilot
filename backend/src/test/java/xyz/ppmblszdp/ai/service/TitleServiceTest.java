package xyz.ppmblszdp.ai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.test.StepVerifier;
import xyz.ppmblszdp.ai.registry.ModelDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TitleServiceTest {

	private ProviderRegistry registry;
	private ChatModel chatModel;
	private TitleService titleService;

	@BeforeEach
	void setUp() {
		registry = mock(ProviderRegistry.class);
		chatModel = mock(ChatModel.class);
		when(chatModel.getOptions()).thenReturn(org.springframework.ai.openai.OpenAiChatOptions.builder().build());

		ProviderDescriptor provider = ProviderDescriptor.builder()
				.providerId("openai")
				.chatModel(chatModel)
				.build();

		ModelDescriptor model = ModelDescriptor.builder().id("gpt-4o").modelName("gpt-4o").build();
		ResolvedModel resolved = new ResolvedModel(chatModel, provider, model);

		when(registry.resolve(eq("openai"), eq("gpt-4o"))).thenReturn(resolved);
		titleService = new TitleService(registry);
	}

	@Test
	void testGenerateTitleSuccessAndCleansPrefix() {
		ChatResponse resp = new ChatResponse(List.of(new Generation(new AssistantMessage("标题：**Spring AI 核心架构**"))));
		when(chatModel.call(any(Prompt.class))).thenReturn(resp);

		StepVerifier.create(titleService.generateTitle("请问 Spring AI 怎么用？", "Spring AI 提供了通用抽象...", "openai", "gpt-4o"))
				.expectNext("Spring AI 核心架构")
				.verifyComplete();
	}

	@Test
	void testGenerateTitleReturnsNullWhenInputIsBlank() {
		StepVerifier.create(titleService.generateTitle("", " ", "openai", "gpt-4o"))
				.verifyComplete();
	}

	@Test
	void testGenerateTitleReturnsNullWhenModelResolveFails() {
		when(registry.resolve("unknown", "unknown")).thenThrow(new RuntimeException("Provider not found"));

		StepVerifier.create(titleService.generateTitle("Hi", "Hello", "unknown", "unknown"))
				.verifyComplete();
	}

	@Test
	void testGenerateTitleReturnsNullWhenLlmCallThrowsException() {
		when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("LLM Timeout"));

		StepVerifier.create(titleService.generateTitle("Hi", "Hello", "openai", "gpt-4o"))
				.verifyComplete();
	}
}
