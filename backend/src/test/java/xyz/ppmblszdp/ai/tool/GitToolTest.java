package xyz.ppmblszdp.ai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Sinks;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.dto.ChatChunkDto;
import xyz.ppmblszdp.ai.safeguard.SafeGuardEngine;

class GitToolTest {

    private GitTool gitTool;
    private ToolEventEmitter emitter;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ObjectProvider<SafeGuardEngine> safeGuardProvider = mock(ObjectProvider.class);
        gitTool = new GitTool(safeGuardProvider);

        AiProviderProperties props = mock(AiProviderProperties.class);
        AiProviderProperties.AgentConfig agentConfig = mock(AiProviderProperties.AgentConfig.class);
        when(props.resolveAgent()).thenReturn(agentConfig);
        when(agentConfig.resolveMaxToolCalls()).thenReturn(10);
        when(agentConfig.resolveTimeoutSeconds()).thenReturn(30);

        emitter = new ToolEventEmitter(props);
    }

    private ToolContext createMockToolContext(String userId) {
        Sinks.Many<ChatChunkDto> sink = emitter.newSink();
        Map<String, Object> ctxMap = new HashMap<>();
        ctxMap.put(ToolEventEmitter.CTX_EMITTER, emitter);
        ctxMap.put("eventSink", sink);
        ctxMap.put(ToolEventEmitter.CTX_USER_ID, userId);
        return new ToolContext(ctxMap);
    }

    @Test
    void validateUrl_shouldRejectInvalidOrDangerousProtocols() {
        ToolContext ctx = createMockToolContext("user-1");

        // 本地协议与私有回环地址均应被拒绝
        String res1 = gitTool.gitClone("file:///etc/passwd", "my-repo", "main", false, ctx);
        assertThat(res1).contains("工具执行失败");

        String res2 = gitTool.gitClone("http://127.0.0.1:8080/repo.git", "my-repo", "main", false, ctx);
        assertThat(res2).contains("工具执行失败");

        String res3 = gitTool.gitClone("http://localhost/repo.git", "my-repo", "main", false, ctx);
        assertThat(res3).contains("工具执行失败");
    }

    @Test
    void pathTraversal_shouldBeBlockedBySecurityException() {
        ToolContext ctx = createMockToolContext("user-1");

        // 试图逃逸用户沙箱目录
        String res1 = gitTool.gitStatus("../victim-repo", ctx);
        assertThat(res1).contains("工具执行失败");

        String res2 = gitTool.gitLog("../../etc", 10, null, null, ctx);
        assertThat(res2).contains("工具执行失败");
    }

    @Test
    void sanitizeOutput_shouldRedactApiKeysAndSecrets() {
        String rawOutput = """
                commit a1b2c3d
                Author: dev <dev@example.com>

                diff --git a/.env b/.env
                +OPENAI_API_KEY=sk-proj-abc12345678901234567890
                +AWS_KEY=AKIAIOSFODNN7EXAMPLE
                +GITHUB_TOKEN=ghp_123456789012345678901234567890123456
                +password: mySecretPassword123
                """;

        String sanitized = gitTool.sanitizeOutput(rawOutput);

        assertThat(sanitized).doesNotContain("AKIAIOSFODNN7EXAMPLE");
        assertThat(sanitized).doesNotContain("ghp_123456789012345678901234567890123456");
        assertThat(sanitized).contains("[REDACTED_SECRET]");
    }

    @Test
    void localGitOperations_shouldWorkInValidRepo() throws Exception {
        String userId = "test-user-git";
        Path userRepos = Paths.get(System.getProperty("java.io.tmpdir"), "agent-repos", userId, "demo-repo");
        Files.createDirectories(userRepos);

        // 初始化一个真实的本地临时 Git 仓库用于测试
        ProcessBuilder initPb = new ProcessBuilder("git", "init");
        initPb.directory(userRepos.toFile());
        initPb.start().waitFor();

        ProcessBuilder configName = new ProcessBuilder("git", "config", "user.name", "Tester");
        configName.directory(userRepos.toFile());
        configName.start().waitFor();

        ProcessBuilder configEmail = new ProcessBuilder("git", "config", "user.email", "tester@example.com");
        configEmail.directory(userRepos.toFile());
        configEmail.start().waitFor();

        // 写入一个文件并提交
        Path testFile = userRepos.resolve("README.md");
        Files.writeString(testFile, "# Demo Project\nHello Git Agent!\n");

        ProcessBuilder addPb = new ProcessBuilder("git", "add", "README.md");
        addPb.directory(userRepos.toFile());
        addPb.start().waitFor();

        ProcessBuilder commitPb = new ProcessBuilder("git", "commit", "-m", "initial commit");
        commitPb.directory(userRepos.toFile());
        commitPb.start().waitFor();

        ToolContext ctx = createMockToolContext(userId);

        // 测试 gitStatus
        String statusJson = gitTool.gitStatus("demo-repo", ctx);
        assertThat(statusJson).contains("output");

        // 测试 gitLog
        String logJson = gitTool.gitLog("demo-repo", 5, null, null, ctx);
        assertThat(logJson).contains("initial commit");

        // 测试 gitBranch
        String branchJson = gitTool.gitBranch("demo-repo", ctx);
        assertThat(branchJson.contains("master") || branchJson.contains("main")).isTrue();

        // 测试 gitBlame
        String blameJson = gitTool.gitBlame("demo-repo", "README.md", 1, 2, ctx);
        assertThat(blameJson).contains("Hello Git Agent!");

        // 测试 gitShow
        String showJson = gitTool.gitShow("demo-repo", "HEAD", "README.md", ctx);
        assertThat(showJson).contains("Demo Project");
    }
}
