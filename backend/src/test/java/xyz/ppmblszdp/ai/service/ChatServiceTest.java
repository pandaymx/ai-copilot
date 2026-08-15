package xyz.ppmblszdp.ai.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.config.AiProviderProperties.MemoryConfig;
import xyz.ppmblszdp.ai.context.AssembleResult;
import xyz.ppmblszdp.ai.context.ContextAssembler;
import xyz.ppmblszdp.ai.dto.ChatChunkDto;
import xyz.ppmblszdp.ai.dto.ChatRequest;
import xyz.ppmblszdp.ai.dto.ChatResponseDto;
import xyz.ppmblszdp.ai.dto.ImageGenerationResultDto;
import xyz.ppmblszdp.ai.dto.MediaDto;
import xyz.ppmblszdp.ai.memory.ChatRateLimiter.RateLimiter;
import xyz.ppmblszdp.ai.memory.LongTermMemoryConfig.LongTermMemoryAdvisorFactory;
import xyz.ppmblszdp.ai.memory.LongTermMemoryConfig.LongTermMemoryWriter;
import xyz.ppmblszdp.ai.memory.LongTermMemoryProcessor;
import xyz.ppmblszdp.ai.memory.UsageQuotaChecker.UsageQuota;
import xyz.ppmblszdp.ai.rag.advisor.RagAdvisorConfig.RagAdvisorFactory;
import xyz.ppmblszdp.ai.registry.ModelDescriptor;
import xyz.ppmblszdp.ai.registry.ModelHealthTracker;
import xyz.ppmblszdp.ai.registry.ProviderDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;
import xyz.ppmblszdp.ai.repository.UsageRepository;
import xyz.ppmblszdp.ai.safeguard.SafeGuardAdvisor;
import xyz.ppmblszdp.ai.tool.ToolEventEmitter;
import xyz.ppmblszdp.ai.tool.ToolSearchAdvisorConfig;

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
    private ObjectProvider<SafeGuardAdvisor> safeGuardAdvisor;
    private ObjectProvider<RagAdvisorFactory> ragAdvisorFactory;
    private ObjectProvider<UsageQuota> usageQuota;
    private UsageRepository usageRepository;

    private ChatModel chatModel;
    private ChatService chatService;

    @SuppressWarnings("unchecked")
    private ObjectProvider<OpenAiAudioSpeechModel> speechModelProvider = mock(ObjectProvider.class);

    @SuppressWarnings("unchecked")
    private ObjectProvider<SyncMcpToolCallbackProvider> mcpToolProvider = mock(ObjectProvider.class);

    @SuppressWarnings("unchecked")
    private ObjectProvider<ToolSearchAdvisorConfig.ToolSearchAdvisorFactory> toolSearchFactory =
            mock(ObjectProvider.class);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        registry = mock(ProviderRegistry.class);
        contextAssembler = mock(ContextAssembler.class);
        when(contextAssembler.defaultSystemPrompt()).thenReturn("You are a helpful assistant.");
        when(contextAssembler.assembleWithResult(any(), any(), any(), any(), anyInt(), any(), any()))
                .thenReturn(new AssembleResult(List.of()));
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

        safeGuardAdvisor = mock(ObjectProvider.class);
        ragAdvisorFactory = mock(ObjectProvider.class);
        usageQuota = mock(ObjectProvider.class);
        usageRepository = mock(UsageRepository.class);

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
                mcpToolProvider,
                toolSearchFactory);
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
        ModelDescriptor primaryModelDesc = ModelDescriptor.builder()
                .id("deepseek-chat")
                .modelName("deepseek-chat")
                .build();
        ResolvedModel primaryResolved = new ResolvedModel(primaryModel, primaryProvider, primaryModelDesc);

        ProviderDescriptor fallbackProviderDesc = ProviderDescriptor.builder()
                .providerId("openai")
                .chatModel(fallbackModel)
                .build();
        ModelDescriptor fallbackModelDesc =
                ModelDescriptor.builder().id("gpt-4o").modelName("gpt-4o").build();
        ResolvedModel fallbackResolved = new ResolvedModel(fallbackModel, fallbackProviderDesc, fallbackModelDesc);

        when(registry.resolve("deepseek", "deepseek-chat")).thenReturn(primaryResolved);
        when(registry.resolveFallback("deepseek", "openai", null)).thenReturn(fallbackResolved);
        when(properties.fallbackProvider()).thenReturn("openai");

        when(primaryModel.call(any(Prompt.class))).thenThrow(new RuntimeException("DeepSeek 429 Too Many Requests"));

        ChatResponse fallbackResp =
                new ChatResponse(List.of(new Generation(new AssistantMessage("Fallback success!"))));
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
                .when(processor)
                .processTurn(any(), any(), any(), any());
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
                mockMcpToolProvider,
                toolSearchFactory);

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

    @Test
    void testNonVisionModelWithMediaReturnsFriendlyError() {
        ModelDescriptor nonVisionModel = ModelDescriptor.builder()
                .id("deepseek-chat")
                .modelName("deepseek-chat")
                .tags(List.of("chat"))
                .build();
        ProviderDescriptor provider = ProviderDescriptor.builder()
                .providerId("deepseek")
                .chatModel(chatModel)
                .build();
        ResolvedModel resolved = new ResolvedModel(chatModel, provider, nonVisionModel);
        when(registry.resolve("deepseek", "deepseek-chat")).thenReturn(resolved);

        MediaDto mediaDto = new MediaDto(
                "image/png",
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");
        ChatRequest request = new ChatRequest(
                "描述这张图片", null, "deepseek", "deepseek-chat", null, null, "user-1", List.of(mediaDto), null);

        Mono<ChatResponseDto> mono = chatService.chat(request, "user-1");
        StepVerifier.create(mono)
                .assertNext(dto -> {
                    assertNotNull(dto);
                    assertEquals("当前模型不支持图片，请切换到支持图片的模型", dto.content());
                })
                .verifyComplete();

        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void testNonVisionModelWithEmptyMediaProceedsNormally() {
        ModelDescriptor nonVisionModel = ModelDescriptor.builder()
                .id("deepseek-chat")
                .modelName("deepseek-chat")
                .tags(List.of("chat"))
                .build();
        ProviderDescriptor provider = ProviderDescriptor.builder()
                .providerId("deepseek")
                .chatModel(chatModel)
                .build();
        ResolvedModel resolved = new ResolvedModel(chatModel, provider, nonVisionModel);
        when(registry.resolve("deepseek", "deepseek-chat")).thenReturn(resolved);

        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("Hello text!"))));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);

        ChatRequest request =
                new ChatRequest("Hello", null, "deepseek", "deepseek-chat", null, null, "user-1", List.of(), null);

        Mono<ChatResponseDto> mono = chatService.chat(request, "user-1");
        StepVerifier.create(mono)
                .assertNext(dto -> {
                    assertNotNull(dto);
                    assertEquals("Hello text!", dto.content());
                })
                .verifyComplete();
    }

    @Test
    void testIsImageGenerationRequestWithExtendedKeywords() {
        ImageGenerationService imgService = mock(ImageGenerationService.class);
        ImageGenerationResultDto imgResult =
                new ImageGenerationResultDto("img-1", "prompt", "b64data", "image/png", "openai", "dall-e-3");
        when(imgService.generateImage(any())).thenReturn(Mono.just(imgResult));

        @SuppressWarnings("unchecked")
        ObjectProvider<ImageGenerationService> imgProvider = mock(ObjectProvider.class);
        when(imgProvider.getIfAvailable()).thenReturn(imgService);

        ChatService serviceWithImg = new ChatService(
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
                mcpToolProvider,
                toolSearchFactory,
                null,
                imgProvider);

        ChatRequest req1 = new ChatRequest("绘制一张雪山风景图", null, "openai", "gpt-4o", null, null, null, null, null);
        Flux<ChatChunkDto> flux1 = serviceWithImg.streamChatChunks(req1, "user-1");

        StepVerifier.create(flux1)
                .expectNextMatches(
                        chunk -> chunk.content() != null && chunk.content().contains("正在为你生成图片：一张雪山风景图"))
                .expectNextMatches(chunk -> "processing".equals(chunk.status()))
                .expectNextMatches(chunk -> "complete".equals(chunk.status()))
                .verifyComplete();

        ChatRequest req2 = new ChatRequest(
                "generate image: a cyberpunk city", null, "openai", "gpt-4o", null, null, null, null, null);
        Flux<ChatChunkDto> flux2 = serviceWithImg.streamChatChunks(req2, "user-1");

        StepVerifier.create(flux2)
                .expectNextMatches(
                        chunk -> chunk.content() != null && chunk.content().contains("正在为你生成图片：a cyberpunk city"))
                .expectNextMatches(chunk -> "processing".equals(chunk.status()))
                .expectNextMatches(chunk -> "complete".equals(chunk.status()))
                .verifyComplete();
    }
}
