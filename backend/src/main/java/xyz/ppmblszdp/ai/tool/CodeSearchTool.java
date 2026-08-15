package xyz.ppmblszdp.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import xyz.ppmblszdp.ai.memory.SafeEmbeddingModel;
import xyz.ppmblszdp.ai.safeguard.ActionPolicy;
import xyz.ppmblszdp.ai.safeguard.SafeGuardEngine;

/**
 * 代码语义与符号检索工具：提供代码全文/正则检索、自然语言语义代码块搜索、符号定义提取与目录树分析。
 *
 * <h3>熔断保护与安全设计</h3>
 * <ul>
 *   <li><b>目录树熔断保护</b>：限制 maxDepth（默认 3，最大 6）和 maxFiles（默认 200，最大 500），超出优雅折叠</li>
 *   <li><b>安全路径校验</b>：防止通过 repoName 或相对路径逃逸沙箱</li>
 *   <li><b>敏感信息脱敏</b>：输出代码片段通过 SafeGuardEngine 与内置 API Key 规则清洗</li>
 *   <li><b>语义搜索容错</b>：Embedding 模型可用时使用向量余弦相似度，离线时优雅降级至词频/TF-IDF 相似度</li>
 * </ul>
 */
@Component
public class CodeSearchTool {

    private static final Logger log = LoggerFactory.getLogger(CodeSearchTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Pattern SAFE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]+$");
    private static final long MAX_SEARCH_FILE_BYTES = 512 * 1024; // 512KB 单文件检索上限

