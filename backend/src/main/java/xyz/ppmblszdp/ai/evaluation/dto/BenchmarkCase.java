package xyz.ppmblszdp.ai.evaluation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 评测基准用例（Benchmark Case）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BenchmarkCase(
        String id,
        String title,
        String category,
        String prompt,
        String expectedOutput,
        String context,
        List<String> tags,
        Long createdAt) {

    public BenchmarkCase(
            String id,
            String title,
            String category,
            String prompt,
            String expectedOutput,
            String context,
            List<String> tags) {
        this(id, title, category, prompt, expectedOutput, context, tags, System.currentTimeMillis());
    }
}
