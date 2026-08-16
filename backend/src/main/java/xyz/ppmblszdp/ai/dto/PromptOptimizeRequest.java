package xyz.ppmblszdp.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Prompt 优化请求体。
 *
 * <p>无状态分析请求：仅携带待优化的原始 Prompt 文本与可选的深度优化开关，
 * 不包含任何用户身份信息（userId 由服务端受信任链路 {@code UserIdentityFilter} 注入）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PromptOptimizeRequest {

    /** 待优化的原始 Prompt 文本。 */
    @JsonProperty("prompt")
    private String prompt;

    /**
     * 是否启用深度优化。
     *
     * <p>{@code true} 时升级到用户所选模型（推理能力更强的 T3 级）以获得更高质量的重写；
     * {@code false} 或缺失时使用最便宜的低成本模型（T2），兼顾成本与响应速度。
     */
    @JsonProperty("depth")
    private Boolean depth;

    public PromptOptimizeRequest() {}

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public Boolean getDepth() {
        return depth;
    }

    public void setDepth(Boolean depth) {
        this.depth = depth;
    }
}
