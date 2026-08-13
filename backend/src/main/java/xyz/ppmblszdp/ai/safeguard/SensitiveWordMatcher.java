package xyz.ppmblszdp.ai.safeguard;

import java.util.Collection;

/**
 * 敏感词匹配抽象接口。
 * 解耦算法实现（如默认正则/字符串匹配与未来的 AC 自动机算法）。
 */
public interface SensitiveWordMatcher {

    /**
     * 校验文本中是否包含任意敏感词。
     */
    boolean containsAny(String text);

    /**
     * 对文本中的所有敏感词进行脱敏掩码替换。
     */
    String mask(String text, String replacement);

    /**
     * 动态重载敏感词库。
     */
    void reload(Collection<String> words);

    /**
     * 获取当前词库中最长敏感词的长度（用于流式滑动窗口计算）。
     */
    int getMaxWordLength();
}
