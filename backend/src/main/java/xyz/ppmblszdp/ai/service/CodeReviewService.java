package xyz.ppmblszdp.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;
import xyz.ppmblszdp.ai.registry.TaskKey;
import xyz.ppmblszdp.ai.registry.TaskModelResolver;
import xyz.ppmblszdp.ai.tool.dto.CodeReviewDto;
import xyz.ppmblszdp.ai.tool.dto.CodeReviewDto.CodeReviewFinding;
import xyz.ppmblszdp.ai.tool.dto.CodeReviewDto.CodeReviewReport;
import xyz.ppmblszdp.ai.tool.dto.CodeReviewDto.Level;

/**
 * 代码审查引擎。
 *
 * <p>流水线：静态规则硬检查（确定性、零延迟）→ LLM 多维度主审（结构化输出）→ 合并去重排序。
 * 静态规则用于覆盖明显安全问题（硬编码凭据、SQL 拼接等），避免 LLM 漏判；LLM 负责
 * 风格、复杂度、最佳实践等需要语义理解的部分。两者结果按 {@link CodeReviewFinding#dedupeKey()}
 * 模糊分桶合并，静态规则命中时优先保留静态 {@code ruleId} 与 critical 级别，并将 LLM 分析补充进 suggestion。
 */
@Service
public class CodeReviewService {

    private static final Logger log = LoggerFactory.getLogger(CodeReviewService.class);

    private static final int MAX_FINDINGS = 50;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProviderRegistry providerRegistry;
    private final TaskModelResolver taskModelResolver;

    /** 沙箱工作区根目录：用于解析仅传 filePath 时的本地文件绝对路径，并拦截路径遍历。 */
    @Value("${ai.codereview.workspace-base-dir:${java.io.tmpdir}/ai-copilot/workspaces}")
    private String workspaceBaseDir;

    public CodeReviewService(ProviderRegistry providerRegistry, TaskModelResolver taskModelResolver) {
        this.providerRegistry = providerRegistry;
        this.taskModelResolver = taskModelResolver;
    }

    /** 工作区上下文：仅传 filePath 时用于定位与读取本地文件，附带防遍历校验。 */
    public record WorkspaceContext(String workspaceId, String gitUrl) {}

    /** 原始审查请求。 */
    public record ReviewRequest(
            String codeSnippet, String gitDiff, String filePath, String language, String relativePath, String scope) {}

    /**
     * 执行完整审查流水线。
     *
     * @param req     审查请求（codeSnippet / gitDiff / filePath 至少其一非空）
     * @param wsCtx   工作区上下文（filePath 模式需要，可为 null）
     * @return 结构化报告（任何异常均降级为单条 suggestion，不中断调用链）
     */
    public CodeReviewReport review(ReviewRequest req, WorkspaceContext wsCtx) {
        long start = System.currentTimeMillis();
        String targetLabel = resolveTargetLabel(req, wsCtx);
        try {
            String code = resolveCode(req, wsCtx);
            if (code == null || code.isBlank()) {
                return CodeReviewReport.empty("未提供有效代码内容，无法进行代码审查（请提供 codeSnippet / gitDiff / filePath）。");
            }

            List<CodeReviewFinding> findings = new ArrayList<>();
            findings.addAll(runStaticChecks(code, targetLabel));
            int staticCount = findings.size();

            List<CodeReviewFinding> llmFindings = runLlmReview(code, req, targetLabel);
            findings.addAll(llmFindings);

            boolean truncated = findings.size() > MAX_FINDINGS;
            List<CodeReviewFinding> merged =
                    mergeAndSort(truncated ? findings.subList(0, MAX_FINDINGS) : findings, staticCount);

            int c = count(merged, Level.CRITICAL);
            int w = count(merged, Level.WARNING);
            int s = count(merged, Level.SUGGESTION);
            String summary = buildSummary(merged, c, w, s, targetLabel);
            List<String> suggestedTests = extractSuggestedTests(merged);

            log.info(
                    "CodeReview 完成 target={} 静态={} llm={} 合并={} 耗时={}ms",
                    targetLabel,
                    staticCount,
                    llmFindings.size(),
                    merged.size(),
                    System.currentTimeMillis() - start);
            return new CodeReviewReport(summary, c, w, s, truncated, merged, suggestedTests);
        } catch (Exception e) {
            log.warn("CodeReview 审查异常（降级为单条建议）: {}", e.getMessage());
            return CodeReviewReport.empty("代码审查过程中发生异常，已降级为安全提示：" + e.getMessage());
        }
    }

    // ---------------------------------------------------------------------
    // 代码解析与沙箱路径处理
    // ---------------------------------------------------------------------

