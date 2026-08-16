package xyz.ppmblszdp.ai.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 将系统内置的 {@link ToolCallback} 适配为 MCP 协议 Tool 定义与执行器。
 */
@Component
public class ToolToMcpAdapter {

    private static final Logger log = LoggerFactory.getLogger(ToolToMcpAdapter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ObjectProvider<ToolCallback[]> toolCallbacksProvider;

    public ToolToMcpAdapter(ObjectProvider<ToolCallback[]> toolCallbacksProvider) {
        this.toolCallbacksProvider = toolCallbacksProvider;
    }

    public List<McpProtocolDto.McpToolDefinition> listTools() {
        ToolCallback[] callbacks = toolCallbacksProvider.getIfAvailable();
        if (callbacks == null || callbacks.length == 0) {
            return Collections.emptyList();
        }

        List<McpProtocolDto.McpToolDefinition> tools = new ArrayList<>();
        for (ToolCallback cb : callbacks) {
            try {
                var def = cb.getToolDefinition();
                Object schema = def.inputSchema();
                if (schema instanceof String schemaStr && !schemaStr.isBlank()) {
                    try {
                        schema = MAPPER.readValue(schemaStr, new TypeReference<Map<String, Object>>() {});
                    } catch (Exception ignored) {
                    }
                }
                tools.add(new McpProtocolDto.McpToolDefinition(def.name(), def.description(), schema));
            } catch (Exception e) {
                log.warn("转换 ToolCallback {} 为 MCP 定义异常: {}", cb, e.getMessage());
            }
        }
        return tools;
    }

    public McpProtocolDto.McpCallToolResult callTool(String toolName, Map<String, Object> arguments, String userId) {
        ToolCallback[] callbacks = toolCallbacksProvider.getIfAvailable();
        if (callbacks == null || callbacks.length == 0) {
            return McpProtocolDto.McpCallToolResult.error("系统当前未装配任何可用的 MCP 工具");
        }

        ToolCallback target = null;
        for (ToolCallback cb : callbacks) {
            if (cb.getToolDefinition().name().equalsIgnoreCase(toolName)) {
                target = cb;
                break;
            }
        }

        if (target == null) {
            return McpProtocolDto.McpCallToolResult.error("找不到名称为 '" + toolName + "' 的 MCP 工具");
        }

        try {
            String inputJson = arguments != null ? MAPPER.writeValueAsString(arguments) : "{}";
            log.info("MCP 外部调用工具: name={}, userId={}, args={}", toolName, userId, inputJson);
            String output = target.call(inputJson);
            return McpProtocolDto.McpCallToolResult.success(output != null ? output : "执行成功");
        } catch (Exception e) {
            log.error("执行 MCP 工具 {} 失败: {}", toolName, e.getMessage(), e);
            return McpProtocolDto.McpCallToolResult.error("工具执行异常: " + e.getMessage());
        }
    }
}
