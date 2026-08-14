package xyz.ppmblszdp.ai.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.config.AiProviderProperties.CodeSandboxConfig;

/**
 * AI 代码执行沙箱服务（Code Interpreter Engine）。
 *
 * <h2>架构设计与安全防护</h2>
 * <ul>
 *   <li><b>双模式隔离沙箱</b>：优先使用 Docker 隔离容器（{@code --network none --read-only --cap-drop=ALL -m 256m --cpus 1.0}）；
 *       Docker 不可用时受控回退为本地进程沙箱（需 {@code allow-local-fallback=true}）。</li>
 *   <li><b>多用户与并发隔离</b>：每次执行独立分配专属 UUID 临时工作区，执行完毕在 {@code finally} 块彻底销毁，杜绝文件串味与磁盘泄漏。</li>
 *   <li><b>图表自动捕获</b>：自动注入 Matplotlib 无头模式（{@code MPLBACKEND=Agg}），扫描工作区生成的图片（PNG/JPG/SVG/WebP）并转为 Base64 结构化回传。</li>
 *   <li><b>本地防提权与危险代码前置拦截</b>：本地回退模式下剥离宿主机敏感环境变量（API Keys / .env），并前置拦截危险系统调用。</li>
 * </ul>
 */
@Service
public class CodeExecutionService {

