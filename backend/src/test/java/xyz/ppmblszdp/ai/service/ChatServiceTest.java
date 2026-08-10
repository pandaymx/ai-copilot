package xyz.ppmblszdp.ai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.config.AiProviderProperties.MemoryConfig;
import xyz.ppmblszdp.ai.context.ContextAssembler;
import xyz.ppmblszdp.ai.dto.ChatRequest;
import xyz.ppmblszdp.ai.dto.ChatResponseDto;
import xyz.ppmblszdp.ai.registry.ModelDescriptor;
import xyz.ppmblszdp.ai.registry.ModelHealthTracker;
import xyz.ppmblszdp.ai.registry.ProviderDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;
import xyz.ppmblszdp.ai.memory.ChatRateLimiter.RateLimiter;
import xyz.ppmblszdp.ai.memory.LongTermMemoryConfig.LongTermMemoryAdvisorFactory;
import xyz.ppmblszdp.ai.memory.LongTermMemoryConfig.LongTermMemoryWriter;
import xyz.ppmblszdp.ai.memory.LongTermMemoryProcessor;
import xyz.ppmblszdp.ai.memory.UsageQuotaChecker.UsageQuota;
import xyz.ppmblszdp.ai.rag.advisor.RagAdvisorConfig.RagAdvisorFactory;
import xyz.ppmblszdp.ai.repository.UsageRepository;
import xyz.ppmblszdp.ai.safeguard.SafeGuardAdvisor;
import xyz.ppmblszdp.ai.tool.ToolEventEmitter;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;

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
	private ObjectProvider<ChatMemory> sessionChatMemory;
	private ObjectProvider<LongTermMemoryAdvisorFactory> longTermFactory;
	private ObjectProvider<LongTermMemoryWriter> longTermWriter;
	private ObjectProvider<LongTermMemoryProcessor> longTermProcessor;
	private ObjectProvider<RateLimiter> rateLimiter;
	private SessionService sessionService;
	private AiProviderProperties properties;

	private ChatModel chatModel;
	private ChatService chatService;
	@SuppressWarnings("unchecked")
	private ObjectProvider<OpenAiAudioSpeechModel> speechModelProvider = mock(ObjectProvider.class);
	@SuppressWarnings("unchecked")
	private ObjectProvider<SyncMcpToolCallbackProvider> mcpToolProvider = mock(ObjectProvider.class);

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		registry = mock(ProviderRegistry.class);
		contextAssembler = mock(ContextAssembler.class);
		when(contextAssembler.defaultSystemPrompt()).thenReturn("You are a helpful assistant.");
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

		AiProviderProperties.AgentConfig agentConfig = mock(AiProviderProperties.AgentConfig.class);
		when(agentConfig.isEnabled()).thenReturn(false);
		when(properties.resolveAgent()).thenReturn(agentConfig);

		chatModel = mock(ChatModel.class);
		when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().build());

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

		ObjectProvider<SafeGuardAdvisor> safeGuardAdvisor = mock(ObjectProvider.class);
		ObjectProvider<RagAdvisorFactory> ragAdvisorFactory = mock(ObjectProvider.class);
		ObjectProvider<UsageQuota> usageQuota = mock(ObjectProvider.class);
		UsageRepository usageRepository = mock(UsageRepository.class);

		chatService = new ChatService(
				registry,
				contextAssembler,
				sessionChatMemory,
				longTermFactory,
				longTermWriter,
				longTermProcessor,
				rateLimiter,
				usageQuota,
				usageRepository,
				safeGuardAdvisor,
				ragAdvisorFactory,
				new ModelHealthTracker(),
				sessionService,
				properties,
				speechModelProvider,
				new ToolEventEmitter(properties),
				new ToolCallback[0],
				mcpToolProvider);
	}

	@Test
	void testChatWithoutMemorySuccess() {
		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("Hello, world!"))));
		when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

		ChatRequest request = new ChatRequest("Hello", null, "openai", "gpt-4o", null, null, null, null, null);
		Mono<ChatResponseDto> resultMono = chatService.chat(request, "user-1");

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

		ChatRequest request = new ChatRequest("Hello", null, "openai", "gpt-4o", null, null, null, null, null);
		Mono<ChatResponseDto> resultMono = chatService.chat(request, "user-1");

		StepVerifier.create(resultMono)
				.assertNext(dto -> {
					assertNotNull(dto);
					assertEquals("上游供应商响应超时/异常，请稍后再试。", dto.content());
					assertEquals("openai", dto.provider());
					assertEquals("gpt-4o", dto.model());
				})
				.verifyComplete();
	}

	@Test
	void testChatFallbackToSecondaryProviderWhenPrimaryFails() {
		ChatModel primaryModel = mock(ChatModel.class);
		ChatModel fallbackModel = mock(ChatModel.class);
		when(primaryModel.getOptions()).thenReturn(OpenAiChatOptions.builder().build());
		when(fallbackModel.getOptions()).thenReturn(OpenAiChatOptions.builder().build());

		ProviderDescriptor primaryProvider = ProviderDescriptor.builder()
				.providerId("deepseek")
				.chatModel(primaryModel)
				.build();
		ModelDescriptor primaryModelDesc = ModelDescriptor.builder().id("deepseek-chat").modelName("deepseek-chat")
				.build();
		ResolvedModel primaryResolved = new ResolvedModel(primaryModel, primaryProvider, primaryModelDesc);

		ProviderDescriptor fallbackProviderDesc = ProviderDescriptor.builder()
				.providerId("openai")
				.chatModel(fallbackModel)
				.build();
		ModelDescriptor fallbackModelDesc = ModelDescriptor.builder().id("gpt-4o").modelName("gpt-4o").build();
		ResolvedModel fallbackResolved = new ResolvedModel(fallbackModel, fallbackProviderDesc, fallbackModelDesc);

		when(registry.resolve("deepseek", "deepseek-chat")).thenReturn(primaryResolved);
		when(registry.resolveFallback("deepseek", "openai", null)).thenReturn(fallbackResolved);
		when(properties.fallbackProvider()).thenReturn("openai");

		when(primaryModel.call(any(Prompt.class))).thenThrow(new RuntimeException("DeepSeek 429 Too Many Requests"));

		ChatResponse fallbackResp = new ChatResponse(
				List.of(new Generation(new AssistantMessage("Fallback success!"))));
		when(fallbackModel.call(any(Prompt.class))).thenReturn(fallbackResp);

		ChatRequest request = new ChatRequest("Hi", null, "deepseek", "deepseek-chat", null, null, null, null, null);
		Mono<ChatResponseDto> resultMono = chatService.chat(request, "user-1");

		StepVerifier.create(resultMono)
				.assertNext(dto -> {
					assertNotNull(dto);
					assertEquals("Fallback success!", dto.content());
					assertEquals("openai", dto.provider());
					assertEquals("gpt-4o", dto.model());
				})
				.verifyComplete();
	}

	@Test
	void testChatWithLongTermMemoryExceptionDoesNotFailMainFlow() throws InterruptedException {
		MemoryConfig memoryConfig = mock(MemoryConfig.class);
		when(memoryConfig.isEnabled()).thenReturn(true);
		when(properties.resolveMemory()).thenReturn(memoryConfig);

		ChatMemory chatMemory = mock(ChatMemory.class);
		when(chatMemory.get(any())).thenReturn(List.of());
		when(sessionChatMemory.getIfAvailable()).thenReturn(chatMemory);

		LongTermMemoryProcessor processor = mock(LongTermMemoryProcessor.class);
		doThrow(new RuntimeException("pgvector connection refused"))
				.when(processor).processTurn(any(), any(), any(), any());
		when(longTermProcessor.getIfAvailable()).thenReturn(processor);

		@SuppressWarnings("unchecked")
		ObjectProvider<SafeGuardAdvisor> mockSafeGuardAdvisor = mock(ObjectProvider.class);
		@SuppressWarnings("unchecked")
		ObjectProvider<RagAdvisorFactory> mockRagAdvisorFactory = mock(ObjectProvider.class);
		@SuppressWarnings("unchecked")
		ObjectProvider<UsageQuota> usageQuota = mock(ObjectProvider.class);
		@SuppressWarnings("unchecked")
		ObjectProvider<SyncMcpToolCallbackProvider> mockMcpToolProvider = mock(ObjectProvider.class);
		UsageRepository usageRepository = mock(UsageRepository.class);

		ChatService enabledChatService = new ChatService(
				registry,
				contextAssembler,
				sessionChatMemory,
				longTermFactory,
				longTermWriter,
				longTermProcessor,
				rateLimiter,
				usageQuota,
				usageRepository,
				mockSafeGuardAdvisor,
				mockRagAdvisorFactory,
				new ModelHealthTracker(),
				sessionService,
				properties,
				speechModelProvider,
				new ToolEventEmitter(properties),
				new ToolCallback[0],
				mockMcpToolProvider);

		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("Hello!"))));
		when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

		ChatRequest request = new ChatRequest("Hi", null, "openai", "gpt-4o", null, "conv-1", "user-1", null, null);
		Mono<ChatResponseDto> resultMono = enabledChatService.chat(request, "user-1");

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
