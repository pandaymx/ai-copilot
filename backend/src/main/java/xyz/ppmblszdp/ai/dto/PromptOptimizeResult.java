package xyz.ppmblszdp.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;

/**
 * Prompt 优化结构化结果。
 *
 * <p>由 LLM 经 {@code BeanOutputConverter} 强类型反序列化得到，前端零解析直接消费。
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code score}：清晰度评分（0-100），越高表示原 Prompt 越清晰可执行。</li>
 *   <li>{@code missing}：结构化缺失项枚举列表，仅取受限枚举值（见下方约束），
 *       用于前端稳定渲染，避免 LLM 输出发散的自然语言。</li>
 *   <li>{@code issues}：具体的可操作修改建议（精炼短句，每项一条）。</li>
 *   <li>{@code optimized}：优化重写后的完整 Prompt，可直接回填输入框。</li>
 *   <li>{@code fewShot}：按需生成的 Few-shot 示例（0-3 条）。</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PromptOptimizeResult {

    /** 清晰度评分（0-100）。 */
    @JsonProperty("score")
    private int score;

    /**
     * 缺失项枚举列表，取值严格限定于：
     * {@code ROLE, CONTEXT, FORMAT, CONSTRAINTS, EXAMPLES, AUDIENCE, TONE}。
     * 若原 Prompt 各要素齐备则为空列表。
     */
    @JsonProperty("missing")
    private List<String> missing = Collections.emptyList();

    /** 具体修改建议（精炼短句，每项一条）。 */
    @JsonProperty("issues")
    private List<String> issues = Collections.emptyList();

    /** 优化重写后的完整 Prompt。 */
    @JsonProperty("optimized")
    private String optimized;

    /** 按需生成的 Few-shot 示例（0-3 条）。 */
    @JsonProperty("fewShot")
    private List<String> fewShot = Collections.emptyList();

    public PromptOptimizeResult() {}

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public List<String> getMissing() {
        return missing;
    }

    public void setMissing(List<String> missing) {
        this.missing = missing != null ? missing : Collections.emptyList();
    }

    public List<String> getIssues() {
        return issues;
    }

    public void setIssues(List<String> issues) {
        this.issues = issues != null ? issues : Collections.emptyList();
    }

    public String getOptimized() {
        return optimized;
    }

    public void setOptimized(String optimized) {
        this.optimized = optimized;
    }

    public List<String> getFewShot() {
        return fewShot;
    }

    public void setFewShot(List<String> fewShot) {
        this.fewShot = fewShot != null ? fewShot : Collections.emptyList();
    }
}
