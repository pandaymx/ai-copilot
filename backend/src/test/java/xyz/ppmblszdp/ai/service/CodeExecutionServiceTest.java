package xyz.ppmblszdp.ai.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.config.AiProviderProperties.AgentConfig;
import xyz.ppmblszdp.ai.config.AiProviderProperties.CodeSandboxConfig;

class CodeExecutionServiceTest {

    private CodeExecutionService service;
    private AiProviderProperties properties;

    @BeforeEach
    void setUp() {
        CodeSandboxConfig sandboxConfig = new CodeSandboxConfig(
                true,
                10,
                65536,
                true, // dockerEnabled
                true, // allowLocalFallback
                "python:3.11-slim",
                "node:20-alpine",
                "256m",
                "1.0");
        AgentConfig agentConfig = new AgentConfig(true, 5, 30, false, null, false, null, null, 2048, 1, sandboxConfig);
        properties = mock(AiProviderProperties.class);
        when(properties.resolveAgent()).thenReturn(agentConfig);
        service = new CodeExecutionService(properties);
    }

    @Test
    void shouldExecutePythonScriptSuccessfully() {
        String code = "print(sum([x * 2 for x in range(5)]))";
        CodeExecutionService.ExecutionResponse response = service.execute("python", code);

        assertNotNull(response);
        assertEquals("python", response.language());
        assertEquals(0, response.exitCode());
        assertEquals("success", response.status());
        assertTrue(response.stdout().contains("20"));
        assertTrue(response.executionTimeMs() >= 0);
    }

    @Test
    void shouldExecuteJavaScriptSuccessfully() {
        String code = "console.log([1, 2, 3, 4].map(x => x * 3).join('-'));";
        CodeExecutionService.ExecutionResponse response = service.execute("javascript", code);

        assertNotNull(response);
        assertEquals("javascript", response.language());
        assertEquals(0, response.exitCode());
        assertEquals("success", response.status());
        assertTrue(response.stdout().contains("3-6-9-12"));
    }

    @Test
    void shouldBlockDangerousPythonCodeInLocalFallback() {
        CodeSandboxConfig noDockerConfig = new CodeSandboxConfig(
                true, 10, 65536, false, true, "python:3.11-slim", "node:20-alpine", "256m", "1.0");
        AgentConfig agentConfig = new AgentConfig(true, 5, 30, false, null, false, null, null, 2048, 1, noDockerConfig);
        AiProviderProperties props = mock(AiProviderProperties.class);
        when(props.resolveAgent()).thenReturn(agentConfig);
        CodeExecutionService localService = new CodeExecutionService(props);

        String dangerousCode = "import subprocess\nsubprocess.run(['ls', '-la'])";
        CodeExecutionService.ExecutionResponse response = localService.execute("python", dangerousCode);

        assertNotNull(response);
        assertEquals("error", response.status());
        assertEquals("local-blocked", response.sandboxType());
        assertTrue(response.stderr().contains("安全拦截"));
    }

    @Test
    void shouldBlockDangerousJsCodeInLocalFallback() {
        CodeSandboxConfig noDockerConfig = new CodeSandboxConfig(
                true, 10, 65536, false, true, "python:3.11-slim", "node:20-alpine", "256m", "1.0");
        AgentConfig agentConfig = new AgentConfig(true, 5, 30, false, null, false, null, null, 2048, 1, noDockerConfig);
        AiProviderProperties props = mock(AiProviderProperties.class);
        when(props.resolveAgent()).thenReturn(agentConfig);
        CodeExecutionService localService = new CodeExecutionService(props);

        String dangerousCode = "const cp = require('child_process'); cp.execSync('whoami');";
        CodeExecutionService.ExecutionResponse response = localService.execute("javascript", dangerousCode);

        assertNotNull(response);
        assertEquals("error", response.status());
        assertEquals("local-blocked", response.sandboxType());
        assertTrue(response.stderr().contains("安全拦截"));
    }

    @Test
    void shouldCaptureGeneratedImageArtifacts() {
        // Python 脚本生成一个简易图片文件到当前目录
        String code = """
                with open('chart.png', 'wb') as f:
                    # 写入最小 PNG 头部字节
                    f.write(bytes([137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82, 0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0, 31, 21, 196, 137]))
                print("Chart generated successfully")
                """;
        CodeExecutionService.ExecutionResponse response = service.execute("python", code);

        assertNotNull(response);
        assertEquals(0, response.exitCode());
        assertTrue(response.stdout().contains("Chart generated successfully"));
        assertFalse(response.images().isEmpty(), "应成功捕获当前目录下生成的 chart.png");
        CodeExecutionService.ImageArtifact img = response.images().get(0);
        assertEquals("chart.png", img.name());
        assertEquals("image/png", img.mimeType());
        assertTrue(img.data().startsWith("data:image/png;base64,"));
    }

    @Test
    void shouldHandleEmptyCodeGracefully() {
        CodeExecutionService.ExecutionResponse response = service.execute("python", "   ");
        assertNotNull(response);
        assertEquals("error", response.status());
        assertTrue(response.stderr().contains("为空"));
    }
}
