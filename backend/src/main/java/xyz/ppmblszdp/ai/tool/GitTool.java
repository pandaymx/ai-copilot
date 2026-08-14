package xyz.ppmblszdp.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import xyz.ppmblszdp.ai.safeguard.ActionPolicy;
import xyz.ppmblszdp.ai.safeguard.SafeGuardEngine;

/**
 * Git 仓库操作工具：面向代码审查辅助、Bug 定位与代码探索。
 *
 * <h3>安全与隔离策略</h3>
 * <ul>
 *   <li><b>沙箱隔离</b>：仓库限定在 {@code <java.io.tmpdir>/agent-repos/<userId>/<repoName>}</li>
 *   <li><b>防路径穿越</b>：规范化路径并严格校验 {@code startsWith(safeRoot)}</li>
 *   <li><b>浅克隆带宽保护</b>：默认 {@code --depth=20 --single-branch}，防止大仓库耗尽带宽与存储</li>
 *   <li><b>敏感信息与凭据过滤</b>：输出通过 SafeGuardEngine 与内置 API Key/私钥正则清洗</li>
 *   <li><b>防命令注入与 SSRF</b>：使用 ProcessBuilder 参数数组调用，严格限制远程 URL 协议</li>
 * </ul>
 */
@Component
public class GitTool {

    private static final Logger log = LoggerFactory.getLogger(GitTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final long MAX_OUTPUT_CHARS = 200_000; // 200KB 截断保护
    private static final int PROCESS_TIMEOUT_SECONDS = 30;

    private static final Pattern SAFE_URL_PATTERN =
            Pattern.compile("^(https?|git)://[a-zA-Z0-9_.~:/?#\\[\\]@!$&'()*+,;=-]+$");
    private static final Pattern SAFE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]+$");

    // 常见敏感凭据正则模式
    private static final List<Pattern> SECRET_PATTERNS = List.of(
            Pattern.compile("AKIA[0-9A-Z]{16}"), // AWS Access Key
            Pattern.compile("ghp_[a-zA-Z0-9]{36,40}"), // GitHub Personal Token
            Pattern.compile("eyJ[a-zA-Z0-9_-]{10,}\\.[a-zA-Z0-9_-]{10,}\\.[a-zA-Z0-9_-]{10,}"), // JWT Token
            Pattern.compile(
                    "(?i)(api[_-]?key|secret[_-]?key|access[_-]?token|password|auth[_-]?token)\\s*[:=]\\s*['\"]?([a-zA-Z0-9_~./+=-]{8,})['\"]?"), // 通用密钥对
            Pattern.compile("-----BEGIN [A-Z ]+PRIVATE KEY-----([\\s\\S]*?)-----END [A-Z ]+PRIVATE KEY-----") // 私钥
            );

    private final ObjectProvider<SafeGuardEngine> safeGuardEngineProvider;

    public GitTool(ObjectProvider<SafeGuardEngine> safeGuardEngineProvider) {
        this.safeGuardEngineProvider = safeGuardEngineProvider;
    }

