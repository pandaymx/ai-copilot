package xyz.ppmblszdp.ai.clarification;

import java.util.List;

/**
 * 主动澄清评估结果。
 *
 * @param isAmbiguous          是否属于模糊/缺失关键要素的提问
 * @param mode                 执行模式 (STRICT / SOFT / DISABLED)
 * @param category             模糊场景类别
 * @param missingAspects       缺失的关键信息要素列表
 * @param clarificationMessage 格式化生成的主动澄清提问文案
 */
public record ClarificationAssessment(
        boolean isAmbiguous,
        ClarificationMode mode,
        String category,
        List<String> missingAspects,
        String clarificationMessage) {

    public static final String CLARIFICATION_MARKER = "<!-- CLARIFICATION_PROMPT -->";

    /**
     * 构建清晰/无需澄清的放行评估结果
     */
    public static ClarificationAssessment clear(ClarificationMode mode) {
        return new ClarificationAssessment(false, mode, "CLEAR", List.of(), null);
    }

    /**
     * 构建模糊评估结果
     */
    public static ClarificationAssessment ambiguous(
            ClarificationMode mode, String category, List<String> missingAspects, String message) {
        return new ClarificationAssessment(true, mode, category, missingAspects, message);
    }
}