    private static final Logger log = LoggerFactory.getLogger(CodeExecutionService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // 危险模式拦截正则（本地回退模式防护）
    private static final Pattern DANGEROUS_PYTHON_PATTERN = Pattern.compile(
            "\\b(subprocess|os\\.system|os\\.popen|os\\.exec|os\\.spawn|shutil\\.rmtree|pty\\.spawn|builtins\\.__import__|__subclasses__|ctypes)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DANGEROUS_JS_PATTERN = Pattern.compile(
            "\\b(child_process|fs\\.unlink|fs\\.rmdir|fs\\.rm|process\\.exit|process\\.kill|require\\(['\"]child_process['\"]\\))\\b",
            Pattern.CASE_INSENSITIVE);

    private final AiProviderProperties properties;

    public CodeExecutionService(AiProviderProperties properties) {
        this.properties = properties;
    }

    public record ImageArtifact(String name, String mimeType, String data) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ExecutionResponse(
            String status,
            String language,
            String sandboxType,
            int exitCode,
            String stdout,
            String stderr,
            long executionTimeMs,
            List<ImageArtifact> images,
            boolean truncated) {

        public String toJson() {
            try {
                return MAPPER.writeValueAsString(this);
            } catch (JsonProcessingException e) {
                return "{\"status\":\"" + status + "\",\"stdout\":\"" + stdout + "\"}";
            }
        }
    }

    /**
     * 执行指定语言的代码并捕获结果。
     *
     * @param language 支持 "python"（或 "py"）和 "javascript"（或 "nodejs", "js"）
     * @param code     源代码
     * @return 结构化执行结果
     */
    public ExecutionResponse execute(String language, String code) {
        if (code == null || code.isBlank()) {
            return new ExecutionResponse("error", language, "none", -1, "", "错误：提交的代码内容为空", 0L, List.of(), false);
        }

        String normalizedLang = normalizeLanguage(language);
        CodeSandboxConfig config = properties.resolveAgent().resolveCodeSandbox();
        long startTime = System.currentTimeMillis();

        // 尝试 Docker 容器执行
        if (config.isDockerEnabled() && isDockerAvailable()) {
            try {
                return executeInDocker(normalizedLang, code, config, startTime);
            } catch (Exception e) {
                log.warn("Docker 沙箱执行失败，尝试评估本地回退: {}", e.getMessage());
            }
        }

        // 评估本地回退模式
        if (!config.isAllowLocalFallback()) {
            long duration = System.currentTimeMillis() - startTime;
            return new ExecutionResponse(
                    "error",
                    normalizedLang,
                    "rejected",
                    -1,
                    "",
                    "安全拦截：Docker 沙箱不可用，且当前配置禁止本地回退执行（allow-local-fallback=false）",
                    duration,
                    List.of(),
                    false);
        }

        return executeLocally(normalizedLang, code, config, startTime);
    }

    private ExecutionResponse executeInDocker(String language, String code, CodeSandboxConfig config, long startTime)
            throws Exception {
        String executionId = UUID.randomUUID().toString();
        Path tempDir = Files.createTempDirectory("ai-sandbox-docker-" + executionId);
        tempDir.toFile().setReadable(true, false);
        tempDir.toFile().setWritable(true, false);
        tempDir.toFile().setExecutable(true, false);

        try {
            String scriptFileName = "python".equals(language) ? "script.py" : "script.js";
            String preparedCode = prepareCode(language, code);
            Path scriptFile = tempDir.resolve(scriptFileName);
            Files.writeString(scriptFile, preparedCode, StandardCharsets.UTF_8);
            scriptFile.toFile().setReadable(true, false);
            scriptFile.toFile().setWritable(true, false);

            String image = "python".equals(language) ? config.resolvePythonImage() : config.resolveNodeImage();
            String runnerCmd = "python".equals(language) ? "python3" : "node";

            // 构造强隔离 Docker 命令
            List<String> cmd = new ArrayList<>(List.of(
                    "docker",
                    "run",
                    "--rm",
                    "--network",
                    "none",
                    "--memory",
                    config.resolveMemoryLimit(),
                    "--cpus",
                    config.resolveCpuLimit(),
                    "--pids-limit",
                    "64",
                    "--read-only",
                    "--security-opt=no-new-privileges:true",
                    "--cap-drop=ALL",
                    "--tmpfs",
                    "/tmp:rw,noexec,nosuid,size=64m",
                    "-v",
                    tempDir.toAbsolutePath() + ":/workspace:rw",
                    "-w",
                    "/workspace",
                    "-e",
                    "MPLBACKEND=Agg",
                    image,
                    runnerCmd,
                    scriptFileName));

            ProcessResult pr = runProcess(cmd, tempDir.toFile(), config.resolveTimeoutSeconds(), Map.of());
            long duration = System.currentTimeMillis() - startTime;

            List<ImageArtifact> images = scanAndEncodeImages(tempDir);
            String truncatedStdout = truncate(pr.stdout, config.resolveMaxOutputLength());
            String truncatedStderr = truncate(pr.stderr, config.resolveMaxOutputLength());
            boolean isTruncated = pr.stdout.length() > config.resolveMaxOutputLength()
                    || pr.stderr.length() > config.resolveMaxOutputLength();

            String status = pr.exitCode == 0 ? "success" : "error";
            return new ExecutionResponse(
                    status,
                    language,
                    "docker",
                    pr.exitCode,
                    truncatedStdout,
                    truncatedStderr,
                    duration,
                    images,
                    isTruncated);
        } finally {
            cleanupDirectory(tempDir);
        }
    }

    private ExecutionResponse executeLocally(String language, String code, CodeSandboxConfig config, long startTime) {
        String executionId = UUID.randomUUID().toString();
        long duration;

        // 本地模式危险调用拦截
        String securityViolation = checkSecurity(language, code);
        if (securityViolation != null) {
            duration = System.currentTimeMillis() - startTime;
            return new ExecutionResponse(
                    "error",
                    language,
                    "local-blocked",
                    -1,
                    "",
                    "安全拦截：本地执行模式下检测到潜在危险系统调用 [" + securityViolation + "]，已阻断执行",
                    duration,
                    List.of(),
                    false);
        }

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("ai-sandbox-local-" + executionId);
            String scriptFileName = "python".equals(language) ? "script.py" : "script.js";
            String preparedCode = prepareCode(language, code);
            Path scriptFile = tempDir.resolve(scriptFileName);
            Files.writeString(scriptFile, preparedCode, StandardCharsets.UTF_8);

            String runner = "python".equals(language) ? resolvePythonExecutable() : resolveNodeExecutable();
            List<String> cmd = List.of(runner, scriptFileName);

            // 本地环境变量安全清洗：剥离所有敏感宿主变量，仅提供基本 PATH 与无头渲染变量
            Map<String, String> safeEnv = Map.of(
                    "PATH", System.getenv("PATH") != null ? System.getenv("PATH") : "/usr/bin:/bin",
                    "MPLBACKEND", "Agg",
                    "PYTHONUNBUFFERED", "1");

            ProcessResult pr = runProcess(cmd, tempDir.toFile(), config.resolveTimeoutSeconds(), safeEnv);
            duration = System.currentTimeMillis() - startTime;

            List<ImageArtifact> images = scanAndEncodeImages(tempDir);
            String truncatedStdout = truncate(pr.stdout, config.resolveMaxOutputLength());
            String truncatedStderr = truncate(pr.stderr, config.resolveMaxOutputLength());
            boolean isTruncated = pr.stdout.length() > config.resolveMaxOutputLength()
                    || pr.stderr.length() > config.resolveMaxOutputLength();

            String status = pr.exitCode == 0 ? "success" : "error";
            return new ExecutionResponse(
                    status,
                    language,
                    "local",
                    pr.exitCode,
                    truncatedStdout,
                    truncatedStderr,
                    duration,
                    images,
                    isTruncated);
        } catch (Exception e) {
            duration = System.currentTimeMillis() - startTime;
            log.error("本地沙箱执行异常: {}", e.getMessage(), e);
            return new ExecutionResponse(
                    "error", language, "local", -1, "", "执行异常: " + e.getMessage(), duration, List.of(), false);
        } finally {
            if (tempDir != null) {
                cleanupDirectory(tempDir);
            }
        }
    }

