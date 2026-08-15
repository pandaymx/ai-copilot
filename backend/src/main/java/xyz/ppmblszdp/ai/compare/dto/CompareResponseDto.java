package xyz.ppmblszdp.ai.compare.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 非流式多模型对比响应体。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompareResponseDto(String prompt, long timestamp, List<ModelCompareResult> results) {

    public record ModelCompareResult(
            int modelIndex,
            String provider,
            String model,
            String content,
            String thinking,
            Long ttftMs,
            Long totalDurationMs,
            Double tokensPerSecond,
            Integer tokensCount,
            String error) {}
}