    @Tool(description = "克隆远程 Git 仓库到用户的安全沙箱中。默认采用浅克隆（--depth=20 --single-branch）以保护网络带宽与磁盘空间")
    public String gitClone(
            @ToolParam(description = "远程 Git 仓库 URL，只允许 http://, https://, git:// 协议") String repoUrl,
            @ToolParam(description = "本地仓库别名（仅允许字母数字和下划线点短横线，如 my-project）") String repoName,
            @ToolParam(description = "可选，要克隆的分支名，默认 main/master") String branch,
            @ToolParam(description = "可选，是否拉取全量历史（true/false），默认 false（浅克隆）") Boolean fullHistory,
            ToolContext toolContext) {

        String argsJson =
                toJson(Map.of("repoUrl", repoUrl == null ? "" : repoUrl, "repoName", repoName == null ? "" : repoName));
        return ToolEventEmitter.from(toolContext).executeWithEvent("git_clone", argsJson, toolContext, () -> {
            validateUrl(repoUrl);
            validateRepoName(repoName);

            Path repoDir = resolveSafeRepoDir(toolContext, repoName);
            if (Files.exists(repoDir)) {
                return "{\"output\":\"仓库已存在于沙箱中: " + repoName + "\"}";
            }

            List<String> command = new ArrayList<>(List.of("git", "clone"));
            if (!Boolean.TRUE.equals(fullHistory)) {
                command.add("--depth=20");
                command.add("--single-branch");
            }
            if (branch != null
                    && !branch.isBlank()
                    && SAFE_NAME_PATTERN.matcher(branch.trim()).matches()) {
                command.add("--branch");
                command.add(branch.trim());
            }
            command.add(repoUrl.trim());
            command.add(repoDir.toString());

            String result = executeCommand(command, repoDir.getParent().toFile());
            return toOutputJson(sanitizeOutput("克隆成功:\n" + result));
        });
    }

    @Tool(description = "查看已克隆 Git 仓库的提交日志（Commit 历史列表、提交人、时间与提交消息）")
    public String gitLog(
            @ToolParam(description = "本地仓库别名") String repoName,
            @ToolParam(description = "可选，最大拉取的提交数，默认 10，最大 50") Integer maxCount,
            @ToolParam(description = "可选，分支或特定 revision，如 main 或 HEAD~3") String revision,
            @ToolParam(description = "可选，限定查看特定文件的提交历史") String filePath,
            ToolContext toolContext) {

        String argsJson = toJson(Map.of("repoName", repoName == null ? "" : repoName));
        return ToolEventEmitter.from(toolContext).executeWithEvent("git_log", argsJson, toolContext, () -> {
            Path repoDir = getExistingRepo(toolContext, repoName);
            int count = (maxCount != null && maxCount > 0) ? Math.min(maxCount, 50) : 10;

            List<String> command = new ArrayList<>(List.of(
                    "git", "log", "-n", String.valueOf(count), "--pretty=format:%h - %an (%ad): %s", "--date=short"));
            if (revision != null
                    && !revision.isBlank()
                    && SAFE_NAME_PATTERN.matcher(revision.trim()).matches()) {
                command.add(revision.trim());
            }
            if (filePath != null && !filePath.isBlank()) {
                command.add("--");
                command.add(filePath.trim());
            }

            String result = executeCommand(command, repoDir.toFile());
            return toOutputJson(sanitizeOutput(result));
        });
    }

    @Tool(description = "查看 Git 仓库的代码差异（Diff 对比），支持工作区比对或两个 Commit / 分支间差异比对")
    public String gitDiff(
            @ToolParam(description = "本地仓库别名") String repoName,
            @ToolParam(description = "可选，起始 commit 或分支名，如 HEAD 或 main") String commitA,
            @ToolParam(description = "可选，目标 commit 或分支名，如 feature-1") String commitB,
            @ToolParam(description = "可选，限定对比特定文件路径") String filePath,
            ToolContext toolContext) {

        String argsJson = toJson(Map.of("repoName", repoName == null ? "" : repoName));
        return ToolEventEmitter.from(toolContext).executeWithEvent("git_diff", argsJson, toolContext, () -> {
            Path repoDir = getExistingRepo(toolContext, repoName);
            List<String> command = new ArrayList<>(List.of("git", "diff"));

            if (commitA != null
                    && !commitA.isBlank()
                    && SAFE_NAME_PATTERN.matcher(commitA.trim()).matches()) {
                command.add(commitA.trim());
            }
            if (commitB != null
                    && !commitB.isBlank()
                    && SAFE_NAME_PATTERN.matcher(commitB.trim()).matches()) {
                command.add(commitB.trim());
            }
            if (filePath != null && !filePath.isBlank()) {
                command.add("--");
                command.add(filePath.trim());
            }

            String result = executeCommand(command, repoDir.toFile());
            return toOutputJson(sanitizeOutput(result));
        });
    }

