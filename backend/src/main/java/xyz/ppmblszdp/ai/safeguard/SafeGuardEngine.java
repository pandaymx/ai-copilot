package xyz.ppmblszdp.ai.safeguard;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 高性能安全检查与脱敏引擎。
 *
 * <p>
 * 特性：
 * <ul>
 * <li>所有的正则 Pattern (手机号、身份证、Email、Prompt Injection) 均统一声明为 {@code static final Pattern} 预编译，零运行期重复编译成本；</li>
 * <li>解耦 {@link SensitiveWordMatcher}，词库扩展或后续切换 AC 自动机算法时零代码侵入；</li>
 * <li>支持 BLOCK / MASK / LOG_ONLY 三级处置策略。</li>
 * </ul>
 */
public class SafeGuardEngine {

    private static final Logger log = LoggerFactory.getLogger(SafeGuardEngine.class);

    // ─────────────────────────────────────────────
    // 静态预编译正则表达式（高性能）
    // ─────────────────────────────────────────────

    /** 中国大陆手机号正则 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");

    /** 中国居民身份证正则 */
    private static final Pattern ID_CARD_PATTERN =
            Pattern.compile("[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dX]");

    /** 常用 Email 正则 */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    /** Prompt 注入攻击模式 */
    private static final List<Pattern> PROMPT_INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)ignore\\s+(all\\s+)?previous\\s+instructions"),
            Pattern.compile("(?i)system\\s+prompt\\s+(override|bypass|instructions)"),
            Pattern.compile("(?i)you\\s+are\\s+now\\s+DAN"),
            Pattern.compile("(?i)jailbreak\\s+(mode|prompt)"),
            Pattern.compile("(?i)disregard\\s+all\\s+prior\\s+rules"));

    private final SensitiveWordMatcher sensitiveWordMatcher;
    private final String maskReplacement;
    private final SemanticInjectionDetector semanticDetector;
    private final OutputQualityGate outputQualityGate;

    public SafeGuardEngine(
            SensitiveWordMatcher sensitiveWordMatcher,
            String maskReplacement,
            SemanticInjectionDetector semanticDetector,
            OutputQualityGate outputQualityGate) {
        this.sensitiveWordMatcher = sensitiveWordMatcher;
        this.maskReplacement = maskReplacement != null ? maskReplacement : "***";
        this.semanticDetector = semanticDetector != null ? semanticDetector : new SemanticInjectionDetector();
        this.outputQualityGate = outputQualityGate != null ? outputQualityGate : new OutputQualityGate();
    }

    public SafeGuardEngine(SensitiveWordMatcher sensitiveWordMatcher, String maskReplacement) {
        this(sensitiveWordMatcher, maskReplacement, new SemanticInjectionDetector(), new OutputQualityGate());
    }

    public SafeGuardEngine(List<String> sensitiveWords, String maskReplacement) {
        this(new DefaultSensitiveWordMatcher(sensitiveWords), maskReplacement);
    }

    /**
     * 前置 Request 检查
     *
     * @param text   用户输入的 Prompt 文本
     * @param policy 前置处置策略 (BLOCK / MASK / LOG_ONLY)
     * @return 检查结果
     */
    public SafeGuardCheckResult inspectRequest(String text, ActionPolicy policy) {
        return inspectRequest(text, policy, false);
    }

    /**
     * 前置 Request 检查（支持语义级增强检测）
     */
    public SafeGuardCheckResult inspectRequest(String text, ActionPolicy policy, boolean semanticEnabled) {
        if (text == null || text.isBlank()) {
            return SafeGuardCheckResult.clean(text);
        }

        // 1. 正则 Prompt 注入攻击检测
        for (Pattern p : PROMPT_INJECTION_PATTERNS) {
            if (p.matcher(text).find()) {
                log.warn("🚨 [SafeGuard-Request] 触发 Prompt 注入攻击拦截 Rule={}", p.pattern());
                if (policy == ActionPolicy.BLOCK) {
                    return SafeGuardCheckResult.triggered(
                            SafeGuardCheckResult.TriggerType.PROMPT_INJECTION, p.pattern(), text);
                }
            }
        }

        // 2. 语义级 Prompt 越狱与高级攻击分类检测
        if (semanticEnabled && semanticDetector != null) {
            var verdict = semanticDetector.classify(text);
            if (verdict.isInjected()) {
                log.warn("🚨 [SafeGuard-Request] 语义级对抗攻击拦截: 类别={}, 详情={}", verdict.category(), verdict.explanation());
                if (policy == ActionPolicy.BLOCK) {
                    return SafeGuardCheckResult.triggered(
                            SafeGuardCheckResult.TriggerType.PROMPT_INJECTION, "SEMANTIC_" + verdict.category(), text);
                }
            }
        }

        // 3. 敏感词及隐私检测
        return executeSanitization(text, policy, "Request");
    }

    /**
     * 后置 Response 处理（脱敏或阻断）
     *
     * @param text   大模型生成的回复文本
     * @param policy 后置处置策略 (BLOCK / MASK / LOG_ONLY)
     * @return 处理后的结果
     */
    public SafeGuardCheckResult inspectResponse(String text, ActionPolicy policy) {
        if (text == null || text.isBlank()) {
            return SafeGuardCheckResult.clean(text);
        }

        // 1. 输出内容质量与毒性门控审查
        if (outputQualityGate != null) {
            var verdict = outputQualityGate.inspect(text);
            if (!verdict.isSafe()) {
                log.warn("🚨 [SafeGuard-Response] 触发输出质量门控拦截: 类别={}, 详情={}", verdict.riskCategory(), verdict.detail());
                if (policy == ActionPolicy.BLOCK) {
                    return SafeGuardCheckResult.triggered(
                            SafeGuardCheckResult.TriggerType.SENSITIVE_WORD, "QUALITY_" + verdict.riskCategory(), text);
                }
            }
        }

        return executeSanitization(text, policy, "Response");
    }

    /**
     * 统一执行隐私正则与敏感词检测/脱敏替换
     */
    private SafeGuardCheckResult executeSanitization(String text, ActionPolicy policy, String stage) {
        boolean triggered = false;
        SafeGuardCheckResult.TriggerType firstType = SafeGuardCheckResult.TriggerType.NONE;
        String matchedRule = null;

        String processed = text;

        // A. 检查手机号
        Matcher phoneMatcher = PHONE_PATTERN.matcher(processed);
        if (phoneMatcher.find()) {
            triggered = true;
            firstType = SafeGuardCheckResult.TriggerType.PHONE;
            matchedRule = "PHONE_PATTERN";
            log.warn("⚠️ [SafeGuard-{}] 发现隐私泄漏 (手机号)", stage);
            if (policy == ActionPolicy.BLOCK) {
                return SafeGuardCheckResult.triggered(firstType, matchedRule, text);
            }
            if (policy == ActionPolicy.MASK) {
                processed = PHONE_PATTERN.matcher(processed).replaceAll(maskReplacement);
            }
        }

        // B. 检查身份证
        Matcher idMatcher = ID_CARD_PATTERN.matcher(processed);
        if (idMatcher.find()) {
            triggered = true;
            if (firstType == SafeGuardCheckResult.TriggerType.NONE) {
                firstType = SafeGuardCheckResult.TriggerType.ID_CARD;
                matchedRule = "ID_CARD_PATTERN";
            }
            log.warn("⚠️ [SafeGuard-{}] 发现隐私泄漏 (身份证)", stage);
            if (policy == ActionPolicy.BLOCK) {
                return SafeGuardCheckResult.triggered(firstType, matchedRule, text);
            }
            if (policy == ActionPolicy.MASK) {
                processed = ID_CARD_PATTERN.matcher(processed).replaceAll(maskReplacement);
            }
        }

        // C. 检查 Email
        Matcher emailMatcher = EMAIL_PATTERN.matcher(processed);
        if (emailMatcher.find()) {
            triggered = true;
            if (firstType == SafeGuardCheckResult.TriggerType.NONE) {
                firstType = SafeGuardCheckResult.TriggerType.EMAIL;
                matchedRule = "EMAIL_PATTERN";
            }
            log.warn("⚠️ [SafeGuard-{}] 发现隐私泄漏 (Email)", stage);
            if (policy == ActionPolicy.BLOCK) {
                return SafeGuardCheckResult.triggered(firstType, matchedRule, text);
            }
            if (policy == ActionPolicy.MASK) {
                processed = EMAIL_PATTERN.matcher(processed).replaceAll(maskReplacement);
            }
        }

        // D. 检查敏感词库
        if (sensitiveWordMatcher.containsAny(processed)) {
            triggered = true;
            if (firstType == SafeGuardCheckResult.TriggerType.NONE) {
                firstType = SafeGuardCheckResult.TriggerType.SENSITIVE_WORD;
                matchedRule = "SENSITIVE_WORD";
            }
            log.warn("⚠️ [SafeGuard-{}] 触发敏感词匹配", stage);
            if (policy == ActionPolicy.BLOCK) {
                return SafeGuardCheckResult.triggered(firstType, matchedRule, text);
            }
            if (policy == ActionPolicy.MASK) {
                processed = sensitiveWordMatcher.mask(processed, maskReplacement);
            }
        }

        if (triggered) {
            return SafeGuardCheckResult.triggered(firstType, matchedRule, processed);
        }
        return SafeGuardCheckResult.clean(processed);
    }

    public SensitiveWordMatcher getSensitiveWordMatcher() {
        return sensitiveWordMatcher;
    }
}
