package xyz.ppmblszdp.ai.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import xyz.ppmblszdp.ai.identity.AuthProperties;

class McpServerEndpointTest {

    private McpServerProperties properties;
    private ToolToMcpAdapter toolAdapter;
    private RagResourceProvider ragResourceProvider;
    private AuthProperties authProperties;
    private McpServerEndpoint endpoint;

    @BeforeEach
    void setUp() {
        properties = new McpServerProperties();
        properties.setEnabled(true);
        toolAdapter = mock(ToolToMcpAdapter.class);
        ragResourceProvider = mock(RagResourceProvider.class);
        authProperties = new AuthProperties("dev", "X-User-Id", Set.of("admin"));

        endpoint = new McpServerEndpoint(properties, toolAdapter, ragResourceProvider, authProperties);
    }

    @Test
    void initialize_ReturnsCapabilities() {
        var req = new McpProtocolDto.JsonRpcRequest("2.0", 1, "initialize", Map.of());
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/mcp/message").build());

        ResponseEntity<McpProtocolDto.JsonRpcResponse> response = endpoint.handleJsonRpc(req, null, exchange);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().result()).isInstanceOf(McpProtocolDto.InitializeResult.class);

        var initResult = (McpProtocolDto.InitializeResult) response.getBody().result();
        assertThat(initResult.serverInfo().name()).isEqualTo("ai-copilot-mcp-server");
    }

    @Test
    void toolsList_ReturnsTools() {
        when(toolAdapter.listTools())
                .thenReturn(List.of(new McpProtocolDto.McpToolDefinition("calculator", "计算器", Map.of())));

        var req = new McpProtocolDto.JsonRpcRequest("2.0", 2, "tools/list", Map.of());
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/mcp/message").build());

        ResponseEntity<McpProtocolDto.JsonRpcResponse> response = endpoint.handleJsonRpc(req, null, exchange);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().result()).isInstanceOf(Map.class);
    }

    @Test
    void toolsCall_ExecutesTool() {
        when(toolAdapter.callTool(anyString(), any(), anyString()))
                .thenReturn(McpProtocolDto.McpCallToolResult.success("计算结果: 42"));

        var req = new McpProtocolDto.JsonRpcRequest(
                "2.0", 3, "tools/call", Map.of("name", "calculator", "arguments", Map.of("expression", "6 * 7")));
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/mcp/message").build());

        ResponseEntity<McpProtocolDto.JsonRpcResponse> response = endpoint.handleJsonRpc(req, null, exchange);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().result()).isInstanceOf(McpProtocolDto.McpCallToolResult.class);
        var result = (McpProtocolDto.McpCallToolResult) response.getBody().result();
        assertThat(result.isError()).isFalse();
        assertThat(result.content().get(0).text()).contains("42");
    }
}