    @Tool(description = "查看特定文件的 Git Blame 代码责任与逐行修改历史记录")
    public String gitBlame(
            @ToolParam(description = "本地仓库别名") String repoName,
            @ToolParam(description = "文件路径，如 src/main/java/Main.java") String filePath,
            @ToolParam(description = "可选，起始行号，如 1") Integer startLine,
            @ToolParam(description = "可选，结束行号，如 50") Integer endLine,
            ToolContext toolContext) {

        String argsJson = toJson(
                Map.of("repoName", repoName == null ? "" : repoName, "filePath", filePath == null ? "" : filePath));
        return ToolEventEmitter.from(toolContext).executeWithEvent("git_blame", argsJson, toolContext, () -> {
            Path repoDir = getExistingRepo(toolContext, repoName);
            if (filePath == null || filePath.isBlank()) {
                throw new IllegalArgumentException("filePath 不能为空");
            }

            List<String> command = new ArrayList<>(List.of("git", "blame"));
            if (startLine != null && endLine != null && startLine > 0 && endLine >= startLine) {
                command.add("-L");
                command.add(startLine + "," + endLine);
            }
            command.add("--");
            command.add(filePath.trim());

            String result = executeCommand(command, repoDir.toFile());
            return toOutputJson(sanitizeOutput(result));
        });
    }

    @Tool(description = "查看 Git 仓库工作区状态（Status）与本地已修改或未提交文件列表")
    public String gitStatus(@ToolParam(description = "本地仓库别名") String repoName, ToolContext toolContext) {

        String argsJson = toJson(Map.of("repoName", repoName == null ? "" : repoName));
        return ToolEventEmitter.from(toolContext).executeWithEvent("git_status", argsJson, toolContext, () -> {
            Path repoDir = getExistingRepo(toolContext, repoName);
            List<String> command = List.of("git", "status", "--short", "--branch");
            String result = executeCommand(command, repoDir.toFile());
            return toOutputJson(sanitizeOutput(result));
        });
    }

    @Tool(description = "查看特定 Commit 详细元数据，或查看指定 Commit 历史快照中的特定文件内容")
    public String gitShow(
            @ToolParam(description = "本地仓库别名") String repoName,
            @ToolParam(description = "Commit Hash、Tag 或分支名（如 HEAD 或 a1b2c3d）") String commitOrRef,
            @ToolParam(description = "可选，要查看的文件路径，为空则展示整个 Commit 的 Diff") String filePath,
            ToolContext toolContext) {

        String argsJson = toJson(Map.of(
                "repoName", repoName == null ? "" : repoName, "commitOrRef", commitOrRef == null ? "" : commitOrRef));
        return ToolEventEmitter.from(toolContext).executeWithEvent("git_show", argsJson, toolContext, () -> {
            Path repoDir = getExistingRepo(toolContext, repoName);
            String ref = (commitOrRef == null || commitOrRef.isBlank()) ? "HEAD" : commitOrRef.trim();

            List<String> command = new ArrayList<>(List.of("git", "show"));
            if (filePath != null && !filePath.isBlank()) {
                command.add(ref + ":" + filePath.trim());
            } else {
                command.add(ref);
            }

            String result = executeCommand(command, repoDir.toFile());
            return toOutputJson(sanitizeOutput(result));
        });
    }

    @Tool(description = "列出 Git 仓库的所有本地分支与远程跟踪分支列表")
    public String gitBranch(@ToolParam(description = "本地仓库别名") String repoName, ToolContext toolContext) {

        String argsJson = toJson(Map.of("repoName", repoName == null ? "" : repoName));
        return ToolEventEmitter.from(toolContext).executeWithEvent("git_branch", argsJson, toolContext, () -> {
            Path repoDir = getExistingRepo(toolContext, repoName);
            List<String> command = List.of("git", "branch", "-a");
            String result = executeCommand(command, repoDir.toFile());
            return toOutputJson(sanitizeOutput(result));
        });
    }

