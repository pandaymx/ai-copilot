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
import xyz.ppmblszdp.ai.memory.SafeEmbeddingModel;
import xyz.ppmblszdp.ai.safeguard.SafeGuardEngine;

class CodeSearchToolTest {

    private CodeSearchTool codeSearchTool;
    private ToolEventEmitter emitter;
    private final String userId = "test-user-search";

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ObjectProvider<SafeGuardEngine> mockSafeGuard = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<SafeEmbeddingModel> mockSafeEmbedding = mock(ObjectProvider.class);

        codeSearchTool = new CodeSearchTool(mockSafeGuard, mockSafeEmbedding, null);

        AiProviderProperties props = mock(AiProviderProperties.class);
        AiProviderProperties.AgentConfig agentConfig = mock(AiProviderProperties.AgentConfig.class);
        when(props.resolveAgent()).thenReturn(agentConfig);
        when(agentConfig.resolveMaxToolCalls()).thenReturn(10);
        when(agentConfig.resolveTimeoutSeconds()).thenReturn(30);

        emitter = new ToolEventEmitter(props);
    }

    private ToolContext createMockToolContext() {
        Sinks.Many<ChatChunkDto> sink = emitter.newSink();
        Map<String, Object> ctxMap = new HashMap<>();
        ctxMap.put(ToolEventEmitter.CTX_EMITTER, emitter);
        ctxMap.put("eventSink", sink);
        ctxMap.put(ToolEventEmitter.CTX_USER_ID, userId);
        return new ToolContext(ctxMap);
    }

    @Test
    void codeSearchRegex_andSymbols_andTree_shouldWork() throws Exception {
        Path userRepos = Paths.get(System.getProperty("java.io.tmpdir"), "agent-repos", userId, "search-repo");
        Files.createDirectories(userRepos.resolve("src/main/java"));
        Files.createDirectories(userRepos.resolve("src/test/java"));

        // 写入测试代码文件
        Path userJava = userRepos.resolve("src/main/java/UserService.java");
        Files.writeString(userJava, """
                package com.example.service;

                public class UserService {
                    public boolean validateToken(String token) {
                        return token != null && !token.isBlank();
                    }
                }
                """);

        Path authJava = userRepos.resolve("src/main/java/AuthFilter.java");
        Files.writeString(authJava, """
                package com.example.security;

                public class AuthFilter {
                    private final UserService userService;

                    public void doFilter() {
                        // 校验身份
                    }
                }
                """);

        ToolContext ctx = createMockToolContext();

        // 1. 测试 Regex 搜索
        String regexRes = codeSearchTool.codeSearchRegex("search-repo", "validateToken", ".java", 10, ctx);
        assertThat(regexRes).contains("UserService.java");
        assertThat(regexRes).contains("validateToken");

        // 2. 测试 Symbol 查找
        String symbolRes = codeSearchTool.codeFindSymbols("search-repo", "UserService", "CLASS", ctx);
        assertThat(symbolRes).contains("UserService.java");
        assertThat(symbolRes).contains("CLASS");

        // 3. 测试 Semantic 语义搜索（fallback 关键词模式）
        String semanticRes = codeSearchTool.codeSearchSemantic("search-repo", "validateToken 身份校验", ".java", 5, ctx);
        assertThat(semanticRes).contains("UserService.java");

        // 4. 测试 目录树与深度熔断
        String treeRes = codeSearchTool.codeFileTree("search-repo", 4, 100, ctx);
        assertThat(treeRes).contains("search-repo");
        assertThat(treeRes).contains("UserService.java");
        assertThat(treeRes).contains("AuthFilter.java");
    }

    @Test
    void codeFileTree_shouldTriggerTruncationWhenExceedingLimit() throws Exception {
        Path userRepos = Paths.get(System.getProperty("java.io.tmpdir"), "agent-repos", userId, "large-repo");
        Files.createDirectories(userRepos);

        for (int i = 0; i < 15; i++) {
            Files.writeString(userRepos.resolve("file_" + i + ".txt"), "test");
        }

        ToolContext ctx = createMockToolContext();

        // 限制最多展示 5 个文件
        String treeRes = codeSearchTool.codeFileTree("large-repo", 2, 5, ctx);
        assertThat(treeRes).contains("large-repo");
        assertThat(treeRes).contains("truncated: 文件数达到上限");
    }
}
