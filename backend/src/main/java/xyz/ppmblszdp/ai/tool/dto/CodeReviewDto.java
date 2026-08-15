package xyz.ppmblszdp.ai.tool.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.List;

/**
 * 代码审查的结构化结果载体。
 *
 * <p>{@link CodeReviewReport} 为整体报告，对齐前端 {@code CodeReviewRenderer} 的解析字段；
 * {@link CodeReviewFinding} 为单条审查发现。所有可空字段（file/line）在序列化时按
 * {@link JsonInclude.Include#NON_NULL} 省略，保证 LLM 结构化输出与静态规则结果字段一致。
 */
@JsonInclude(Include.NON_NULL)
public final class CodeReviewDto {

    private CodeReviewDto() {}

    /** 审查级别：critical（必须修复） / warning（应当修复） / suggestion（可选优化）。 */
    public enum Level {
        CRITICAL,
        WARNING,
        SUGGESTION
    }

    /** 审查维度分类。 */
    public enum Category {
        SECURITY("安全漏洞"),
        PERFORMANCE("性能问题"),
        STYLE("代码风格"),
        BEST_PRACTICE("最佳实践"),
        COMPLEXITY("复杂度");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /**
     * 单条审查发现。
     *
     * @param level      严重级别（critical/warning/suggestion）
     * @param category   审查维度（安全漏洞/性能问题/代码风格/最佳实践/复杂度）
     * @param file       相对文件路径（可为 null，表示整体/未知）
     * @param line       新文件中的实际行号（可为 null，对于 Git Diff 应为变更后行号）
     * @param message    问题描述
     * @param suggestion 修复建议（可包含代码示例）
     * @param ruleId     来源标识：静态规则 id（如 SECRET_HARDCODED）或 LLM 来源标记（如 LLM_REVIEW）
     */
    public record CodeReviewFinding(
            String level,
            String category,
            String file,
            Integer line,
            String message,
            String suggestion,
            String ruleId) {

        public static CodeReviewFinding of(
                Level level,
                Category category,
                String file,
                Integer line,
                String message,
                String suggestion,
                String ruleId) {
            return new CodeReviewFinding(
                    level == null ? null : level.name(),
                    category == null ? null : category.getLabel(),
                    file,
                    line,
                    message,
                    suggestion,
                    ruleId);
        }

        /** 去重键：file + (line/5) + category 的模糊分桶，降低邻近行/重复类别的误报。 */
        public String dedupeKey() {
            int bucket = (line == null) ? -1 : (line / 5);
            return (file == null ? "*" : file) + "|" + bucket + "|" + (category == null ? "*" : category);
        }
    }

    /**
     * 整体结构化报告。
     *
     * @param summary          报告总览摘要（自然语言）
     * @param criticalCount    critical 数量
     * @param warningCount     warning 数量
     * @param suggestionCount  suggestion 数量
     * @param truncated        是否因长度/数量上限被截断
     * @param findings         发现列表（已按严重度排序）
     * @param suggestedTests   建议测试点（供 Agent 决定是否调用 code_execution 生成并执行）
     */
    public record CodeReviewReport(
            String summary,
            int criticalCount,
            int warningCount,
            int suggestionCount,
            boolean truncated,
            List<CodeReviewFinding> findings,
            List<String> suggestedTests) {

        public static CodeReviewReport empty(String summary) {
            return new CodeReviewReport(summary, 0, 0, 0, false, List.of(), List.of());
        }
    }
}
