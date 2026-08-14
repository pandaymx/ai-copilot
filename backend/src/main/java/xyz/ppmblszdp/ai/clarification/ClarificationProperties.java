package xyz.ppmblszdp.ai.clarification;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 主动澄清功能配置属性。
 */
@ConfigurationProperties(prefix = "app.ai.clarification")
public class ClarificationProperties {

    /**
     * 是否全局开启主动澄清功能
     */
    private boolean enabled = true;

    /**
     * 默认工作模式 (SOFT / STRICT / DISABLED)
     */
    private ClarificationMode defaultMode = ClarificationMode.SOFT;

    /**
     * Agent 模式下的默认工作模式（建议 STRICT，确保工具调用参数完备）
     */
    private ClarificationMode agentMode = ClarificationMode.STRICT;

    /**
     * 单次主动澄清生成的最大追问问题数
     */
    private int maxQuestions = 3;

    /**
     * 自动跳过主动澄清的斜杠命令前缀列表
     */
    private List<String> skipCommands = new ArrayList<>(
            List.of("/code", "/translate", "/trans", "/write", "/search", "/math", "/image", "/chat", "/analysis"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ClarificationMode getDefaultMode() {
        return defaultMode;
    }

    public void setDefaultMode(ClarificationMode defaultMode) {
        this.defaultMode = defaultMode;
    }

    public ClarificationMode getAgentMode() {
        return agentMode;
    }

    public void setAgentMode(ClarificationMode agentMode) {
        this.agentMode = agentMode;
    }

    public int getMaxQuestions() {
        return maxQuestions;
    }

    public void setMaxQuestions(int maxQuestions) {
        this.maxQuestions = maxQuestions;
    }

    public List<String> getSkipCommands() {
        return skipCommands;
    }

    public void setSkipCommands(List<String> skipCommands) {
        this.skipCommands = skipCommands;
    }
}
