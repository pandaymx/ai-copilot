package xyz.ppmblszdp.ai.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MCP Server 模式相关配置项。
 */
@Component
@ConfigurationProperties(prefix = "app.mcp.server")
public class McpServerProperties {

    /** 是否启用 MCP Server 端对外暴露自身工具与知识库（默认 true） */
    private boolean enabled = true;

    /** MCP 服务名称 */
    private String serverName = "ai-copilot-mcp-server";

    /** MCP 服务版本 */
    private String serverVersion = "2.0.0";

    /** 是否暴露 RAG 知识库为 MCP Resources */
    private boolean ragResourceEnabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getServerVersion() {
        return serverVersion;
    }

    public void setServerVersion(String serverVersion) {
        this.serverVersion = serverVersion;
    }

    public boolean isRagResourceEnabled() {
        return ragResourceEnabled;
    }

    public void setRagResourceEnabled(boolean ragResourceEnabled) {
        this.ragResourceEnabled = ragResourceEnabled;
    }
}
