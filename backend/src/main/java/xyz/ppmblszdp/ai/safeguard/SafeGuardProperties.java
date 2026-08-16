package xyz.ppmblszdp.ai.safeguard;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 安全防护配置项。
 */
@ConfigurationProperties(prefix = "app.ai.safeguard")
public class SafeGuardProperties {

    /**
     * 是否开启 SafeGuardAdvisor 安全防护
     */
    private boolean enabled = true;

    /**
     * 是否开启语义级 Prompt 注入与越狱检测
     */
    private boolean semanticEnabled = false;

    /**
     * 前置 Request 处置策略 (默认 BLOCK)
     */
    private ActionPolicy requestPolicy = ActionPolicy.BLOCK;

    /**
     * 后置 Response 处置策略 (默认 MASK)
     */
    private ActionPolicy responsePolicy = ActionPolicy.MASK;

    /**
     * BLOCK 阻断时的默认安全响应提示
     */
    private String blockMessage = "【安全提示】您的输入或模型回复触发了系统安全防护规则，已被安全拦截。";

    /**
     * MASK 脱敏时的替换掩码
     */
    private String maskReplacement = "***";

    /**
     * 默认敏感词列表
     */
    private List<String> sensitiveWords =
            new ArrayList<>(List.of("暴恐", "炸药", "毒品", "枪支", "色情", "赌博", "机密", "绝密", "违规词"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSemanticEnabled() {
        return semanticEnabled;
    }

    public void setSemanticEnabled(boolean semanticEnabled) {
        this.semanticEnabled = semanticEnabled;
    }

    public ActionPolicy getRequestPolicy() {
        return requestPolicy;
    }

    public void setRequestPolicy(ActionPolicy requestPolicy) {
        this.requestPolicy = requestPolicy;
    }

    public ActionPolicy getResponsePolicy() {
        return responsePolicy;
    }

    public void setResponsePolicy(ActionPolicy responsePolicy) {
        this.responsePolicy = responsePolicy;
    }

    public String getBlockMessage() {
        return blockMessage;
    }

    public void setBlockMessage(String blockMessage) {
        this.blockMessage = blockMessage;
    }

    public String getMaskReplacement() {
        return maskReplacement;
    }

    public void setMaskReplacement(String maskReplacement) {
        this.maskReplacement = maskReplacement;
    }

    public List<String> getSensitiveWords() {
        return sensitiveWords;
    }

    public void setSensitiveWords(List<String> sensitiveWords) {
        this.sensitiveWords = sensitiveWords;
    }
}
