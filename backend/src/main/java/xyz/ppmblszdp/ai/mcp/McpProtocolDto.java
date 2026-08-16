package xyz.ppmblszdp.ai.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * Model Context Protocol (MCP) JSON-RPC 2.0 协议对象模型。
 */
public class McpProtocolDto {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JsonRpcRequest(String jsonrpc, Object id, String method, Map<String, Object> params) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JsonRpcResponse(String jsonrpc, Object id, Object result, JsonRpcError error) {

        public static JsonRpcResponse success(Object id, Object result) {
            return new JsonRpcResponse("2.0", id, result, null);
        }

        public static JsonRpcResponse error(Object id, int code, String message) {
            return new JsonRpcResponse("2.0", id, null, new JsonRpcError(code, message, null));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JsonRpcError(int code, String message, Object data) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record McpToolDefinition(String name, String description, Object inputSchema) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record McpResourceDefinition(String uri, String name, String description, String mimeType) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record McpResourceContent(String uri, String mimeType, String text) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record McpContentItem(String type, String text) {

        public static McpContentItem text(String text) {
            return new McpContentItem("text", text);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record McpCallToolResult(List<McpContentItem> content, boolean isError) {

        public static McpCallToolResult success(String text) {
            return new McpCallToolResult(List.of(McpContentItem.text(text)), false);
        }

        public static McpCallToolResult error(String errorMsg) {
            return new McpCallToolResult(List.of(McpContentItem.text(errorMsg)), true);
        }
    }

    public record ServerCapabilities(
            Map<String, Object> tools, Map<String, Object> resources, Map<String, Object> prompts) {}

    public record ServerInfo(String name, String version) {}

    public record InitializeResult(String protocolVersion, ServerCapabilities capabilities, ServerInfo serverInfo) {}
}
