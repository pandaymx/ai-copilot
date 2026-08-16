package xyz.ppmblszdp.ai.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;

/**
 * MCP Server 端核心交互控制器（McpServerEndpoint）。
 *
 * <p>遵循 Model Context Protocol (MCP) 规范，提供 JSON-RPC 2.0 消息处理与 SSE 双向通道。
 */
@RestController
@RequestMapping("/mcp")
public class McpServerEndpoint {

    private static final Logger log = LoggerFactory.getLogger(McpServerEndpoint.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final McpServerProperties properties;
    private final ToolToMcpAdapter toolAdapter;
    private final RagResourceProvider ragResourceProvider;
    private final AuthProperties authProperties;

    public McpServerEndpoint(
            McpServerProperties properties,
            ToolToMcpAdapter toolAdapter,
            RagResourceProvider ragResourceProvider,
            AuthProperties authProperties) {
        this.properties = properties;
        this.toolAdapter = toolAdapter;
        this.ragResourceProvider = ragResourceProvider;
        this.authProperties = authProperties;
    }

    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> sseConnect() {
        String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("MCP 客户端建立 SSE 通道: sessionId={}", sessionId);

        ServerSentEvent<String> endpointEvent = ServerSentEvent.<String>builder()
                .event("endpoint")
                .data("/mcp/message?sessionId=" + sessionId)
                .build();

        // 保持心跳连接
        Flux<ServerSentEvent<String>> heartbeats = Flux.interval(Duration.ofSeconds(20))
                .map(seq -> ServerSentEvent.<String>builder()
                        .event("ping")
                        .data("{}")
                        .build());

        return Flux.concat(Flux.just(endpointEvent), heartbeats);
    }

    @PostMapping(
            value = "/message",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<McpProtocolDto.JsonRpcResponse> handleJsonRpc(
            @RequestBody McpProtocolDto.JsonRpcRequest req,
            @RequestParam(required = false) String sessionId,
            ServerWebExchange exchange) {

        if (!properties.isEnabled()) {
            return ResponseEntity.ok(McpProtocolDto.JsonRpcResponse.error(req.id(), -32000, "MCP Server 模式未启用"));
        }

        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        String method = req.method();
        Map<String, Object> params = req.params() != null ? req.params() : Collections.emptyMap();

        log.debug("收到 MCP JSON-RPC 请求: method={}, id={}, user={}", method, req.id(), userId);

        try {
            switch (method) {
                case "initialize" -> {
                    var capabilities = new McpProtocolDto.ServerCapabilities(Map.of(), Map.of(), Map.of());
                    var serverInfo =
                            new McpProtocolDto.ServerInfo(properties.getServerName(), properties.getServerVersion());
                    var result = new McpProtocolDto.InitializeResult("2024-11-05", capabilities, serverInfo);
                    return ResponseEntity.ok(McpProtocolDto.JsonRpcResponse.success(req.id(), result));
                }
                case "notifications/initialized" -> {
                    return ResponseEntity.ok(McpProtocolDto.JsonRpcResponse.success(req.id(), Map.of()));
                }
                case "ping" -> {
                    return ResponseEntity.ok(McpProtocolDto.JsonRpcResponse.success(req.id(), Map.of()));
                }
                case "tools/list" -> {
                    var tools = toolAdapter.listTools();
                    return ResponseEntity.ok(McpProtocolDto.JsonRpcResponse.success(req.id(), Map.of("tools", tools)));
                }
                case "tools/call" -> {
                    String toolName = (String) params.get("name");
                    Map<String, Object> arguments = null;
                    if (params.get("arguments") instanceof Map<?, ?> argMap) {
                        arguments = MAPPER.convertValue(argMap, new TypeReference<Map<String, Object>>() {});
                    }
                    var callResult = toolAdapter.callTool(toolName, arguments, userId);
                    return ResponseEntity.ok(McpProtocolDto.JsonRpcResponse.success(req.id(), callResult));
                }
                case "resources/list" -> {
                    var resources = properties.isRagResourceEnabled()
                            ? ragResourceProvider.listResources(userId)
                            : Collections.emptyList();
                    return ResponseEntity.ok(
                            McpProtocolDto.JsonRpcResponse.success(req.id(), Map.of("resources", resources)));
                }
                case "resources/read" -> {
                    String uri = (String) params.get("uri");
                    var content = ragResourceProvider.readResource(userId, uri);
                    return ResponseEntity.ok(McpProtocolDto.JsonRpcResponse.success(
                            req.id(), Map.of("contents", java.util.List.of(content))));
                }
                default -> {
                    return ResponseEntity.ok(
                            McpProtocolDto.JsonRpcResponse.error(req.id(), -32601, "不支持的 MCP 方法: " + method));
                }
            }
        } catch (Exception e) {
            log.error("处理 MCP JSON-RPC 异常: {}", e.getMessage(), e);
            return ResponseEntity.ok(
                    McpProtocolDto.JsonRpcResponse.error(req.id(), -32603, "Internal error: " + e.getMessage()));
        }
    }

    @GetMapping("/tools")
    public ResponseEntity<?> getToolsRest() {
        return ResponseEntity.ok(Map.of(
                "tools",
                toolAdapter.listTools(),
                "count",
                toolAdapter.listTools().size()));
    }

    @GetMapping("/resources")
    public ResponseEntity<?> getResourcesRest(ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        var resources =
                properties.isRagResourceEnabled() ? ragResourceProvider.listResources(userId) : Collections.emptyList();
        return ResponseEntity.ok(Map.of("resources", resources));
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        return ResponseEntity.ok(Map.of(
                "enabled", properties.isEnabled(),
                "serverName", properties.getServerName(),
                "serverVersion", properties.getServerVersion(),
                "toolsCount", toolAdapter.listTools().size(),
                "ragResourceEnabled", properties.isRagResourceEnabled()));
    }
}
