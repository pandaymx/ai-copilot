package xyz.ppmblszdp.ai.tool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.ppmblszdp.ai.agent.SubAgentTool;
import xyz.ppmblszdp.ai.config.AiProviderProperties;

/**
 * Agent 工具注册表：将各 {@code @Tool} Bean 封装为 {@link ToolCallback} 数组，供 {@code ChatService}
 * 在 Agent 模式开启时注入 {@code ChatClient.tools(...)}。
 *
 * <p>当 {@code app.ai.agent.orchestrator-enabled=true} 时，额外将
 * {@link SubAgentTool}（三个子代理工具：分析 / 代码 / 摘要）注入工具集；
 * 默认关闭，不影响现有 Agent 路径。
 */
@Configuration
public class AgentToolRegistry {

    @Bean
    public ToolCallback[] agentToolCallbacks(
            CalculatorTool calculatorTool,
            CodeExecutionTool codeExecutionTool,
            HttpRequestTool httpRequestTool,
            FileTool fileTool,
            KnowledgeQueryTool knowledgeQueryTool,
            GitTool gitTool,
            CodeSearchTool codeSearchTool,
            CodeReviewTool codeReviewTool,
            ObjectProvider<SubAgentTool> subAgentToolProvider,
            AiProviderProperties properties) {

        // ToolCallbacks.from 自动扫描对象上所有 @Tool 注解方法
        List<ToolCallback> all = new ArrayList<>();
        all.addAll(Arrays.asList(ToolCallbacks.from(calculatorTool)));
        if (properties.resolveAgent().resolveCodeSandbox().isEnabled()) {
            all.addAll(Arrays.asList(ToolCallbacks.from(codeExecutionTool)));
        }
        all.addAll(Arrays.asList(ToolCallbacks.from(httpRequestTool)));
        all.addAll(Arrays.asList(ToolCallbacks.from(fileTool)));
        all.addAll(Arrays.asList(ToolCallbacks.from(knowledgeQueryTool)));
        all.addAll(Arrays.asList(ToolCallbacks.from(gitTool)));
        all.addAll(Arrays.asList(ToolCallbacks.from(codeSearchTool)));
        if (properties.resolveAgent().isCodeReviewEnabled()) {
            all.addAll(Arrays.asList(ToolCallbacks.from(codeReviewTool)));
        }

        // 按 orchestratorEnabled 开关条件性注入子代理工具（分析/代码/摘要三个 @Tool）
        if (properties.resolveAgent().isOrchestratorEnabled()) {
            SubAgentTool subAgentTool = subAgentToolProvider.getIfAvailable();
            if (subAgentTool != null) {
                all.addAll(Arrays.asList(ToolCallbacks.from(subAgentTool)));
            }
        }

        return all.toArray(new ToolCallback[0]);
    }
}
