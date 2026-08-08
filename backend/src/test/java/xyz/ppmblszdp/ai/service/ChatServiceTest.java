package xyz.ppmblszdp.ai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.context.ContextAssembler;
import xyz.ppmblszdp.ai.dto.ChatRequest;
import xyz.ppmblszdp.ai.dto.ChatResponseDto;
import xyz.ppmblszdp.ai.registry.ModelDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;

import xyz.ppmblszdp.ai.memory.LongTermMemoryProcessor;

import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceTest {

	private ProviderRegistry registry;
	private ContextAssembler contextAssembler;
	private ObjectProvider sessionChatMemory;
	private ObjectProvider longTermFactory;
	private ObjectProvider longTermWriter;
	private ObjectProvider longTermProcessor;
	private ObjectProvider rateLimiter;
	private SessionService sessionService;
	private AiProviderProperties properties;

	private ChatModel chatModel;
	private ChatService chatService;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		registry = mock(ProviderRegistry.class);
		contextAssembler = mock(ContextAssembler.class);
		sessionChatMemory = mock(ObjectProvider.class);
		longTermFactory = mock(ObjectProvider.class);
		longTermWriter = mock(ObjectProvider.class);
		longTermProcessor = mock(ObjectProvider.class);
		rateLimiter = mock(ObjectProvider.class);
		sessionService = mock(SessionService.class);
		properties = mock(AiProviderProperties.class);

		AiProviderProperties.MemoryConfig memoryConfig = mock(AiProviderProperties.MemoryConfig.class);
		when(memoryConfig.isEnabled()).thenReturn(false);
		when(properties.resolveMemory()).thenReturn(memoryConfig);

		chatModel = mock(ChatModel.class);

		ModelDescriptor modelDescriptor = ModelDescriptor.builder()
				.id("gpt-4o")
				.modelName("gpt-4o")
				.maxContextTokens(128000)
				.build();

		ProviderDescriptor providerDescriptor = ProviderDescriptor.builder()
				.providerId("openai")
				.chatModel(chatModel)
				.build();

		ResolvedModel resolved = new ResolvedModel(chatModel, providerDescriptor, modelDescriptor);
		when(registry.resolve(any(), any())).thenReturn(resolved);

		chatService = new ChatService(
				registry,
				contextAssembler,
				sessionChatMemory,
				longTermFactory,
				longTermWriter,
				longTermProcessor,
				rateLimiter,
				sessionService,
				properties
		);
	}

	@Test
	void testChatWithoutMemorySuccess() {
		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("Hello, world!"))));
		when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

		ChatRequest request = new ChatRequest("Hello", null, "openai", "gpt-4o", null, null, null, null);
		Mono<ChatResponseDto> resultMono = chatService.chat(request);

		StepVerifier.create(resultMono)
				.assertNext(dto -> {
					assertNotNull(dto);
					assertEquals("Hello, world!", dto.content());
					assertEquals("openai", dto.provider());
					assertEquals("gpt-4o", dto.model());
				})
				.verifyComplete();
	}

	@Test
	void testChatWithoutMemoryTimeoutFallback() {
		// 模拟上游调用抛出 TimeoutException
		when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
			throw new TimeoutException("Read timed out");
		});

		ChatRequest request = new ChatRequest("Hello", null, "openai", "gpt-4o", null, null, null, null);
		Mono<ChatResponseDto> resultMono = chatService.chat(request);

		StepVerifier.create(resultMono)
				.assertNext(dto -> {
					assertNotNull(dto);
					assertEquals("上游供应商响应超时，请稍后再试。", dto.content());
					assertEquals("openai", dto.provider());
					assertEquals("gpt-4o", dto.model());
				})
				.verifyComplete();
	}

	@Test
	void testChatWithLongTermMemoryExceptionDoesNotFailMainFlow() throws InterruptedException {
		AiProviderProperties.MemoryConfig memoryConfig = mock(AiProviderProperties.MemoryConfig.class);
		when(memoryConfig.isEnabled()).thenReturn(true);
		when(properties.resolveMemory()).thenReturn(memoryConfig);

		LongTermMemoryProcessor processor = mock(LongTermMemoryProcessor.class);
		doThrow(new RuntimeException("pgvector connection refused"))
				.when(processor).processTurn(any(), any(), any(), any());
		when(longTermProcessor.getIfAvailable()).thenReturn(processor);

		ChatService enabledChatService = new ChatService(
				registry,
				contextAssembler,
				sessionChatMemory,
				longTermFactory,
				longTermWriter,
				longTermProcessor,
				rateLimiter,
				sessionService,
				properties
		);

		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("Hello!"))));
		when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

		ChatRequest request = new ChatRequest("Hi", null, "openai", "gpt-4o", null, "conv-1", "user-1", null);
		Mono<ChatResponseDto> resultMono = enabledChatService.chat(request);

		StepVerifier.create(resultMono)
				.assertNext(dto -> {
					assertNotNull(dto);
					assertEquals("Hello!", dto.content());
				})
				.verifyComplete();

		// 给足时间等待异步重试完成，并验证重试了 2 次 (1 首次 + 1 重试)
		Thread.sleep(2500);
		verify(processor, times(2)).processTurn(eq("user-1"), eq("conv-1"), eq("Hi"), eq("Hello!"));
	}
}
