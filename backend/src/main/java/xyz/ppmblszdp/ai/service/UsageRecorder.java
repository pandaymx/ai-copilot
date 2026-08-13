package xyz.ppmblszdp.ai.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import xyz.ppmblszdp.ai.dto.ChatChunkDto;
import xyz.ppmblszdp.ai.dto.ChatRequest;
import xyz.ppmblszdp.ai.dto.ChatResponseDto;
import xyz.ppmblszdp.ai.memory.UsageQuotaChecker;
import xyz.ppmblszdp.ai.memory.UsageQuotaChecker.UsageQuota;
import xyz.ppmblszdp.ai.registry.ModelDescriptor;
import xyz.ppmblszdp.ai.registry.ResolvedModel;
import xyz.ppmblszdp.ai.repository.UsageRepository;

/**
 * Token 用量统计、成本计算与 Redis 月度配额预扣/落库服务。
 *
 * <p>单向无环依赖叶子服务，不依赖 ChatService / ChatOrchestrator。
 */
@Service
public class UsageRecorder {

    private static final Logger log = LoggerFactory.getLogger(UsageRecorder.class);

    private final UsageRepository usageRepository;
    private final ObjectProvider<UsageQuota> usageQuota;

    public UsageRecorder(UsageRepository usageRepository, ObjectProvider<UsageQuota> usageQuota) {
        this.usageRepository = usageRepository;
        this.usageQuota = usageQuota;
    }

    /** 月度配额预扣：请求发起时无法预知真实 token 数，仅做“已用量 + 预扣值 > 上限”拦截。 */
    public boolean tryReserveMonthlyQuota(String userId, ResolvedModel resolved) {
        UsageQuota quota = usageQuota.getIfAvailable();
        if (quota == null) {
            return true;
        }
        return quota.tryReserve(userId);
    }

    /** 在 chunk 流上累加 usage（取末次非零 total 的 UsageDto），供 doFinally 统一落库。 */
    public Flux<ChatChunkDto> accumulateUsage(
            Flux<ChatChunkDto> chunkFlux, AtomicReference<ChatChunkDto.UsageDto> accum) {
        return chunkFlux.doOnNext(c -> {
            if (c != null
                    && "usage".equals(c.type())
                    && c.usage() != null
                    && c.usage().totalTokens() > 0) {
                accum.set(c.usage());
            }
        });
    }

    /** 月度配额耗尽时返回的统一错误响应（非流式）。 */
    public ChatResponseDto buildMonthlyQuotaExhaustedDto(ResolvedModel resolved, ChatRequest request) {
        log.warn(
                "月度 Token 配额耗尽，拒绝请求 → 供应商={}, 模型={}",
                resolved.provider().providerId(),
                resolved.model().id());
        return new ChatResponseDto(
                "本月对话额度已用尽，请下月再试或联系管理员提升配额。",
                resolved.provider().providerId(),
                resolved.model().id(),
                request.conversationId(),
                null,
                null);
    }

    /**
     * 用量落库 + 月度配额校准（核心）。异步执行，不阻塞主链路。
     * 仅在真实 token 数 > 0 时落库；cost 为 NULL 时兜底 ZERO。
     */
    public void settleUsage(
            String userId,
            ResolvedModel resolved,
            String conversationId,
            ChatChunkDto.UsageDto usage,
            Collection<Disposable> subscriptionTracker) {
        if (usage == null || usage.totalTokens() <= 0) {
            return;
        }
        String monthKey = UsageQuotaChecker.currentMonthKey();
        String providerId = resolved.provider().providerId();
        String modelId = resolved.model().id();
        int promptTokens = usage.promptTokens() > 0 ? usage.promptTokens() : 0;
        int completionTokens = usage.completionTokens() > 0 ? usage.completionTokens() : 0;
        int totalTokens = usage.totalTokens();
        BigDecimal costRmb =
                (usage.estimatedCostRmb() != null) ? BigDecimal.valueOf(usage.estimatedCostRmb()) : BigDecimal.ZERO;

        // 1) 异步落库用量（失败仅告警）
        var saveSub = Mono.fromRunnable(() -> usageRepository.saveUsage(
                        userId,
                        providerId,
                        modelId,
                        conversationId,
                        promptTokens,
                        completionTokens,
                        totalTokens,
                        costRmb,
                        monthKey))
                .onErrorComplete(ex -> {
                    log.warn("用量落库失败 [user={}, model={}]: {}", userId, modelId, ex.getMessage());
                    return true;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
        if (subscriptionTracker != null) {
            subscriptionTracker.add(saveSub);
        }

        // 2) 事后校准月度配额（净增量 = 真实 - 预扣值）
        var calibrateSub = Mono.fromRunnable(() -> {
                    UsageQuota quota = usageQuota.getIfAvailable();
                    if (quota != null) {
                        quota.consumeActual(userId, totalTokens);
                    }
                })
                .onErrorComplete(ex -> {
                    log.warn("月度配额校准失败 [user={}]: {}", userId, ex.getMessage());
                    return true;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
        if (subscriptionTracker != null) {
            subscriptionTracker.add(calibrateSub);
        }
    }

    /** 提取 ChatResponse 中的 UsageDto (包含 Prompt / Completion / Total Tokens 及预估费用)。 */
    public ChatChunkDto.UsageDto extractUsageDto(ChatResponse resp, ResolvedModel resolved) {
        if (resp == null || resp.getMetadata() == null || resp.getMetadata().getUsage() == null) {
            log.trace("LLM 响应未包含 Usage 元数据");
            return null;
        }
        var u = resp.getMetadata().getUsage();
        int prompt = u.getPromptTokens() != null ? u.getPromptTokens().intValue() : 0;
        int completion =
                u.getCompletionTokens() != null ? u.getCompletionTokens().intValue() : 0;
        int total = u.getTotalTokens() != null ? u.getTotalTokens().intValue() : (prompt + completion);
        if (total == 0) {
            log.debug("LLM 响应包含 Usage 元数据但 Token 用量全 0 (首包/中间块)，跳过生成 UsageDto");
            return null;
        }

        ModelDescriptor descriptor = (resolved != null) ? resolved.model() : null;
        BigDecimal inputPrice = (descriptor != null && descriptor.inputPricePerK() != null)
                ? descriptor.inputPricePerK()
                : ModelDescriptor.DEFAULT_INPUT_PRICE;
        BigDecimal outputPrice = (descriptor != null && descriptor.outputPricePerK() != null)
                ? descriptor.outputPricePerK()
                : ModelDescriptor.DEFAULT_OUTPUT_PRICE;

        BigDecimal promptCost = BigDecimal.valueOf(prompt)
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP)
                .multiply(inputPrice);

        BigDecimal completionCost = BigDecimal.valueOf(completion)
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP)
                .multiply(outputPrice);

        BigDecimal totalCostRmb = promptCost.add(completionCost).setScale(4, RoundingMode.HALF_UP);
        return new ChatChunkDto.UsageDto(prompt, completion, total, totalCostRmb.doubleValue());
    }
}