    // -----------------------------------------------------------------------
    // 安全校验与内部辅助
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
        validateRepoName(repoName);
        Path repoDir = resolveSafeRepoDir(toolContext, repoName);
        if (!Files.exists(repoDir) || !Files.isDirectory(repoDir)) {
            throw new IllegalArgumentException("仓库不存在: " + repoName + "，请先执行 gitClone");
        }
        return repoDir;
    }

    private void validateUrl(String url) {
        if (url == null
                || url.isBlank()
                || !SAFE_URL_PATTERN.matcher(url.trim()).matches()) {
            throw new IllegalArgumentException("非法 Git 仓库 URL，只支持 http://, https://, git://");
        }
        String lower = url.toLowerCase();
        if (lower.contains("localhost")
                || lower.contains("127.0.0.1")
                || lower.contains("0.0.0.0")
                || lower.contains("169.254.")) {
            throw new SecurityException("禁止访问私有或本地回环地址");
        }
    }

    private void validateRepoName(String name) {
        if (name == null
                || name.isBlank()
                || !SAFE_NAME_PATTERN.matcher(name.trim()).matches()) {
            throw new IllegalArgumentException("非法仓库别名，仅允许字母数字和短横线下划线: " + name);
        }
    }

    /**
     * 凭据与敏感信息脱敏过滤器（复用 SafeGuardEngine + 专有 API Key 正则）
     */
    public String sanitizeOutput(String text) {
        if (text == null || text.isBlank()) return "";

        String processed = text;

        // 1. 专有 API 密钥 / 私钥脱敏
        for (Pattern p : SECRET_PATTERNS) {
            processed = p.matcher(processed).replaceAll("[REDACTED_SECRET]");
        }

        // 2. 复用 SafeGuardEngine 脱敏 PII 与通用敏感词
        if (safeGuardEngineProvider != null) {
            SafeGuardEngine engine = safeGuardEngineProvider.getIfAvailable();
            if (engine != null) {
                processed = engine.inspectResponse(processed, ActionPolicy.MASK).getProcessedText();
            }
        }

        // 3. 长度截断保护
        if (processed.length() > MAX_OUTPUT_CHARS) {
            processed =
                    processed.substring(0, (int) MAX_OUTPUT_CHARS) + "\n\n... (Output truncated: exceeded 200KB limit)";
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

    private String executeCommand(List<String> command, File workingDir) {
        try {
            if (workingDir != null && !workingDir.exists()) {
                workingDir.mkdirs();
            }
            ProcessBuilder pb = new ProcessBuilder(command);
            if (workingDir != null) {
                pb.directory(workingDir);
            }
            // 避免 Git 弹出凭据输入框阻塞
            pb.environment().put("GIT_TERMINAL_PROMPT", "0");
            pb.environment().put("GIT_ASKPASS", "echo");
            pb.environment().put("LANG", "en_US.UTF-8");

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() < MAX_OUTPUT_CHARS) {
                        output.append(line).append("\n");
                    }
                }
            }

            StringBuilder errorOutput = new StringBuilder();
            try (BufferedReader errReader =
                    new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = errReader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("Git 命令执行超时 (30s 熔断)");
            }

            if (process.exitValue() != 0) {
                String err = errorOutput.toString().trim();
                throw new RuntimeException(
                        "Git 命令失败 (code " + process.exitValue() + "): " + (err.isEmpty() ? output.toString() : err));
            }

            return output.toString().trim();
        } catch (Exception e) {
            log.warn("Git 命令执行异常: {}", e.getMessage());
            throw new RuntimeException("Git 操作执行异常: " + e.getMessage(), e);
        }
    }

    private static String toJson(Map<String, String> data) {
        try {
            return MAPPER.writeValueAsString(data);
        } catch (Exception e) {
            return "{}";
        }
    }
}