    private String checkSecurity(String language, String code) {
        if ("python".equals(language)) {
            var matcher = DANGEROUS_PYTHON_PATTERN.matcher(code);
            if (matcher.find()) {
                return matcher.group();
            }
        } else if ("javascript".equals(language)) {
            var matcher = DANGEROUS_JS_PATTERN.matcher(code);
            if (matcher.find()) {
                return matcher.group();
            }
        }
        return null;
    }

    private String prepareCode(String language, String code) {
        if ("python".equals(language)) {
            // 若包含 matplotlib 且未显式设置 Agg，前置注入无头设置
            if (code.contains("matplotlib") && !code.contains("use('Agg')") && !code.contains("use(\"Agg\")")) {
                return "import matplotlib\nmatplotlib.use('Agg')\n" + code;
            }
        }
        return code;
    }

    private List<ImageArtifact> scanAndEncodeImages(Path dir) {
        List<ImageArtifact> list = new ArrayList<>();
        if (!Files.exists(dir)) return list;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path path : stream) {
                if (!Files.isRegularFile(path)) continue;
                String filename = path.getFileName().toString().toLowerCase(Locale.ROOT);
                String mimeType = null;
                if (filename.endsWith(".png")) {
                    mimeType = "image/png";
                } else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
                    mimeType = "image/jpeg";
                } else if (filename.endsWith(".webp")) {
                    mimeType = "image/webp";
                } else if (filename.endsWith(".svg")) {
                    mimeType = "image/svg+xml";
                }

                if (mimeType != null) {
                    try {
                        byte[] bytes = Files.readAllBytes(path);
                        if (bytes.length > 0 && bytes.length <= 10 * 1024 * 1024) { // ≤ 10MB
                            String base64 = Base64.getEncoder().encodeToString(bytes);
                            String dataUrl = "data:" + mimeType + ";base64," + base64;
                            list.add(new ImageArtifact(path.getFileName().toString(), mimeType, dataUrl));
                        }
                    } catch (IOException e) {
                        log.warn("读取生成的图表文件失败 {}: {}", path, e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            log.warn("扫描图表目录失败: {}", e.getMessage());
        }

        // 按文件名排序保证一致性
        list.sort((a, b) -> a.name().compareTo(b.name()));
        return list;
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {}

    private ProcessResult runProcess(
            List<String> command, File workDir, int timeoutSeconds, Map<String, String> customEnv) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir);

        if (!customEnv.isEmpty()) {
            pb.environment().clear();
            pb.environment().putAll(customEnv);
        }

        Process process = pb.start();

        CompletableFuture<String> stdoutFuture =
                CompletableFuture.supplyAsync(() -> readStream(process.getInputStream()));
        CompletableFuture<String> stderrFuture =
                CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("代码执行超时（超过 " + timeoutSeconds + " 秒）");
        }

        int exitCode = process.exitValue();
        String stdout = stdoutFuture.get(2, TimeUnit.SECONDS);
        String stderr = stderrFuture.get(2, TimeUnit.SECONDS);

        return new ProcessResult(exitCode, stdout, stderr);
    }

    private String readStream(InputStream is) {
        try {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "\n... [日志输出已截断，达到上限 " + maxLength + " 字符]";
    }

    private boolean isDockerAvailable() {
        try {
            Process process = new ProcessBuilder("docker", "version", "--format", "{{.Server.Version}}")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(2, TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0) {
                String out = readStream(process.getInputStream()).trim();
                return !out.isBlank();
            }
            if (!finished) {
                process.destroyForcibly();
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private String resolvePythonExecutable() {
        if (new File("/usr/bin/python3").exists()) return "/usr/bin/python3";
        if (new File("/usr/sbin/python3").exists()) return "/usr/sbin/python3";
        return "python3";
    }

    private String resolveNodeExecutable() {
        if (new File("/usr/bin/node").exists()) return "/usr/bin/node";
        return "node";
    }

    private void cleanupDirectory(Path dir) {
        try {
            FileSystemUtils.deleteRecursively(dir);
        } catch (Exception e) {
            log.warn("清理沙箱临时工作目录失败 {}: {}", dir, e.getMessage());
        }
    }

    private String normalizeLanguage(String lang) {
        if (lang == null) return "python";
        String l = lang.trim().toLowerCase(Locale.ROOT);
        if (l.contains("py")) return "python";
        if (l.contains("js") || l.contains("node") || l.contains("javascript")) return "javascript";
        return "python";
    }
}
