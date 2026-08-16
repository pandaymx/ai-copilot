package xyz.ppmblszdp.ai.safeguard;

import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 语义级 Prompt 注入与对抗越狱检测器（SemanticInjectionDetector）。
 *
 * <p>识别 DAN 变体、角色扮演越狱、编码绕过、提示词泄露尝试等高级对抗攻击。
 */
@Component
public class SemanticInjectionDetector {

    private static final Logger log = LoggerFactory.getLogger(SemanticInjectionDetector.class);

    public record InjectionVerdict(boolean isInjected, String category, double confidence, String explanation) {

        public static InjectionVerdict clean() {
            return new InjectionVerdict(false, "NONE", 0.0, "未检测到对抗性攻击");
        }

        public static InjectionVerdict detected(String category, double confidence, String explanation) {
            return new InjectionVerdict(true, category, confidence, explanation);
        }
    }

    private static final List<Pattern> ADVANCED_ATTACK_PATTERNS = List.of(
            // DAN 变体与无限制模式
            Pattern.compile(
                    "(?i)(stay\\s+in\\s+character|do\\s+anything\\s+now|DAN\\s+mode|unfiltered\\s+mode|developer\\s+mode\\s+enabled|always\\s+answer)"),
            // 角色扮演越狱
            Pattern.compile(
                    "(?i)(you\\s+are\\s+an\\s+unrestricted|pretend\\s+to\\s+be\\s+an\\s+evil|hypothetical\\s+scenario\\s+without\\s+rules)"),
            // 系统提示词窃取 / 提取
            Pattern.compile(
                    "(?i)(print|repeat|output|show|leak)\\s+(the\\s+above|your\\s+initial|system)\\s+(prompt|instructions|rules)"),
            // 编码或伪装绕过
            Pattern.compile("(?i)(base64|rot13|caesar|hex|binary)\\s+(encoded|decode\\s+the\\s+following|string)"),
            // 指令覆盖
            Pattern.compile(
                    "(?i)(override\\s+all\\s+safety|forget\\s+all\\s+safety\\s+guidelines|bypass\\s+content\\s+policy)"));

    /**
     * 对 Prompt 进行语义与模式分类审查
     */
    public InjectionVerdict classify(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return InjectionVerdict.clean();
        }

        for (Pattern pattern : ADVANCED_ATTACK_PATTERNS) {
            if (pattern.matcher(prompt).find()) {
                String patternStr = pattern.pattern();
                String category = resolveCategory(patternStr);
                log.warn("🛡️ [SemanticDetector] 语义对抗攻击检测命中! 类别: {}, Pattern: {}", category, patternStr);
                return InjectionVerdict.detected(category, 0.95, "匹配对抗性攻击特征: " + category);
            }
        }

        // 启发式熵增与编码检测：如超长连续 Base64 / Hex 编码片段
        if (containsObfuscatedPayload(prompt)) {
            log.warn("🛡️ [SemanticDetector] 发现可疑混淆编码 Payload 绕过尝试");
            return InjectionVerdict.detected("ENCODED_OBFUSCATION", 0.88, "输入包含大量混淆编码 Payload");
        }

        return InjectionVerdict.clean();
    }

    private String resolveCategory(String pattern) {
        if (pattern.contains("DAN") || pattern.contains("do\\s+anything")) return "DAN_VARIANT";
        if (pattern.contains("character") || pattern.contains("pretend")) return "ROLEPLAY_JAILBREAK";
        if (pattern.contains("prompt") || pattern.contains("instructions")) return "PROMPT_EXTRACTION";
        if (pattern.contains("encoded") || pattern.contains("base64")) return "ENCODED_BYPASS";
        return "POLICY_OVERRIDE";
    }

    private boolean containsObfuscatedPayload(String text) {
        if (text.length() > 60) {
            // 检查是否存在疑似 Base64 长密文
            Pattern base64Block = Pattern.compile(
                    "(?i)(eyJ[a-zA-Z0-9_-]{20,}|(?:[A-Za-z0-9+/]{4}){10,}(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?)");
            return base64Block.matcher(text).find()
                    && (text.contains("decode") || text.contains("run") || text.contains("eval"));
        }
        return false;
    }
}
