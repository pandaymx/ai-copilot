package xyz.ppmblszdp.ai.rag;

import jakarta.annotation.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Name;

/**
 * RAG 文档多源解析与检索管道配置（绑定 {@code app.ai.rag.*}）。
 *
 * <p>独立 pgvector 表 {@code ai_rag_documents}，与长期记忆物理隔离。
 * 默认 {@code enabled=false}，开启后才能挂接 RagAdvisor 到对话链路并暴露入库接口。
 *
 * @param enabled       RAG 总开关；关闭时 RagAdvisor 不挂接、入库接口不可用
 * @param topK          文档相似检索 Top-K
 * @param chunkSize     TokenTextSplitter 每片 Token 数
 * @param overlap       相邻切片重叠 Token 数（≈ 20% chunk-size），防止上下文在切片边界丢失
 * @param encodingType  TokenTextSplitter 分词编码（CL100K_BASE / P50K_BASE / O200K_BASE）
 * @param collectionName 独立 pgvector 表名
 * @param ssrf           URL 抓取 SSRF/DoS 防护配置
 */
@ConfigurationProperties(prefix = "app.ai.rag")
public record RagProperties(
        @Nullable Boolean enabled,
        @Name("top-k") @Nullable Integer topK,
        @Name("chunk-size") @Nullable Integer chunkSize,
        @Nullable Integer overlap,
        @Name("encoding-type") @Nullable String encodingType,
        @Name("collection-name") @Nullable String collectionName,
        @Name("hybrid-search-enabled") @Nullable Boolean hybridSearchEnabled,
        @Name("rerank-enabled") @Nullable Boolean rerankEnabled,
        @Name("extraction-enabled") @Nullable Boolean extractionEnabled,
        @Name("rrf-k") @Nullable Integer rrfK,
        @Name("candidate-pool-multiplier") @Nullable Integer candidatePoolMultiplier,
        @Nullable SsrfConfig ssrf
) {

    public boolean isEnabled() {
        return enabled != null && enabled;
    }

    public boolean isHybridSearchEnabled() {
        return hybridSearchEnabled == null || hybridSearchEnabled;
    }

    public boolean isRerankEnabled() {
        return rerankEnabled != null && rerankEnabled;
    }

    public boolean isExtractionEnabled() {
        return extractionEnabled == null || extractionEnabled;
    }

    public int resolveRrfK() {
        return (rrfK != null && rrfK > 0) ? rrfK : 60;
    }

    public int resolveCandidatePoolMultiplier() {
        return (candidatePoolMultiplier != null && candidatePoolMultiplier > 0) ? candidatePoolMultiplier : 3;
    }

    public int resolveTopK() {
        return (topK != null && topK > 0) ? topK : 4;
    }

    public int resolveChunkSize() {
        return (chunkSize != null && chunkSize > 0) ? chunkSize : 900;
    }

    public int resolveOverlap() {
        return (overlap != null && overlap >= 0) ? overlap : 180;
    }

    public String resolveEncodingType() {
        return (encodingType != null && !encodingType.isBlank()) ? encodingType.trim() : "CL100K_BASE";
    }

    public String resolveCollectionName() {
        return (collectionName != null && !collectionName.isBlank()) ? collectionName.trim() : "ai_rag_documents";
    }

    public SsrfConfig resolveSsrf() {
        return ssrf != null ? ssrf : SsrfConfig.defaults();
    }

    /**
     * URL 抓取 SSRF/DoS 防护配置。
     *
     * @param timeoutSeconds 连接超时（秒），防 Slowloris
     * @param maxBodyBytes   响应体上限（字节），防 Zip Bomb
     */
    public record SsrfConfig(
            @Name("timeout-seconds") @Nullable Integer timeoutSeconds,
            @Name("max-body-bytes") @Nullable Long maxBodyBytes
    ) {
        public static SsrfConfig defaults() {
            return new SsrfConfig(5, 10_485_760L); // 5s / 10MB
        }

        public int resolveTimeoutSeconds() {
            return (timeoutSeconds != null && timeoutSeconds > 0) ? timeoutSeconds : 5;
        }

        public long resolveMaxBodyBytes() {
            return (maxBodyBytes != null && maxBodyBytes > 0) ? maxBodyBytes : 10_485_760L;
        }
    }
}