    private String resolveTargetLabel(ReviewRequest req, WorkspaceContext wsCtx) {
        if (req.filePath() != null && !req.filePath().isBlank()) {
            return req.filePath();
        }
        if (req.relativePath() != null && !req.relativePath().isBlank()) {
            return req.relativePath();
        }
        if (wsCtx != null && wsCtx.workspaceId() != null) {
            return "workspace:" + wsCtx.workspaceId();
        }
        return "inline-snippet";
    }

    /**
     * 解析实际待审查代码。优先级：codeSnippet > gitDiff > filePath（需经沙箱根解析，禁止路径遍历）。
     */
    private String resolveCode(ReviewRequest req, WorkspaceContext wsCtx) throws IOException {
        if (req.codeSnippet() != null && !req.codeSnippet().isBlank()) {
            return req.codeSnippet();
        }
        if (req.gitDiff() != null && !req.gitDiff().isBlank()) {
            return req.gitDiff();
        }
        if (req.filePath() != null && !req.filePath().isBlank()) {
            Path root = resolveWorkspaceRoot(wsCtx);
            Path target = root.resolve(req.filePath()).normalize();
            if (!target.startsWith(root)) {
                throw new SecurityException("路径遍历被拒绝: " + req.filePath());
            }
            if (!Files.exists(target) || !Files.isRegularFile(target)) {
                throw new IllegalArgumentException("工作区内找不到文件: " + req.filePath());
            }
            return Files.readString(target);
        }
        return null;
    }

    /** 解析沙箱工作区根目录：优先按 workspaceId 定位，否则回退到基础目录。 */
    private Path resolveWorkspaceRoot(WorkspaceContext wsCtx) {
        Path base = Paths.get(workspaceBaseDir).toAbsolutePath().normalize();
        if (wsCtx != null && wsCtx.workspaceId() != null && !wsCtx.workspaceId().isBlank()) {
            Path root = base.resolve(sanitizeId(wsCtx.workspaceId())).normalize();
            if (root.startsWith(base)) {
                return root;
            }
        }
        return base;
    }

    private static String sanitizeId(String id) {
        return id.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    // ---------------------------------------------------------------------
    // 静态规则硬检查
    // ---------------------------------------------------------------------

    private static final Pattern[] SECRET_PATTERNS = {
        Pattern.compile(
                "(?i)(password|passwd|pwd|secret|token|api[_-]?key|access[_-]?key|private[_-]?key)\\s*[:=]\\s*[\"']([^\"']{6,})[\"']"),
        Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._-]{20,}"),
        Pattern.compile("(?i)-----BEGIN\\s+(RSA|EC|OPENSSH)?\\s*PRIVATE KEY-----"),
    };

    private static final Pattern[] SQL_CONCAT_PATTERNS = {
        Pattern.compile("(?i)(select|insert|update|delete).*\\+\\s*[\"']"),
        Pattern.compile("(?i)\"\\s*\\+\\s*(request|req|input|param|args?).*\\+"),
        Pattern.compile("(?i)execute\\s*\\(\\s*[\"'][^\"']*\\{\\d\\}"),
    };

    private static final Pattern[] DANGEROUS_CALL_PATTERNS = {
        Pattern.compile(
                "(?i)\\b(eval|exec|os\\.system|subprocess|child_process\\.exec|Runtime\\.getRuntime\\(\\)\\.exec)\\s*\\("),
        Pattern.compile("(?i)(innerHTML|dangerouslySetInnerHTML)\\s*="),
    };

    private List<CodeReviewFinding> runStaticChecks(String code, String target) {
        List<CodeReviewFinding> out = new ArrayList<>();
        for (Pattern p : SECRET_PATTERNS) {
            out.addAll(matchLines(
                    code, target, p, Level.CRITICAL, "安全漏洞", "检测到疑似硬编码凭据/密钥，应迁移至环境变量或密钥管理服务。", "SECRET_HARDCODED"));
        }
        for (Pattern p : SQL_CONCAT_PATTERNS) {
            out.addAll(matchLines(
                    code, target, p, Level.WARNING, "安全漏洞", "检测到疑似 SQL 字符串拼接，存在注入风险，应使用参数化查询/预编译语句。", "SQL_CONCAT"));
        }
        for (Pattern p : DANGEROUS_CALL_PATTERNS) {
            out.addAll(matchLines(
                    code,
                    target,
                    p,
                    Level.WARNING,
                    "安全漏洞",
                    "检测到危险的动态执行/不安全的 HTML 注入，可能造成代码执行或 XSS，需严格校验输入或改用安全 API。",
                    "DANGEROUS_CALL"));
        }
        return out;
    }

