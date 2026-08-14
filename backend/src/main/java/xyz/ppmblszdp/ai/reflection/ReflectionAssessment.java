package xyz.ppmblszdp.ai.reflection;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * AI 自我反思与纠错评估结果。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReflectionAssessment(
        boolean passed,
        Double factualityScore,
        Double completenessScore,
        List<String> issues,
        String correctionExplanation,
        String supplementalCorrection) {

    public static ReflectionAssessment ofPassed() {
        return new ReflectionAssessment(true, 1.0, 1.0, List.of(), null, null);
    }

    public static ReflectionAssessment needsCorrection(
            Double factuality, Double completeness, List<String> issues, String explanation, String supplemental) {
        return new ReflectionAssessment(
                false,
                factuality != null ? factuality : 0.7,
                completeness != null ? completeness : 0.7,
                issues != null ? issues : List.of(),
                explanation,
                supplemental);
    }
}
