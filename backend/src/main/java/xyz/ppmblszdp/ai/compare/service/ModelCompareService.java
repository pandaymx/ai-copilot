package xyz.ppmblszdp.ai.compare.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import xyz.ppmblszdp.ai.compare.dto.CompareChunkDto;
import xyz.ppmblszdp.ai.compare.dto.CompareRequest;
import xyz.ppmblszdp.ai.compare.dto.CompareResponseDto;
import xyz.ppmblszdp.ai.compare.dto.CompareResponseDto.ModelCompareResult;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;
import xyz.ppmblszdp.ai.service.SessionService;

/**
 * 多模型并行调用、流式复用与评测对比服务。
 */
@Service
public class ModelCompareService {

    private static final Logger log = LoggerFactory.getLogger(ModelCompareService.class);

    private final ProviderRegistry providerRegistry;
    private final SessionService sessionService;

    public ModelCompareService(ProviderRegistry providerRegistry, SessionService sessionService) {
        this.providerRegistry = providerRegistry;
        this.sessionService = sessionService;
    }

    /**
     * 估算 Token 数量（用于没有直接返回 Token Usage 时的精确兜底，避免 NaN）。
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int chineseChars = 0;
        int otherChars = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }
        return Math.max(1, (int) Math.ceil(chineseChars * 1.3 + (otherChars / 3.8)));
    }

    /**
     * 构建包含可选历史上下文的系统与用户 Prompt。
     */
    private String buildPromptWithContext(CompareRequest request, String userId) {
        String prompt = request.prompt();
        if (request.conversationId() != null && !request.conversationId().isBlank() && sessionService != null) {
            try {
                var sessionOpt = sessionService.getSessionDetail(request.conversationId(), userId);
                if (sessionOpt.isPresent()) {
                    var msgs = sessionOpt.get().messages();
                    if (msgs != null && !msgs.isEmpty()) {
                        StringBuilder historySnippet = new StringBuilder("【前序对话背景参考】\n");
                        int start = Math.max(0, msgs.size() - 6);
                        for (int i = start; i < msgs.size(); i++) {
                            var m = msgs.get(i);
                            historySnippet
                                    .append(m.role())
                                    .append(": ")
                                    .append(m.content())
                                    .append("\n");
                        }
                        historySnippet.append("\n【当前用户问题】\n").append(prompt);
                        return historySnippet.toString();
                    }
                }
            } catch (Exception e) {
                log.warn("载入会话历史上下文失败，降级为纯文本提示词: {}", e.getMessage());
            }
        }
        return prompt;
    }

    /**
     * 并行流式多模型比对：各模型流式分片打上 modelIndex 标签，单模型异常隔离。
     */
    public Flux<CompareChunkDto> streamCompare(CompareRequest request, String userId) {
        if (request == null || request.prompt() == null || request.prompt().isBlank()) {
            return Flux.error(new IllegalArgumentException("对比提示词不能为空"));
        }
        List<CompareRequest.ModelTarget> targets = (request.models() != null
                        && !request.models().isEmpty())
                ? request.models().subList(0, Math.min(3, request.models().size()))
                : List.of();

        if (targets.isEmpty()) {
            return Flux.error(new IllegalArgumentException("请至少指定一个对比模型"));
        }

        String userPrompt = buildPromptWithContext(request, userId);
        String systemPrompt =
                (request.systemPrompt() != null && !request.systemPrompt().isBlank())
                        ? request.systemPrompt()
                        : "你是一个高效、精准的 AI 专家助手。请针对用户的要求给出专业、条理清晰的高质量回答。";

        List<Flux<CompareChunkDto>> modelStreams = new ArrayList<>();

        for (int i = 0; i < targets.size(); i++) {
            final int index = i;
            final CompareRequest.ModelTarget target = targets.get(i);
            modelStreams.add(streamSingleModel(target, index, userPrompt, systemPrompt));
        }

        return Flux.merge(modelStreams);
    }