    private static final Set<String> IGNORED_DIRS = Set.of(
            ".git", ".gradle", ".idea", "node_modules", "target", "build", ".next", "dist", "__pycache__", ".venv");

    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            "class", "jar", "war", "zip", "tar", "gz", "png", "jpg", "jpeg", "gif", "ico", "pdf", "exe", "bin", "mp4",
            "mp3", "woff", "woff2", "ttf");

    private final ObjectProvider<SafeGuardEngine> safeGuardEngineProvider;
    private final ObjectProvider<SafeEmbeddingModel> safeEmbeddingModelProvider;
    private final ObjectProvider<EmbeddingModel> genericEmbeddingModelProvider;

    public CodeSearchTool(
            ObjectProvider<SafeGuardEngine> safeGuardEngineProvider,
            ObjectProvider<SafeEmbeddingModel> safeEmbeddingModelProvider,
            ObjectProvider<EmbeddingModel> genericEmbeddingModelProvider) {
        this.safeGuardEngineProvider = safeGuardEngineProvider;
        this.safeEmbeddingModelProvider = safeEmbeddingModelProvider;
        this.genericEmbeddingModelProvider = genericEmbeddingModelProvider;
    }

    @Tool(description = "在已克隆的代码仓库中执行正则或关键字全文搜索，返回匹配的代码行号与上下文代码片段")
    public String codeSearchRegex(
            @ToolParam(description = "本地仓库别名，如 my-project") String repoName,
            @ToolParam(description = "搜索的正则表达式或普通关键字，如 @ConditionalOnProperty 或 function handleSubmit") String pattern,
            @ToolParam(description = "可选，限定文件后缀或 glob 模式，如 .java 或 .tsx") String filePattern,
            @ToolParam(description = "可选，最大返回结果数，默认 15，最大 50") Integer maxResults,
            ToolContext toolContext) {

        String argsJson =
                toJson(Map.of("repoName", repoName == null ? "" : repoName, "pattern", pattern == null ? "" : pattern));
        return ToolEventEmitter.from(toolContext).executeWithEvent("code_search_regex", argsJson, toolContext, () -> {
            Path repoDir = getExistingRepo(toolContext, repoName);
            if (pattern == null || pattern.isBlank()) {
                throw new IllegalArgumentException("pattern 搜索关键字不能为空");
            }

            Pattern regex;
            try {
                regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
            } catch (Exception e) {
                // 若正则解析失败，自动降级为转义字面量匹配
                regex = Pattern.compile(Pattern.quote(pattern), Pattern.CASE_INSENSITIVE);
            }

            int limit = (maxResults != null && maxResults > 0) ? Math.min(maxResults, 50) : 15;
            List<CodeMatchResult> matches = new ArrayList<>();

            List<Path> codeFiles = collectCodeFiles(repoDir, filePattern, 1000);
            for (Path file : codeFiles) {
                if (matches.size() >= limit) break;
                searchInFile(repoDir, file, regex, matches, limit);
            }

            if (matches.isEmpty()) {
                return "{\"output\":\"未找到匹配的代码片段 (pattern: " + pattern + ")\"}";
            }

            StringBuilder sb = new StringBuilder("🔍 共找到 " + matches.size() + " 处匹配:\n\n");
            for (CodeMatchResult m : matches) {
                sb.append("📄 ")
                        .append(m.relativePath())
                        .append(":")
                        .append(m.lineNumber())
                        .append("\n");
                sb.append("```\n").append(m.snippet()).append("\n```\n\n");
            }

            return toOutputJson(sanitizeOutput(sb.toString()));
        });
    }

    @Tool(description = "在代码仓库中进行自然语言语义搜索（通过向量相似度计算），找出与描述最相关的代码块或模块（如：'用户登录身份验证的拦截器'）")
    public String codeSearchSemantic(
            @ToolParam(description = "本地仓库别名") String repoName,
            @ToolParam(description = "自然语言查询描述，如：'查找处理文件上传的 Controller' 或 'JWT 密钥验证逻辑'") String query,
            @ToolParam(description = "可选，限定文件类型，如 .java") String filePattern,
            @ToolParam(description = "可选，返回的最相关代码片段数，默认 5，最大 15") Integer topK,
            ToolContext toolContext) {

        String argsJson =
                toJson(Map.of("repoName", repoName == null ? "" : repoName, "query", query == null ? "" : query));
        return ToolEventEmitter.from(toolContext)
                .executeWithEvent("code_search_semantic", argsJson, toolContext, () -> {
                    Path repoDir = getExistingRepo(toolContext, repoName);
                    if (query == null || query.isBlank()) {
                        throw new IllegalArgumentException("query 语义查询不能为空");
                    }

                    int limit = (topK != null && topK > 0) ? Math.min(topK, 15) : 5;
                    List<Path> codeFiles = collectCodeFiles(repoDir, filePattern, 300);

                    List<CodeChunk> chunks = new ArrayList<>();
                    for (Path file : codeFiles) {
                        chunks.addAll(chunkCodeFile(repoDir, file));
                    }

                    if (chunks.isEmpty()) {
                        return "{\"output\":\"代码库中未扫描到有效文本源码文件\"}";
                    }

                    // 优先利用向量模型进行语义余弦打分
                    EmbeddingModel model = resolveEmbeddingModel();
                    List<ScoredCodeChunk> scored = new ArrayList<>();

                    if (model != null) {
                        try {
                            float[] queryVec = model.embed(query);
                            for (CodeChunk c : chunks) {
                                float[] chunkVec = model.embed(c.content());
                                double sim = cosineSimilarity(queryVec, chunkVec);
                                scored.add(new ScoredCodeChunk(c, sim));
                            }
                        } catch (Exception ex) {
                            log.warn("向量化语义搜索降级为关键词打分: {}", ex.getMessage());
                            scored = keywordScoreFallback(chunks, query);
                        }
                    } else {
                        scored = keywordScoreFallback(chunks, query);
                    }

                    scored.sort(Comparator.comparingDouble((ScoredCodeChunk s) -> s.score())
                            .reversed());
                    List<ScoredCodeChunk> topResults =
                            scored.stream().limit(limit).toList();

                    StringBuilder sb = new StringBuilder("💡 语义相关度 Top " + topResults.size() + " 代码片段:\n\n");
                    for (ScoredCodeChunk r : topResults) {
                        sb.append("📄 ")
                                .append(r.chunk.relativePath)
                                .append(" (行 ")
                                .append(r.chunk.startLine)
                                .append("-")
                                .append(r.chunk.endLine)
                                .append(")")
                                .append(" - 相似度: ")
                                .append(String.format("%.2f", r.score * 100))
                                .append("%\n");
                        sb.append("```\n").append(r.chunk.content).append("\n```\n\n");
                    }

                    return toOutputJson(sanitizeOutput(sb.toString()));
                });
    }

    @Tool(description = "在代码仓库中定位类、方法、函数或接口等符号（Symbol）的定义位置")
    public String codeFindSymbols(
            @ToolParam(description = "本地仓库别名") String repoName,
            @ToolParam(description = "要查找的符号名称，如 UserService 或 generateToken") String symbolName,
            @ToolParam(description = "可选，符号类型过滤：ALL / CLASS / FUNCTION / INTERFACE，默认 ALL") String kind,
            ToolContext toolContext) {

        String argsJson = toJson(Map.of(
                "repoName", repoName == null ? "" : repoName, "symbolName", symbolName == null ? "" : symbolName));
        return ToolEventEmitter.from(toolContext).executeWithEvent("code_find_symbols", argsJson, toolContext, () -> {
            Path repoDir = getExistingRepo(toolContext, repoName);
            if (symbolName == null || symbolName.isBlank()) {
                throw new IllegalArgumentException("symbolName 符号名不能为空");
            }

            List<Path> codeFiles = collectCodeFiles(repoDir, null, 500);
            List<SymbolMatch> symbols = new ArrayList<>();

            Pattern symPattern = buildSymbolPattern(symbolName.trim(), kind);

            for (Path file : codeFiles) {
                if (symbols.size() >= 30) break;
                try {
                    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i);
                        Matcher m = symPattern.matcher(line);
                        if (m.find()) {
                            String relPath = repoDir.relativize(file).toString();
                            symbols.add(new SymbolMatch(relPath, i + 1, line.trim(), guessSymbolKind(line)));
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            if (symbols.isEmpty()) {
                return "{\"output\":\"未找到符号定义: " + symbolName + "\"}";
            }

            StringBuilder sb = new StringBuilder("🏷️ 找到 " + symbols.size() + " 个符号定义:\n\n");
            for (SymbolMatch s : symbols) {
                sb.append("• [")
                        .append(s.kind)
                        .append("] ")
                        .append(s.relativePath)
                        .append(":")
                        .append(s.lineNumber)
                        .append("\n")
                        .append("  `")
                        .append(s.codeLine)
                        .append("`\n\n");
            }

            return toOutputJson(sanitizeOutput(sb.toString()));
        });
    }

    @Tool(description = "生成代码仓库的文件目录树结构。具备层级深度与文件总数熔断保护，防止大项目爆表")
    public String codeFileTree(
            @ToolParam(description = "本地仓库别名") String repoName,
            @ToolParam(description = "可选，最大递归目录深度，默认 3，最大 6") Integer maxDepth,
            @ToolParam(description = "可选，最大遍历展示文件数，默认 200，最大 500") Integer maxFiles,
            ToolContext toolContext) {

        String argsJson = toJson(Map.of("repoName", repoName == null ? "" : repoName));
        return ToolEventEmitter.from(toolContext).executeWithEvent("code_file_tree", argsJson, toolContext, () -> {
            Path repoDir = getExistingRepo(toolContext, repoName);

            int depthLimit = (maxDepth != null && maxDepth > 0) ? Math.min(maxDepth, 6) : 3;
            int fileLimit = (maxFiles != null && maxFiles > 0) ? Math.min(maxFiles, 500) : 200;

            StringBuilder tree = new StringBuilder("📁 " + repoName + "/\n");
            AtomicInteger fileCount = new AtomicInteger(0);
            boolean truncated = buildTree(repoDir, repoDir, 1, depthLimit, fileLimit, fileCount, tree);

            if (truncated) {
                tree.append("\n... (truncated: 文件数达到上限 ").append(fileLimit).append("，已自动折叠剩余文件)");
            }

            return toOutputJson(tree.toString());
        });
    }

    // -----------------------------------------------------------------------
    // 内部实现与辅助算法
    // -----------------------------------------------------------------------

    private Path resolveSafeRepoDir(ToolContext toolContext, String repoName) {
        String userId = (String) toolContext.getContext().get(ToolEventEmitter.CTX_USER_ID);
        if (userId == null || userId.isBlank()) userId = "anonymous";

        Path safeRoot = Paths.get(System.getProperty("java.io.tmpdir"), "agent-repos", userId)
                .normalize()
                .toAbsolutePath();
        Path target = safeRoot.resolve(repoName).normalize().toAbsolutePath();

        if (!target.startsWith(safeRoot)) {
            throw new SecurityException("非法路径逃逸尝试被拒绝: " + repoName);
        }
        return target;
    }

    private Path getExistingRepo(ToolContext toolContext, String repoName) {
        if (repoName == null
                || repoName.isBlank()
                || !SAFE_NAME_PATTERN.matcher(repoName.trim()).matches()) {
            throw new IllegalArgumentException("非法仓库别名: " + repoName);
        }
        Path repoDir = resolveSafeRepoDir(toolContext, repoName);
        if (!Files.exists(repoDir) || !Files.isDirectory(repoDir)) {
            throw new IllegalArgumentException("仓库不存在: " + repoName + "，请先调用 gitClone");
        }
        return repoDir;
    }

    private List<Path> collectCodeFiles(Path repoDir, String filePattern, int maxFiles) {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(repoDir, 10)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> !isIgnored(repoDir, p))
                    .filter(p -> {
                        if (filePattern == null || filePattern.isBlank()) return true;
                        return p.getFileName()
                                .toString()
                                .toLowerCase()
                                .endsWith(filePattern.toLowerCase().trim());
                    })
                    .limit(maxFiles)
                    .forEach(files::add);
        } catch (Exception e) {
            log.warn("扫描代码仓库文件异常: {}", e.getMessage());
        }
        return files;
    }

    private boolean isIgnored(Path root, Path p) {
        Path rel = root.relativize(p);
        for (Path part : rel) {
            if (IGNORED_DIRS.contains(part.toString())) return true;
        }
        String fileName = p.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            String ext = fileName.substring(dot + 1).toLowerCase();
            if (BINARY_EXTENSIONS.contains(ext)) return true;
        }
        return false;
    }

    private void searchInFile(Path root, Path file, Pattern regex, List<CodeMatchResult> matches, int limit) {
        try {
            if (Files.size(file) > MAX_SEARCH_FILE_BYTES) return;
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            String relPath = root.relativize(file).toString();

            for (int i = 0; i < lines.size(); i++) {
                if (matches.size() >= limit) break;
                String line = lines.get(i);
                if (regex.matcher(line).find()) {
                    int start = Math.max(0, i - 2);
                    int end = Math.min(lines.size() - 1, i + 2);
                    StringBuilder snippet = new StringBuilder();
                    for (int k = start; k <= end; k++) {
                        snippet.append(k + 1).append(": ").append(lines.get(k)).append("\n");
                    }
                    matches.add(new CodeMatchResult(
                            relPath, i + 1, snippet.toString().trim()));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private List<CodeChunk> chunkCodeFile(Path root, Path file) {
        List<CodeChunk> chunks = new ArrayList<>();
        try {
            if (Files.size(file) > MAX_SEARCH_FILE_BYTES) return chunks;
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            String relPath = root.relativize(file).toString();

            int chunkSize = 35;
            int step = 25;

            for (int i = 0; i < lines.size(); i += step) {
                int end = Math.min(lines.size(), i + chunkSize);
                StringBuilder content =
                        new StringBuilder("// File: " + relPath + " (lines " + (i + 1) + "-" + end + ")\n");
                for (int k = i; k < end; k++) {
                    content.append(lines.get(k)).append("\n");
                }
                chunks.add(new CodeChunk(relPath, i + 1, end, content.toString().trim()));
                if (end >= lines.size()) break;
            }
        } catch (Exception ignored) {
        }
        return chunks;
    }

    private boolean buildTree(
            Path root,
            Path current,
            int currentDepth,
            int maxDepth,
            int maxFiles,
            AtomicInteger count,
            StringBuilder sb) {
        if (currentDepth > maxDepth) return false;

        File[] files = current.toFile().listFiles();
        if (files == null) return false;

        // 目录在前，文件在后排序
        List<File> sorted = new ArrayList<>(List.of(files));
        sorted.sort((f1, f2) -> {
            if (f1.isDirectory() && !f2.isDirectory()) return -1;
            if (!f1.isDirectory() && f2.isDirectory()) return 1;
            return f1.getName().compareToIgnoreCase(f2.getName());
        });

        String indent = "  ".repeat(currentDepth);

        for (File f : sorted) {
            if (IGNORED_DIRS.contains(f.getName())) continue;

            if (count.incrementAndGet() > maxFiles) {
                return true;
            }

            if (f.isDirectory()) {
                sb.append(indent).append("📁 ").append(f.getName()).append("/\n");
                boolean tr = buildTree(root, f.toPath(), currentDepth + 1, maxDepth, maxFiles, count, sb);
                if (tr) return true;
            } else {
                sb.append(indent).append("📄 ").append(f.getName()).append("\n");
            }
        }
        return false;
    }

    private List<ScoredCodeChunk> keywordScoreFallback(List<CodeChunk> chunks, String query) {
        String[] tokens = query.toLowerCase().split("\\s+");
        List<ScoredCodeChunk> list = new ArrayList<>();
        for (CodeChunk c : chunks) {
            String lower = c.content.toLowerCase();
            int score = 0;
            for (String t : tokens) {
                if (t.isBlank()) continue;
                if (lower.contains(t)) score += 10;
            }
            double normalized = Math.min(1.0, score / (double) Math.max(1, tokens.length * 10));
            list.add(new ScoredCodeChunk(c, normalized));
        }
        return list;
    }

    private Pattern buildSymbolPattern(String name, String kind) {
        String escaped = Pattern.quote(name);
        String k = kind == null ? "ALL" : kind.toUpperCase();
        return switch (k) {
            case "CLASS" -> Pattern.compile("\\b(class|record|struct|type)\\s+" + escaped + "\\b");
            case "INTERFACE" -> Pattern.compile("\\b(interface|protocol|trait)\\s+" + escaped + "\\b");
            case "FUNCTION" ->
                Pattern.compile(
                        "\\b(def|func|function|void|int|String|boolean|async\\s+function)\\s+" + escaped + "\\s*\\(");
            default ->
                Pattern.compile("\\b(class|interface|record|enum|struct|def|func|function|const|let|var|val)\\s+"
                        + escaped + "\\b");
        };
    }

    private String guessSymbolKind(String line) {
        String lower = line.toLowerCase();
        if (lower.contains("class ")) return "CLASS";
        if (lower.contains("interface ")) return "INTERFACE";
        if (lower.contains("record ")) return "RECORD";
        if (lower.contains("enum ")) return "ENUM";
        if (lower.contains("def ") || lower.contains("func ") || lower.contains("function ")) return "FUNCTION";
        return "SYMBOL";
    }

    private double cosineSimilarity(float[] v1, float[] v2) {
        if (v1 == null || v2 == null || v1.length != v2.length || v1.length == 0) return 0.0;
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < v1.length; i++) {
            dot += v1[i] * v2[i];
            normA += v1[i] * v1[i];
            normB += v2[i] * v2[i];
        }
        if (normA <= 0 || normB <= 0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private EmbeddingModel resolveEmbeddingModel() {
        if (safeEmbeddingModelProvider != null) {
            SafeEmbeddingModel safe = safeEmbeddingModelProvider.getIfAvailable();
            if (safe != null) return safe;
        }
        if (genericEmbeddingModelProvider != null) {
            return genericEmbeddingModelProvider.getIfAvailable();
        }
        return null;
    }

    private String sanitizeOutput(String text) {
        if (text == null || text.isBlank()) return "";
        String processed = text;
        if (safeGuardEngineProvider != null) {
            SafeGuardEngine engine = safeGuardEngineProvider.getIfAvailable();
            if (engine != null) {
                processed = engine.inspectResponse(processed, ActionPolicy.MASK).getProcessedText();
            }
        }
        return processed;
    }

    private String toOutputJson(String content) {
        try {
            return "{\"output\":" + MAPPER.writeValueAsString(content) + "}";
        } catch (Exception e) {
            return "{\"output\":\"\"}";
        }
    }

    private static String toJson(Map<String, String> data) {
        try {
            return MAPPER.writeValueAsString(data);
        } catch (Exception e) {
            return "{}";
        }
    }

    // Records
    public record CodeMatchResult(String relativePath, int lineNumber, String snippet) {}

    public record CodeChunk(String relativePath, int startLine, int endLine, String content) {}

    public record ScoredCodeChunk(CodeChunk chunk, double score) {}

    public record SymbolMatch(String relativePath, int lineNumber, String codeLine, String kind) {}
}
