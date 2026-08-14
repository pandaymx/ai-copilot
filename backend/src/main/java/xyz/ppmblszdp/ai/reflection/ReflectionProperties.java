package xyz.ppmblszdp.ai.reflection;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 自我反思与纠错（Self-Reflection & Correction）配置属性。
 */
@ConfigurationProperties(prefix = "ai.reflection")
public class ReflectionProperties {

    /** 是否启用 AI 自我反思与纠错 */
    private boolean enabled = true;

    /** 是否在发现矛盾或缺失时自动向回复追加纠错补充说明 */
    private boolean autoCorrectionEnabled = true;

    /** 触发自我反思的最小回复字符长度（避免简短问候产生额外开销） */
    private int minContentLength = 40;

    /** 反思判定超时时间（毫秒） */
    private long timeoutMs = 4000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAutoCorrectionEnabled() {
        return autoCorrectionEnabled;
    }

    public void setAutoCorrectionEnabled(boolean autoCorrectionEnabled) {
        this.autoCorrectionEnabled = autoCorrectionEnabled;
    }

    public int getMinContentLength() {
        return minContentLength;
    }

    public void setMinContentLength(int minContentLength) {
        this.minContentLength = minContentLength;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