    private List<CodeReviewFinding> matchLines(
            String code, String target, Pattern p, Level level, String category, String suggestion, String ruleId) {
        List<CodeReviewFinding> out = new ArrayList<>();
        String[] lines = code.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher m = p.matcher(lines[i]);
            if (m.find()) {
                // 命中处仅记录 ruleId + 文件，不记录代码全文/密钥值（脱敏，同 GitTool 处理）
                out.add(CodeReviewFinding.of(
                        level, toCategory(category), target, i + 1, "静态规则命中：" + ruleId, suggestion, ruleId));
            }
        }
        return out;
    }

    private static CodeReviewDto.Category toCategory(String label) {
        for (CodeReviewDto.Category c : CodeReviewDto.Category.values()) {
            if (c.getLabel().equals(label)) {
                return c;
            }
        }
        return CodeReviewDto.Category.BEST_PRACTICE;
    }

    // ---------------------------------------------------------------------
    // LLM 多维度主审
    // ---------------------------------------------------------------------

    private static final String REVIEW_SYSTEM_PROMPT = "你是一名资深代码审查专家。请对提供的代码进行严格、客观、可操作的审查。\n"
            + "审查维度必须覆盖：1)安全漏洞 2)性能问题 3)代码风格 4)最佳实践 5)复杂度。\n"
            + "每行发现必须定位到具体文件与行号；若输入为 Git Diff，行号必须对应变更后新文件的实际行号。\n"
            + "仅报告确有依据的问题，避免主观吹毛求疵。\n"
            + "在 findings 后追加 suggestedTests：列出 2-5 个可由自动化测试覆盖的关键验证点（如边界条件、异常路径、安全校验）。\n"
            + "只输出 JSON，不要使用 markdown 代码围栏（```json），不要任何额外解释文字。";

    private List<CodeReviewFinding> runLlmReview(String code, ReviewRequest req, String target) {
        try {
            ResolvedModel resolved = taskModelResolver.resolve(TaskKey.CODE_REVIEW);
            ChatClient chatClient = resolved.chatClient();

            BeanOutputConverter<CodeReviewReport> converter =
                    new BeanOutputConverter<>(new ParameterizedTypeReference<CodeReviewReport>() {});
            String formatInstruction = converter.getFormat();

            String scope = req.scope() != null ? req.scope() : "整体审查";
            String lang = req.language() != null ? req.language() : "未指定";
            String userPrompt = String.format("语言: %s\n范围: %s\n目标: %s\n\n待审查内容:\n%s", lang, scope, target, code);

            String response = chatClient
                    .prompt()
                    .system(REVIEW_SYSTEM_PROMPT + "\n" + formatInstruction)
                    .user(userPrompt)
                    .call()
                    .content();

            if (response == null || response.isBlank()) {
                return List.of();
            }
            return parseLlmReport(response);
        } catch (Exception e) {
            log.warn("LLM 代码审查异常（返回空）: {}", e.getMessage());
            return List.of();
        }
    }

    /** Fallback 容错链：BeanOutputConverter 解析 → ObjectMapper 裸 JSON → 单条降级。 */
    private List<CodeReviewFinding> parseLlmReport(String response) {
        try {
            BeanOutputConverter<CodeReviewReport> converter =
                    new BeanOutputConverter<>(new ParameterizedTypeReference<CodeReviewReport>() {});
            CodeReviewReport report = converter.convert(response);
            if (report != null && report.findings() != null) {
                return report.findings();
            }
        } catch (Exception ignored) {
            // 继续降级路径
        }
        try {
            String json = extractJsonBlock(response);
            Map<String, Object> root = objectMapper.readValue(
                    json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            Object f = root.get("findings");
            if (f instanceof List<?> list) {
                List<CodeReviewFinding> out = new ArrayList<>();
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                        out.add(mapToFinding(m, targetFromMap(m)));
                    }
                }
                return out;
            }
        } catch (Exception e) {
            log.warn("LLM 报告 JSON 解析失败，降级为单条建议: {}", e.getMessage());
        }
        // 最终降级：保留原始文本为单条 suggestion，确保流程不中断
        return List.of(CodeReviewFinding.of(
                Level.SUGGESTION,
                CodeReviewDto.Category.BEST_PRACTICE,
                null,
                null,
                "LLM 审查结果无法结构化解析，已保留原始文本：",
                response.length() > 2000 ? response.substring(0, 2000) : response,
                "LLM_REVIEW"));
    }

    private static String targetFromMap(Map<?, ?> m) {
        Object f = m.get("file");
        return f instanceof String s && !s.isBlank() ? s : null;
    }

    private CodeReviewFinding mapToFinding(Map<?, ?> m, String target) {
        String level = asString(m.get("level"), Level.SUGGESTION.name());
        String category = asString(m.get("category"), CodeReviewDto.Category.BEST_PRACTICE.getLabel());
        Integer line = m.get("line") instanceof Number n ? n.intValue() : null;
        String message = asString(m.get("message"), "");
        String suggestion = asString(m.get("suggestion"), "");
        return new CodeReviewFinding(normalizeLevel(level), category, target, line, message, suggestion, "LLM_REVIEW");
    }

    private static String normalizeLevel(String level) {
        if (level == null) {
            return Level.SUGGESTION.name();
        }
        String l = level.trim().toUpperCase();
        for (Level lv : Level.values()) {
            if (lv.name().equals(l) || l.contains(lv.name())) {
                return lv.name();
            }
        }
        return Level.SUGGESTION.name();
    }

    private static String asString(Object o, String def) {
        return o == null ? def : o.toString();
    }

    private static String extractJsonBlock(String text) {
        if (text == null) {
            return "{}";
        }
        int fence = text.indexOf("```");
        if (fence >= 0) {
            int start = text.indexOf('{', fence);
            int end = text.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return text.substring(start, end + 1);
            }
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return "{}";
    }

    // ---------------------------------------------------------------------
    // 合并去重排序
    // ---------------------------------------------------------------------

    /**
     * 合并：以静态规则结果为准（同桶内优先保留静态 critical），并将 LLM 同桶分析的 suggestion 补充进来。
     * 去重键见 {@link CodeReviewFinding#dedupeKey()}（file + line/5 + category 模糊分桶）。
     */
    private List<CodeReviewFinding> mergeAndSort(List<CodeReviewFinding> findings, int staticCount) {
        Map<String, CodeReviewFinding> merged = new LinkedHashMap<>();
        // 先放静态结果（0..staticCount-1），再放 LLM 结果，静态优先覆盖同键
        for (int i = 0; i < staticCount && i < findings.size(); i++) {
            CodeReviewFinding f = findings.get(i);
            merged.put(f.dedupeKey(), f);
        }
        for (int i = staticCount; i < findings.size(); i++) {
            CodeReviewFinding f = findings.get(i);
            CodeReviewFinding existing = merged.get(f.dedupeKey());
            if (existing == null) {
                merged.put(f.dedupeKey(), f);
            } else {
                // 同桶：保留静态 ruleId 与更严重的级别，补充 LLM 建议
                String suggestion =
                        existing.suggestion() + " | LLM补充：" + (f.suggestion() == null ? "" : f.suggestion());
                merged.put(
                        f.dedupeKey(),
                        new CodeReviewFinding(
                                existing.level(),
                                existing.category(),
                                existing.file(),
                                existing.line(),
                                existing.message(),
                                suggestion,
                                existing.ruleId()));
            }
        }
        List<CodeReviewFinding> list = new ArrayList<>(merged.values());
        list.sort((a, b) -> Integer.compare(levelRank(b.level()), levelRank(a.level())));
        return list;
    }

    private static int levelRank(String level) {
        if (Level.CRITICAL.name().equals(level)) {
            return 0;
        }
        if (Level.WARNING.name().equals(level)) {
            return 1;
        }
        return 2;
    }

    private static int count(List<CodeReviewFinding> list, Level level) {
        int c = 0;
        for (CodeReviewFinding f : list) {
            if (level.name().equals(f.level())) {
                c++;
            }
        }
        return c;
    }

    private static List<String> extractSuggestedTests(List<CodeReviewFinding> findings) {
        // 由 LLM 报告层直接提供；此处从 finding 的 suggestion 中抽取“测试”相关建议作为兜底
        List<String> tests = new ArrayList<>();
        for (CodeReviewFinding f : findings) {
            if (f.suggestion() != null && f.suggestion().toLowerCase().contains("测试")) {
                tests.add("[" + (f.file() != null ? f.file() : "?") + "] " + f.suggestion());
            }
        }
        return tests;
    }

    private static String buildSummary(List<CodeReviewFinding> merged, int c, int w, int s, String target) {
        if (merged.isEmpty()) {
            return "未发现问题，代码质量良好（目标：" + target + "）。";
        }
        return String.format(
                "对 %s 完成审查，共发现 %d 项问题（critical=%d, warning=%d, suggestion=%d）。建议优先处理 critical 级别问题。",
                target, merged.size(), c, w, s);
    }
}
