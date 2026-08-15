package xyz.ppmblszdp.ai.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import xyz.ppmblszdp.ai.dto.TranslateRequestDto;
import xyz.ppmblszdp.ai.dto.TranslateResponseDto;
import xyz.ppmblszdp.ai.factory.ChatOptionsFactory;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;

/**
 * 多语言翻译引擎核心服务：
 * <ul>
 *   <li><b>格式隔离与无损恢复 (FormatPreserver)</b>：自动将代码块、行内代码与 LaTeX 公式替换为安全占位符，翻译后无歧义回填；</li>
 *   <li><b>智能语种探测 (LanguageDetector)</b>：基于 Unicode 字符集特征的高性能启发式语种扫描；</li>
 *   <li><b>术语表强制约束 (GlossaryMapper)</b>：在 Prompt 注入专业术语映射并对译文统计命中频次；</li>
 *   <li><b>LLM 驱动与统一回落</b>：基于 {@link ProviderRegistry} 路由模型，温度设为低发散度 (0.1) 保障高保真翻译。</li>
 * </ul>
 */
@Service
public class TranslationService {

    private static final Logger log = LoggerFactory.getLogger(TranslationService.class);

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```[\\s\\S]*?```");
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`[^`\\n]+`");
    private static final Pattern MATH_DISPLAY_PATTERN = Pattern.compile("\\$\\$[\\s\\S]*?\\$\\$");
    private static final Pattern MATH_INLINE_PATTERN = Pattern.compile("\\$[^$\\n]+?\\$");

    private final ProviderRegistry providerRegistry;

    public TranslationService(ProviderRegistry providerRegistry) {
        this.providerRegistry = providerRegistry;
    }

    /**
     * 执行多语言翻译。
     */
    public TranslateResponseDto translate(TranslateRequestDto request) {
        long startTime = System.currentTimeMillis();
        if (request == null || request.text() == null || request.text().isBlank()) {
            return new TranslateResponseDto(
                    "", "auto", request != null ? request.targetLang() : "zh-CN", "unknown", "", 0, 0);
        }

        String rawText = request.text();
        String targetLang =
                (request.targetLang() != null && !request.targetLang().isBlank())
                        ? request.targetLang().trim()
                        : "zh-CN";

        // 1. 智能探测或确定源语言
        String detectedLang = detectLanguage(rawText);
        String sourceLang = (request.sourceLang() != null
                        && !request.sourceLang().isBlank()
                        && !"auto".equalsIgnoreCase(request.sourceLang()))
                ? request.sourceLang().trim()
                : detectedLang;

        // 若源语言与目标语言完全一致且无术语表要求，直接返回
        if (sourceLang.equalsIgnoreCase(targetLang)
                && (request.glossary() == null || request.glossary().isEmpty())) {
            long latencyMs = System.currentTimeMillis() - startTime;
            return new TranslateResponseDto(rawText, sourceLang, targetLang, detectedLang, rawText, 0, latencyMs);
        }

        // 2. 格式保护：提取代码块与数学公式占位符
        boolean preserve = request.preserveFormatting() == null || Boolean.TRUE.equals(request.preserveFormatting());
        ProtectedContent protectedContent = preserve
                ? extractProtectedPlaceholders(rawText)
                : new ProtectedContent(rawText, Collections.emptyMap());

        // 3. 构建 Prompt
        String systemInstruction = buildSystemInstruction(sourceLang, targetLang, request.glossary(), preserve);
        ResolvedModel resolved = providerRegistry.resolve(request.provider(), request.model());
        ChatOptions chatOptions = ChatOptionsFactory.forProvider(resolved, 0.1);

        List<Message> messages =
                List.of(new SystemMessage(systemInstruction), new UserMessage(protectedContent.processedText()));
        Prompt prompt = new Prompt(messages, chatOptions);

        // 4. 调用 LLM
        ChatResponse chatResponse = resolved.chatModel().call(prompt);
        String rawTranslated = "";
        if (chatResponse != null
                && chatResponse.getResult() != null
                && chatResponse.getResult().getOutput() != null) {
            rawTranslated = chatResponse.getResult().getOutput().getText();
        }
        if (rawTranslated == null) {
            rawTranslated = "";
        }

        // 5. 格式还原：回填占位符
        String finalTranslated =
                preserve ? restoreProtectedPlaceholders(rawTranslated, protectedContent.placeholders()) : rawTranslated;

        // 6. 统计术语表命中数
        int glossaryCount = countGlossaryMatches(finalTranslated, request.glossary());
        long latencyMs = System.currentTimeMillis() - startTime;

        log.debug(
                "翻译完成: [{} -> {}] 原文长度={} 耗时={}ms 术语命中={}",
                sourceLang,
                targetLang,
                rawText.length(),
                latencyMs,
                glossaryCount);

        return new TranslateResponseDto(
                rawText, sourceLang, targetLang, detectedLang, finalTranslated.trim(), glossaryCount, latencyMs);
    }

    /**
     * Unicode 字符集启发式语种探测。
     */
    public String detectLanguage(String text) {
        if (text == null || text.isBlank()) {
            return "unknown";
        }
        int cjkCount = 0;
        int kanaCount = 0;
        int hangulCount = 0;
        int cyrillicCount = 0;
        int arabicCount = 0;
        int totalValidChars = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) {
                continue;
            }
            totalValidChars++;
            Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
            if (block == Character.UnicodeBlock.HIRAGANA || block == Character.UnicodeBlock.KATAKANA) {
                kanaCount++;
            } else if (block == Character.UnicodeBlock.HANGUL_SYLLABLES
                    || block == Character.UnicodeBlock.HANGUL_JAMO) {
                hangulCount++;
            } else if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A) {
                cjkCount++;
            } else if (block == Character.UnicodeBlock.CYRILLIC) {
                cyrillicCount++;
            } else if (block == Character.UnicodeBlock.ARABIC) {
                arabicCount++;
            }
        }

        if (totalValidChars == 0) {
            return "en";
        }

        if (kanaCount > 0) {
            return "ja";
        }
        if (hangulCount > 0) {
            return "ko";
        }
        if (cjkCount > 0 && ((double) cjkCount / totalValidChars > 0.15)) {
            return "zh-CN";
        }
        if (cyrillicCount > 0 && ((double) cyrillicCount / totalValidChars > 0.2)) {
            return "ru";
        }
        if (arabicCount > 0 && ((double) arabicCount / totalValidChars > 0.2)) {
            return "ar";
        }

        return "en";
    }

    /**
     * 提取代码块、行内代码与数学公式占位符。
     */
    ProtectedContent extractProtectedPlaceholders(String text) {
        if (text == null || text.isBlank()) {
            return new ProtectedContent(text, Collections.emptyMap());
        }
        Map<String, String> placeholders = new LinkedHashMap<>();
        int index = 0;

        // 1. 代码块 ```...```
        Matcher codeBlockMatcher = CODE_BLOCK_PATTERN.matcher(text);
        StringBuilder sb1 = new StringBuilder();
        while (codeBlockMatcher.find()) {
            String key = "__PROTECTED_CODE_BLOCK_" + (index++) + "__";
            placeholders.put(key, codeBlockMatcher.group());
            codeBlockMatcher.appendReplacement(sb1, Matcher.quoteReplacement(key));
        }
        codeBlockMatcher.appendTail(sb1);
        String step1 = sb1.toString();

        // 2. LaTeX 块级公式 $$...$$
        Matcher mathDisplayMatcher = MATH_DISPLAY_PATTERN.matcher(step1);
        StringBuilder sb2 = new StringBuilder();
        while (mathDisplayMatcher.find()) {
            String key = "__PROTECTED_MATH_DISPLAY_" + (index++) + "__";
            placeholders.put(key, mathDisplayMatcher.group());
            mathDisplayMatcher.appendReplacement(sb2, Matcher.quoteReplacement(key));
        }
        mathDisplayMatcher.appendTail(sb2);
        String step2 = sb2.toString();

        // 3. LaTeX 行内公式 $...$
        Matcher mathInlineMatcher = MATH_INLINE_PATTERN.matcher(step2);
        StringBuilder sb3 = new StringBuilder();
        while (mathInlineMatcher.find()) {
            String key = "__PROTECTED_MATH_INLINE_" + (index++) + "__";
            placeholders.put(key, mathInlineMatcher.group());
            mathInlineMatcher.appendReplacement(sb3, Matcher.quoteReplacement(key));
        }
        mathInlineMatcher.appendTail(sb3);
        String step3 = sb3.toString();

        // 4. 行内代码 `...`
        Matcher inlineCodeMatcher = INLINE_CODE_PATTERN.matcher(step3);
        StringBuilder sb4 = new StringBuilder();
        while (inlineCodeMatcher.find()) {
            String key = "__PROTECTED_INLINE_CODE_" + (index++) + "__";
            placeholders.put(key, inlineCodeMatcher.group());
            inlineCodeMatcher.appendReplacement(sb4, Matcher.quoteReplacement(key));
        }
        inlineCodeMatcher.appendTail(sb4);

        return new ProtectedContent(sb4.toString(), placeholders);
    }

    /**
     * 还原占位符。
     */
    String restoreProtectedPlaceholders(String translatedText, Map<String, String> placeholders) {
        if (translatedText == null || placeholders == null || placeholders.isEmpty()) {
            return translatedText;
        }
        String result = translatedText;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private String buildSystemInstruction(
            String sourceLang, String targetLang, Map<String, String> glossary, boolean preserve) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a professional multilingual translation engine.\n");
        sb.append("Translate the provided text faithfully, accurately, and naturally from ")
                .append(sourceLang)
                .append(" to ")
                .append(targetLang)
                .append(".\n\n");

        sb.append("CRITICAL REQUIREMENTS:\n");
        sb.append("1. Maintain all Markdown syntax, structural layout, bullet points, headers, and table formats.\n");
        if (preserve) {
            sb.append(
                    "2. DO NOT translate, modify, space out, or delete any placeholder tokens starting with __PROTECTED_ and ending with __ (e.g. __PROTECTED_CODE_BLOCK_0__). Keep every placeholder in its exact position.\n");
        }
        sb.append(
                "3. Provide ONLY the direct translation output. Do NOT include greetings, preamble, explanations, notes, or wrap the translation in markdown block quotes unless present in source.\n");

        if (glossary != null && !glossary.isEmpty()) {
            sb.append("\n[GLOSSARY CONSTRAINTS]\n");
            sb.append("Translate the following terms strictly as mapped:\n");
            for (Map.Entry<String, String> entry : glossary.entrySet()) {
                if (entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null) {
                    sb.append("- \"")
                            .append(entry.getKey().trim())
                            .append("\" -> \"")
                            .append(entry.getValue().trim())
                            .append("\"\n");
                }
            }
        }

        return sb.toString();
    }

    private int countGlossaryMatches(String translatedText, Map<String, String> glossary) {
        if (translatedText == null || glossary == null || glossary.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String targetTerm : glossary.values()) {
            if (targetTerm != null && !targetTerm.isBlank() && translatedText.contains(targetTerm.trim())) {
                count++;
            }
        }
        return count;
    }

    /**
     * 获取支持的标准语言列表。
     */
    public List<Map<String, String>> getSupportedLanguages() {
        return List.of(
                Map.of("code", "zh-CN", "name", "简体中文", "nativeName", "简体中文"),
                Map.of("code", "zh-TW", "name", "繁体中文", "nativeName", "繁體中文"),
                Map.of("code", "en", "name", "英语", "nativeName", "English"),
                Map.of("code", "ja", "name", "日语", "nativeName", "日本語"),
                Map.of("code", "ko", "name", "韩语", "nativeName", "한국어"),
                Map.of("code", "fr", "name", "法语", "nativeName", "Français"),
                Map.of("code", "de", "name", "德语", "nativeName", "Deutsch"),
                Map.of("code", "es", "name", "西班牙语", "nativeName", "Español"),
                Map.of("code", "ru", "name", "俄语", "nativeName", "Русский"),
                Map.of("code", "it", "name", "意大利语", "nativeName", "Italiano"),
                Map.of("code", "pt", "name", "葡萄牙语", "nativeName", "Português"),
                Map.of("code", "ar", "name", "阿拉伯语", "nativeName", "العربية"),
                Map.of("code", "vi", "name", "越南语", "nativeName", "Tiếng Việt"),
                Map.of("code", "th", "name", "泰语", "nativeName", "ไทย"));
    }

    record ProtectedContent(String processedText, Map<String, String> placeholders) {}
}
