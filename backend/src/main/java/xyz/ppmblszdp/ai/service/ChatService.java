package xyz.ppmblszdp.ai.service;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.context.ContextAssembler;
import xyz.ppmblszdp.ai.dto.ChatChunkDto;
import xyz.ppmblszdp.ai.dto.ChatRequest;
import xyz.ppmblszdp.ai.dto.ChatResponseDto;
import xyz.ppmblszdp.ai.memory.ChatRateLimiter.RateLimiter;
import xyz.ppmblszdp.ai.memory.LongTermMemoryConfig.LongTermMemoryAdvisorFactory;
import xyz.ppmblszdp.ai.memory.LongTermMemoryConfig.LongTermMemoryWriter;
import xyz.ppmblszdp.ai.memory.LongTermMemoryProcessor;
import xyz.ppmblszdp.ai.memory.UsageQuotaChecker.UsageQuota;
import xyz.ppmblszdp.ai.rag.advisor.RagAdvisorConfig.RagAdvisorFactory;
import xyz.ppmblszdp.ai.registry.ModelHealthTracker;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.repository.UsageRepository;
import xyz.ppmblszdp.ai.safeguard.SafeGuardAdvisor;
import xyz.ppmblszdp.ai.tool.AugmentedToolCallbackProvider;
import xyz.ppmblszdp.ai.tool.ToolEventEmitter;
import xyz.ppmblszdp.ai.tool.ToolSearchAdvisorConfig.ToolSearchAdvisorFactory;

/**
 * 聊天业务门面服务 (Facade Pattern)。
 *
 * <p>组合注入 {@link ChatOrchestrator}、{@link ImageRouter}、{@link VoiceService} 与 {@link UsageRecorder}，
 * 构造函数由原本的 24 个参数精简至 4 个，实现严格的 DAG 单向无环依赖与职责分离。
 */
@Service
public class ChatService implements DisposableBean {

	private final ChatOrchestrator orchestrator;
	private final ImageRouter imageRouter;
	private final VoiceService voiceService;
	private final UsageRecorder usageRecorder;

	@Autowired
	public ChatService(
			ChatOrchestrator orchestrator,
			ImageRouter imageRouter,
			VoiceService voiceService,
			UsageRecorder usageRecorder
	) {
		this.orchestrator = orchestrator;
		this.imageRouter = imageRouter;
		this.voiceService = voiceService;
		this.usageRecorder = usageRecorder;
	}

	/** 兼容旧版测试套件 19 参数重载构造器。 */
	public ChatService(
			ProviderRegistry registry,
			ContextAssembler contextAssembler,
			ObjectProvider<ChatMemory> sessionChatMemory,
			ObjectProvider<LongTermMemoryAdvisorFactory> longTermFactory,
			ObjectProvider<LongTermMemoryWriter> longTermWriter,
			ObjectProvider<LongTermMemoryProcessor> longTermProcessor,
			ObjectProvider<RateLimiter> rateLimiter,
			ObjectProvider<UsageQuota> usageQuota,
			UsageRepository usageRepository,
			ObjectProvider<SafeGuardAdvisor> safeGuardAdvisor,
			ObjectProvider<RagAdvisorFactory> ragAdvisorFactory,
			ModelHealthTracker healthTracker,
			SessionService sessionService,
			AiProviderProperties properties,
			ObjectProvider<OpenAiAudioSpeechModel> speechModelProvider,
			ToolEventEmitter toolEventEmitter,
			@Qualifier("agentToolCallbacks") ToolCallback[] toolCallbacks,
			ObjectProvider<SyncMcpToolCallbackProvider> mcpToolProvider,
			ObjectProvider<ToolSearchAdvisorFactory> toolSearchFactory) {
		this(registry, contextAssembler, sessionChatMemory, longTermFactory, longTermWriter, longTermProcessor,
				rateLimiter, usageQuota, usageRepository, safeGuardAdvisor, ragAdvisorFactory, healthTracker,
				sessionService, properties, speechModelProvider, toolEventEmitter, toolCallbacks, mcpToolProvider,
				toolSearchFactory, null, null);
	}

	/** 兼容旧版测试套件 24 参数全量构造器。 */
	public ChatService(
			ProviderRegistry registry,
			ContextAssembler contextAssembler,
			ObjectProvider<ChatMemory> sessionChatMemory,
			ObjectProvider<LongTermMemoryAdvisorFactory> longTermFactory,
			ObjectProvider<LongTermMemoryWriter> longTermWriter,
			ObjectProvider<LongTermMemoryProcessor> longTermProcessor,
			ObjectProvider<RateLimiter> rateLimiter,
			ObjectProvider<UsageQuota> usageQuota,
			UsageRepository usageRepository,
			ObjectProvider<SafeGuardAdvisor> safeGuardAdvisor,
			ObjectProvider<RagAdvisorFactory> ragAdvisorFactory,
			ModelHealthTracker healthTracker,
			SessionService sessionService,
			AiProviderProperties properties,
			ObjectProvider<OpenAiAudioSpeechModel> speechModelProvider,
			ToolEventEmitter toolEventEmitter,
			@Qualifier("agentToolCallbacks") ToolCallback[] toolCallbacks,
			ObjectProvider<SyncMcpToolCallbackProvider> mcpToolProvider,
			ObjectProvider<ToolSearchAdvisorFactory> toolSearchFactory,
			ObjectProvider<AugmentedToolCallbackProvider> augmentedToolProvider,
			ObjectProvider<ImageGenerationService> imageGenerationServiceProvider) {
		UsageRecorder uRecorder = new UsageRecorder(usageRepository, usageQuota);
		ImageRouter iRouter = new ImageRouter(imageGenerationServiceProvider, properties);
		VoiceService vService = new VoiceService(speechModelProvider, registry);
		IntentClassifier intentClassifier = new IntentClassifier();
		ChatOrchestrator cOrchestrator = new ChatOrchestrator(
				registry, contextAssembler, sessionChatMemory, longTermFactory, longTermWriter,
				longTermProcessor, rateLimiter, uRecorder, safeGuardAdvisor, ragAdvisorFactory,
				healthTracker, sessionService, properties, toolEventEmitter, toolCallbacks,
				mcpToolProvider, toolSearchFactory, augmentedToolProvider, iRouter, intentClassifier
		);
		this.orchestrator = cOrchestrator;
		this.imageRouter = iRouter;
		this.voiceService = vService;
		this.usageRecorder = uRecorder;
	}

	@Override
	public void destroy() throws Exception {
		if (orchestrator != null) {
			orchestrator.destroy();
		}
	}

	public String resolveConversationId(ChatRequest request, String userId) {
		return orchestrator.resolveConversationId(request, userId);
	}

	public Mono<ChatResponseDto> chat(ChatRequest request, String userId) {
		return orchestrator.chat(request, userId);
	}

	public Flux<ChatChunkDto> streamChatChunks(ChatRequest request, String userId) {
		return orchestrator.streamChatChunks(request, userId);
	}

	public Mono<byte[]> synthesizeSpeech(String text, String voice, String userId) {
		return voiceService.synthesizeSpeech(text, voice, userId);
	}

	public Mono<String> transcribeAudio(byte[] audioBytes, String mimeType, String userId) {
		return voiceService.transcribeAudio(audioBytes, mimeType, userId);
	}

	public ChatOrchestrator getOrchestrator() {
		return orchestrator;
	}

	public ImageRouter getImageRouter() {
		return imageRouter;
	}

	public VoiceService getVoiceService() {
		return voiceService;
	}

	public UsageRecorder getUsageRecorder() {
		return usageRecorder;
	}
}
