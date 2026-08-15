package xyz.ppmblszdp.ai.compare.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 多模型并行流式 Chunk 数据包。
 *
 * @param modelIndex 模型在对比列表中的索引 (0, 1, 2)
 * @param provider 供应商标识 (如 openai, deepseek, ollama)
 * @param model 模型名 (如 gpt-4o, deepseek-chat)
 * @param chunkType 数据帧类型: "text" | "thinking" | "metrics" | "error" | "done"
 * @param content 文本增量或思考增量
 * @param ttftMs 首字延迟毫秒数
 * @param totalDurationMs 总生成耗时毫秒数
 * @param tokensPerSecond 生成速率 (Tokens/s)
 * @param tokensCount 已生成或估算的 Token 数量
 * @param error 错误信息（若发生单模型故障）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompareChunkDto(
        int modelIndex,
        String provider,
        String model,
        String chunkType,
        String content,
        Long ttftMs,
        Long totalDurationMs,
        Double tokensPerSecond,
        Integer tokensCount,
        String error) {

    public static CompareChunkDto text(int index, String provider, String model, String text) {
        return new CompareChunkDto(index, provider, model, "text", text, null, null, null, null, null);
    }

    public static CompareChunkDto thinking(int index, String provider, String model, String thinking) {
        return new CompareChunkDto(index, provider, model, "thinking", thinking, null, null, null, null, null);
    }

    public static CompareChunkDto metrics(
            int index,
            String provider,
            String model,
            Long ttftMs,
            Long totalDurationMs,
            Double tokensPerSec,
            Integer tokensCount) {
        return new CompareChunkDto(
                index, provider, model, "metrics", null, ttftMs, totalDurationMs, tokensPerSec, tokensCount, null);
    }

    public static CompareChunkDto error(int index, String provider, String model, String errorMessage) {
        return new CompareChunkDto(index, provider, model, "error", null, null, null, null, null, errorMessage);
    }

    public static CompareChunkDto done(int index, String provider, String model) {
        return new CompareChunkDto(index, provider, model, "done", null, null, null, null, null, null);
    }
}
