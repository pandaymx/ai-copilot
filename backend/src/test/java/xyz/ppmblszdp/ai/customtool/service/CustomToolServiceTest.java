package xyz.ppmblszdp.ai.customtool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import xyz.ppmblszdp.ai.customtool.dto.CustomToolDto;
import xyz.ppmblszdp.ai.customtool.dto.CustomToolDto.HttpConfigDto;
import xyz.ppmblszdp.ai.customtool.dto.CustomToolDto.ScriptConfigDto;
import xyz.ppmblszdp.ai.customtool.dto.CustomToolDto.ToolTestRequest;
import xyz.ppmblszdp.ai.customtool.dto.CustomToolDto.ToolTestResponse;
import xyz.ppmblszdp.ai.customtool.model.CustomToolType;
import xyz.ppmblszdp.ai.customtool.repository.CustomToolRepository;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.service.CodeExecutionService;
import xyz.ppmblszdp.ai.service.CodeExecutionService.ExecutionResponse;

class CustomToolServiceTest {

    private CustomToolRepository repository;
    private CodeExecutionService codeExecutionService;
    private ProviderRegistry providerRegistry;
    private DynamicToolCallbackFactory callbackFactory;
    private CustomToolService customToolService;

    @BeforeEach
    void setUp() {
        repository = mock(CustomToolRepository.class);
        codeExecutionService = mock(CodeExecutionService.class);
        providerRegistry = mock(ProviderRegistry.class);
        callbackFactory = new DynamicToolCallbackFactory(codeExecutionService, providerRegistry);
        customToolService = new CustomToolService(repository, callbackFactory);
    }

    @Test
    void testValidation_ReservedName_ThrowsException() {
        CustomToolDto dto = new CustomToolDto(
                null,
                "calculator", // 保留字
                "My Calculator",
                "desc",
                CustomToolType.HTTP,
                true,
                "{}",
                new HttpConfigDto("http://api.com", "GET", null, null, null, null, null, null, 30),
                null,
                null,
                null,
                null);

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> customToolService.createTool(dto, "user-1"));
        assertTrue(ex.getMessage().contains("系统保留关键字"));
    }

    @Test
    void testValidation_InvalidNameFormat_ThrowsException() {
        CustomToolDto dto = new CustomToolDto(
                null,
                "bad name with spaces!",
                "Bad",
                "desc",
                CustomToolType.HTTP,
                true,
                "{}",
                null,
                null,
                null,
                null,
                null);

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> customToolService.createTool(dto, "user-1"));
        assertTrue(ex.getMessage().contains("字母、数字、下划线"));
    }

    @Test
    void testValidation_DuplicateName_ThrowsException() {
        when(repository.existsByNameAndUserId(eq("my_custom_tool"), eq("user-1"), any()))
                .thenReturn(true);

        CustomToolDto dto = new CustomToolDto(
                null,
                "my_custom_tool",
                "My Tool",
                "desc",
                CustomToolType.HTTP,
                true,
                "{}",
                new HttpConfigDto("http://api.com", "GET", null, null, null, null, null, null, 30),
                null,
                null,
                null,
                null);

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> customToolService.createTool(dto, "user-1"));
        assertTrue(ex.getMessage().contains("已存在"));
    }

    @Test
    void testCreateAndMaskSecrets() {
        when(repository.existsByNameAndUserId(anyString(), anyString(), any())).thenReturn(false);

        CustomToolDto dto = new CustomToolDto(
                null,
                "weather_tool",
                "天气查询",
                "查询指定城市天气",
                CustomToolType.HTTP,
                true,
                "{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}",
                new HttpConfigDto(
                        "http://api.weather.com/v1?city={{city}}",
                        "GET",
                        Map.of("Accept", "application/json"),
                        null,
                        null,
                        "BEARER",
                        null,
                        "sk-secret1234567890abcdef",
                        15),
                null,
                null,
                null,
                null);

        CustomToolDto created = customToolService.createTool(dto, "user-1");

        assertNotNull(created.id());
        assertNotNull(created.httpConfig());
        // 返回给前端的凭据应脱敏
        assertTrue(created.httpConfig().authToken().contains("****"));

        // 验证持久化层被调用
        verify(repository).save(any(CustomToolDto.class), eq("user-1"));
    }

    @Test
    void testGetCompiledTools_UsesCache() {
        CustomToolDto dto = new CustomToolDto(
                "tool-1",
                "test_tool",
                "Test",
                "desc",
                CustomToolType.HTTP,
                true,
                "{}",
                new HttpConfigDto("http://api.com", "GET", null, null, null, null, null, null, 10),
                null,
                null,
                1000L,
                1000L);

        when(repository.findByUserIdAndEnabledTrue("user-1")).thenReturn(List.of(dto));

        // 首次读取触发 DB 查询
        List<ToolCallback> tools1 = customToolService.getCompiledTools("user-1");
        assertEquals(1, tools1.size());
        assertEquals("test_tool", tools1.get(0).getToolDefinition().name());

        // 第二次读取命中缓存（不再次查询 DB）
        List<ToolCallback> tools2 = customToolService.getCompiledTools("user-1");
        assertEquals(1, tools2.size());

        // 主动 evict
        customToolService.evictCache("user-1");

        // 再次读取会重新从 DB 取
        List<ToolCallback> tools3 = customToolService.getCompiledTools("user-1");
        assertEquals(1, tools3.size());
    }

    @Test
    void testScriptTool_Execution() {
        CustomToolDto scriptTool = new CustomToolDto(
                "tool-py",
                "calc_fib",
                "Fibonacci",
                "计算斐波那契",
                CustomToolType.SCRIPT,
                true,
                "{\"type\":\"object\",\"properties\":{\"n\":{\"type\":\"integer\"}}}",
                null,
                new ScriptConfigDto("python", "print('fib result: 55')"),
                null,
                1000L,
                1000L);

        when(codeExecutionService.execute(eq("python"), anyString()))
                .thenReturn(new ExecutionResponse(
                        "success", "python", "docker", 0, "fib result: 55", "", 120L, List.of(), false));

        ToolTestRequest req = new ToolTestRequest(scriptTool, Map.of("n", 10));
        ToolTestResponse resp = customToolService.testTool(req, "user-1");

        assertEquals("SUCCESS", resp.status());
        assertEquals("fib result: 55", resp.output());
        assertFalse(resp.isTruncated());
    }

    @Test
    void testOutputTruncation_HardLimit() {
        StringBuilder largeOutput = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeOutput.append("line-").append(i).append(": some very long verbose text...\n");
        }

        CustomToolDto scriptTool = new CustomToolDto(
                "tool-large",
                "large_output",
                "Large",
                "desc",
                CustomToolType.SCRIPT,
                true,
                "{}",
                null,
                new ScriptConfigDto("python", "print(large)"),
                null,
                1000L,
                1000L);

        when(codeExecutionService.execute(eq("python"), anyString()))
                .thenReturn(new ExecutionResponse(
                        "success", "python", "docker", 0, largeOutput.toString(), "", 50L, List.of(), false));

        ToolTestRequest req = new ToolTestRequest(scriptTool, Map.of());
        ToolTestResponse resp = customToolService.testTool(req, "user-1");

        assertEquals("SUCCESS", resp.status());
        assertTrue(resp.isTruncated());
        assertTrue(resp.output().contains("[输出过长已截断"));
    }
}
