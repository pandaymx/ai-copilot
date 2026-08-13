package xyz.ppmblszdp.ai.context;

import java.util.List;
import org.springframework.ai.chat.messages.Message;

/**
 * 默认启发式 Token 估算器。
 *
 * <p>策略（保守偏大，宁可少发也不超窗）：
 * <ul>
 *   <li>CJK 字符（含中文/日文/韩文）≈ 1.0 token/字；</li>
 *   <li>ASCII 字符 ≈ 4 字符/token（即 0.25 token/字），涵盖英文与标点；</li>
 *   <li>每条消息附加固定角色开销（补偿角色/分隔符等隐式 token）；</li>
 *   <li>整体乘安全系数后向上取整。</li>
 * </ul>
 * 对中文混合英文场景较为贴近实际，且对中文偏保守。
 */
public class HeuristicTokenEstimator implements TokenEstimator {

    private static final double CJK_PER_CHAR = 1.0d;
    private static final double ASCII_PER_CHAR = 0.25d;
    private static final int ROLE_OVERHEAD = 4;
    private final double safetyFactor;

    public HeuristicTokenEstimator(double safetyFactor) {
        this.safetyFactor = Math.max(1.0d, safetyFactor);
    }

    @Override
    public int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        double tokens = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isCjk(c)) {
                tokens += CJK_PER_CHAR;
            } else {
                tokens += ASCII_PER_CHAR;
            }
        }
        tokens += ROLE_OVERHEAD;
        return (int) Math.ceil(tokens * safetyFactor);
    }

    @Override
    public int estimate(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (Message m : messages) {
            String content = (m.getText() == null) ? "" : m.getText();
            total += estimate(content);
        }
        return total;
    }

    private boolean isCjk(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        if (block == null) {
            return false;
        }
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA
                || block == Character.UnicodeBlock.HANGUL_SYLLABLES
                || block == Character.UnicodeBlock.HANGUL_JAMO;
    }
}
