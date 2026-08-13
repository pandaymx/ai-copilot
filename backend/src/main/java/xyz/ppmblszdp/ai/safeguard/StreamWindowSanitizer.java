package xyz.ppmblszdp.ai.safeguard;

/**
 * 流式跨 Chunk 脱敏滑动窗口缓冲处理器 (Stream Window Sanitizer)。
 *
 * <p>
 * 解决流式输出（SSE/Flux）中敏感词跨 Chunk 截断（如 chunk1 为 "违规"，chunk2 为 "词"）导致独立匹配失效的问题。
 * 算法原理：在全量 buffer 拼接上整体执行脱敏打码，仅下发安全的前缀部分，将重叠尾部保留在 buffer 中与下一个 Chunk 联合匹配。
 */
public class StreamWindowSanitizer {

    private final SafeGuardEngine engine;
    private final ActionPolicy policy;
    private final int windowOverlap;
    private final StringBuilder buffer = new StringBuilder();

    public StreamWindowSanitizer(SafeGuardEngine engine, ActionPolicy policy) {
        this(engine, policy, 10);
    }

    public StreamWindowSanitizer(SafeGuardEngine engine, ActionPolicy policy, int minWindowSize) {
        this.engine = engine;
        this.policy = policy;
        int maxWordLen = engine.getSensitiveWordMatcher() != null
                ? engine.getSensitiveWordMatcher().getMaxWordLength()
                : 0;
        // 窗口重叠长度：至少取 minWindowSize 与敏感词最大长度之最大者
        this.windowOverlap = Math.max(minWindowSize, maxWordLen);
    }

    /**
     * 接收新的流式分片文本，返回当前滑动窗口脱敏后可下发的前缀文本。
     *
     * @param chunkText 新到来的分片文本
     * @return 脱敏后的安全前缀文本（可能为空串，等待后续分片凑满窗口）
     */
    public synchronized String processChunk(String chunkText) {
        if (chunkText == null || chunkText.isEmpty()) {
            return "";
        }

        buffer.append(chunkText);

        if (buffer.length() <= windowOverlap) {
            return "";
        }

        int processLength = buffer.length() - windowOverlap;

        // 在当前累积的全量 buffer 上执行整体脱敏打码
        SafeGuardCheckResult result = engine.inspectResponse(buffer.toString(), policy);
        String sanitizedFull = result.getProcessedText();

        // 截取安全前缀下发，剩余部分更新为 buffer 尾部
        String safePrefix;
        if (sanitizedFull.length() >= processLength) {
            safePrefix = sanitizedFull.substring(0, processLength);
            buffer.setLength(0);
            buffer.append(sanitizedFull.substring(processLength));
        } else {
            safePrefix = sanitizedFull;
            buffer.setLength(0);
        }

        return safePrefix;
    }

    /**
     * 流式响应完成 (onComplete) 时刷新剩余 Buffer 文本并做最终脱敏。
     *
     * @return 最终剩余 Buffer 脱敏后的文本
     */
    public synchronized String flush() {
        if (buffer.length() == 0) {
            return "";
        }
        String remaining = buffer.toString();
        buffer.setLength(0);
        SafeGuardCheckResult result = engine.inspectResponse(remaining, policy);
        return result.getProcessedText();
    }
}