    /**
     * 单模型流式调用与性能指标采集（包含首字延迟 TTFT、速率 Tokens/s、异常安全兜底）。
     */
    private Flux<CompareChunkDto> streamSingleModel(
            CompareRequest.ModelTarget target, int index, String userPrompt, String systemPrompt) {
        return Flux.defer(() -> {
            long startTime = System.currentTimeMillis();
            AtomicLong ttftMs = new AtomicLong(-1);
            StringBuilder contentAcc = new StringBuilder();
            StringBuilder thinkingAcc = new StringBuilder();
            AtomicBoolean isThinking = new AtomicBoolean(false);

            ResolvedModel resolvedModel;
            try {
                resolvedModel = providerRegistry.resolve(target.provider(), target.model());
            } catch (Exception e) {
                log.error("解析模型失败: {}/{}", target.provider(), target.model(), e);
                return Flux.just(
                        CompareChunkDto.error(index, target.provider(), target.model(), "无法路由该模型: " + e.getMessage()));
            }

            final String resolvedProvider = resolvedModel.provider().providerId();
            final String resolvedModelId = resolvedModel.model().id();

            return resolvedModel.chatClient().prompt().system(systemPrompt).user(userPrompt).stream()
                    .content()
                    .flatMap(chunk -> {
                        if (chunk == null || chunk.isEmpty()) {
                            return Flux.<CompareChunkDto>empty();
                        }
                        // 首字延迟计算
                        if (ttftMs.get() < 0) {
                            ttftMs.set(Math.max(1L, System.currentTimeMillis() - startTime));
                        }

                        // 思考链标签嗅探与分流
                        if (chunk.contains("<think>")) {
                            isThinking.set(true);
                            String[] parts = chunk.split("<think>", 2);
                            List<CompareChunkDto> list = new ArrayList<>();
                            if (!parts[0].isEmpty()) {
                                contentAcc.append(parts[0]);
                                list.add(CompareChunkDto.text(index, resolvedProvider, resolvedModelId, parts[0]));
                            }
                            if (parts.length > 1 && !parts[1].isEmpty()) {
                                thinkingAcc.append(parts[1]);
                                list.add(CompareChunkDto.thinking(index, resolvedProvider, resolvedModelId, parts[1]));
                            }
                            return Flux.fromIterable(list);
                        }
                        if (chunk.contains("</think>")) {
                            isThinking.set(false);
                            String[] parts = chunk.split("</think>", 2);
                            List<CompareChunkDto> list = new ArrayList<>();
                            if (!parts[0].isEmpty()) {
                                thinkingAcc.append(parts[0]);
                                list.add(CompareChunkDto.thinking(index, resolvedProvider, resolvedModelId, parts[0]));
                            }
                            if (parts.length > 1 && !parts[1].isEmpty()) {
                                contentAcc.append(parts[1]);
                                list.add(CompareChunkDto.text(index, resolvedProvider, resolvedModelId, parts[1]));
                            }
                            return Flux.fromIterable(list);
                        }

                        if (isThinking.get()) {
                            thinkingAcc.append(chunk);
                            return Flux.just(CompareChunkDto.thinking(index, resolvedProvider, resolvedModelId, chunk));
                        } else {
                            contentAcc.append(chunk);
                            return Flux.just(CompareChunkDto.text(index, resolvedProvider, resolvedModelId, chunk));
                        }
                    })
                    .concatWith(Flux.defer(() -> {
                        long totalDuration = Math.max(1L, System.currentTimeMillis() - startTime);
                        long finalTtft = (ttftMs.get() > 0) ? ttftMs.get() : totalDuration;
                        int tokensCount = estimateTokens(contentAcc.toString() + thinkingAcc.toString());
                        long generationDuration = Math.max(1L, totalDuration - finalTtft);
                        double tps = (tokensCount * 1000.0) / generationDuration;
                        double roundedTps = Math.round(tps * 10.0) / 10.0;

                        return Flux.just(
                                CompareChunkDto.metrics(
                                        index,
                                        resolvedProvider,
                                        resolvedModelId,
                                        finalTtft,
                                        totalDuration,
                                        roundedTps,
                                        tokensCount),
                                CompareChunkDto.done(index, resolvedProvider, resolvedModelId));
                    }))
                    .subscribeOn(Schedulers.boundedElastic())
                    .onErrorResume(e -> {
                        log.warn("模型 [{} / {}] 流式比对异常: {}", resolvedProvider, resolvedModelId, e.getMessage());
                        return Flux.just(CompareChunkDto.error(
                                index, resolvedProvider, resolvedModelId, "模型响应异常: " + e.getMessage()));
                    });
        });
    }

    /**
     * 并行非流式多模型比对。
     */
    public Mono<CompareResponseDto> compare(CompareRequest request, String userId) {
        if (request == null || request.prompt() == null || request.prompt().isBlank()) {
            return Mono.error(new IllegalArgumentException("对比提示词不能为空"));
        }
        List<CompareRequest.ModelTarget> targets = (request.models() != null
                        && !request.models().isEmpty())
                ? request.models().subList(0, Math.min(3, request.models().size()))
                : List.of();

        if (targets.isEmpty()) {
            return Mono.error(new IllegalArgumentException("请至少指定一个对比模型"));
        }

        String userPrompt = buildPromptWithContext(request, userId);
        String systemPrompt =
                (request.systemPrompt() != null && !request.systemPrompt().isBlank())
                        ? request.systemPrompt()
                        : "你是一个高效、精准的 AI 专家助手。请针对用户的要求给出专业、条理清晰的高质量回答。";

        List<Mono<ModelCompareResult>> monos = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            final int index = i;
            final CompareRequest.ModelTarget target = targets.get(i);
            monos.add(Mono.fromCallable(() -> {
                        long start = System.currentTimeMillis();
                        try {
                            ResolvedModel model = providerRegistry.resolve(target.provider(), target.model());
                            String response = model.chatClient()
                                    .prompt()
                                    .system(systemPrompt)
                                    .user(userPrompt)
                                    .call()
                                    .content();
                            long duration = Math.max(1L, System.currentTimeMillis() - start);
                            int tokens = estimateTokens(response);
                            double tps = Math.round(((tokens * 1000.0) / duration) * 10.0) / 10.0;
                            return new ModelCompareResult(
                                    index,
                                    model.provider().providerId(),
                                    model.model().id(),
                                    response != null ? response : "",
                                    null,
                                    duration / 3, // 估算 TTFT
                                    duration,
                                    tps,
                                    tokens,
                                    null);
                        } catch (Exception ex) {
                            long duration = Math.max(1L, System.currentTimeMillis() - start);
                            log.warn("模型调用失败: {}/{}", target.provider(), target.model(), ex);
                            return new ModelCompareResult(
                                    index,
                                    target.provider(),
                                    target.model(),
                                    null,
                                    null,
                                    null,
                                    duration,
                                    null,
                                    null,
                                    ex.getMessage());
                        }
                    })
                    .subscribeOn(Schedulers.boundedElastic()));
        }

        return Mono.zip(monos, objects -> {
            List<ModelCompareResult> list = new ArrayList<>();
            for (Object obj : objects) {
                list.add((ModelCompareResult) obj);
            }
            return new CompareResponseDto(request.prompt(), System.currentTimeMillis(), list);
        });
    }
}
