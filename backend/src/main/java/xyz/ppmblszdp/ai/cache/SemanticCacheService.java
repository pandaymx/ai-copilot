package xyz.ppmblszdp.ai.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import xyz.ppmblszdp.ai.memory.SafeEmbeddingModel;

/**
 * 语义缓存服务（5-#4）。
 *
 * <p>复用已有的 {@link SafeEmbeddingModel} 将用户请求文本编码为向量，在
 * {@link ChatOrchestrator} 调用 ChatClient 前做语义相似检索（命中即直接返回缓存响应），
 * 响应完成后异步写回缓存。缓存表由 {@link SemanticCacheRepository} 管理，按 user_id
 * 多租户隔离、TTL 7 天。
 *
 * <p>设计原则（与降级链不阻断对话一致）：
 * <ul>
 *   <li>embedding 失败 / 模型不可用 / 检索异常 → 一律当作「无命中」，不阻断主链路；</li>
 *   <li>写入失败仅记日志，绝不影响 SSE 流式输出（异步、零阻塞）；</li>
 *   <li>命中需同时满足「语义相似（余弦 ≥ 阈值）」与「请求精确哈希一致」，防跨模型误命中。</li>
 * </ul>
 */
@Service
public class SemanticCacheService {

    private static final Logger log = LoggerFactory.getLogger(SemanticCacheService.class);

    /** 余弦相似度阈值：≥ 0.92 视为同一语义问题。 */
    private static final double SIMILARITY_THRESHOLD = 0.92;

    private final ObjectProvider<SafeEmbeddingModel> embeddingProvider;
    private final SemanticCacheRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final boolean enabled;

    public SemanticCacheService(
            ObjectProvider<SafeEmbeddingModel> embeddingProvider,
            SemanticCacheRepository repository,
            @Value("${ai.semantic-cache.enabled:true}") boolean enabled) {
        this.embeddingProvider = embeddingProvider;
        this.repository = repository;
        this.enabled = enabled;
    }

    /** 全局语义缓存开关（由 {@code ai.semantic-cache.enabled} 控制）。 */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 检索语义相似的缓存响应。
     *
     * @return 命中的响应文本；无命中或任何异常时返回空（调用方应继续正常生成）
     */
    public Optional<String> lookUp(String userId, String message, String provider, String model) {
        SafeEmbeddingModel embedder = embeddingProvider.getIfAvailable();
        if (embedder == null || message == null || message.isBlank()) {
            return Optional.empty();
        }
        try {
            float[] vec = normalize(embedder.embed(message));
            String payload = repository.findSimilar(userId, vec, SIMILARITY_THRESHOLD);
            if (payload == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(extractContent(payload));
        } catch (Exception e) {
            log.debug("语义缓存检索失败，跳过: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 异步写回缓存（fire-and-forget，零阻塞 SSE 主链路）。
     *
     * @param userId   用户维度
     * @param message  用户请求文本
     * @param provider 实际模型供应商
     * @param model    实际模型 id
     * @param response 生成的响应文本
     */
    public void onComplete(String userId, String message, String provider, String model, String response) {
        if (response == null || response.isBlank()) {
            return;
        }
        SafeEmbeddingModel embedder = embeddingProvider.getIfAvailable();
        if (embedder == null) {
            return;
        }
        Mono.fromRunnable(() -> {
                    try {
                        float[] vec = normalize(embedder.embed(message));
                        String payload = objectMapper.writeValueAsString(Map.of("content", response));
                        repository.save(userId, vec, hash(message, provider, model), payload, provider, model);
                    } catch (Exception e) {
                        log.warn("语义缓存写入失败（忽略）: {}", e.getMessage());
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    private static String extractContent(String payload) {
        try {
            Map<?, ?> map = new ObjectMapper().readValue(payload, Map.class);
            Object content = map.get("content");
            return content != null ? content.toString() : null;
        } catch (Exception e) {
            return payload;
        }
    }

    private static String hash(String message, String provider, String model) {
        String src = (provider == null ? "" : provider) + "|" + (model == null ? "" : model) + "|" + message;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(src.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(src.hashCode());
        }
    }

    /** 归一化向量（L2），使 pgvector 的余弦距离 <#> 等价于 1 - cos_sim。 */
    private static float[] normalize(float[] vec) {
        double norm = 0.0;
        for (float v : vec) {
            norm += (double) v * v;
        }
        norm = Math.sqrt(norm);
        if (norm == 0.0) {
            return vec;
        }
        float[] out = new float[vec.length];
        for (int i = 0; i < vec.length; i++) {
            out[i] = (float) (vec[i] / norm);
        }
        return out;
    }
}
