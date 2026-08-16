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
 * <p>部分工具按配置开关条件性注入，避免默认开启影响现有 Agent 路径：
 * <ul>
 *   <li>{@code app.ai.agent.calendar-task-enabled}：注入 {@link CalendarTool} / {@link TaskTool}（默认关闭）；</li>
 *   <li>{@code app.ai.agent.orchestrator-enabled}：注入 {@link SubAgentTool}（默认关闭）；</li>
 *   <li>code-sandbox / code-review 各自开关控制。</li>
 * </ul>
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
            TranslationTool translationTool,
            CalendarTool calendarTool,
            TaskTool taskTool,
            WebSearchTool webSearchTool,
            DatabaseQueryTool databaseQueryTool,
            EmailTool emailTool,
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
        all.addAll(Arrays.asList(ToolCallbacks.from(translationTool)));
        if (properties.resolveAgent().isCodeReviewEnabled()) {
            all.addAll(Arrays.asList(ToolCallbacks.from(codeReviewTool)));
        }

        // 日历与任务工具：默认关闭，需 app.ai.agent.calendar-task-enabled=true 才注入
        if (properties.resolveAgent().isCalendarTaskEnabled()) {
            all.addAll(Arrays.asList(ToolCallbacks.from(calendarTool)));
            all.addAll(Arrays.asList(ToolCallbacks.from(taskTool)));
        }

        // Web 搜索工具：默认关闭，需 app.ai.agent.web-search-enabled=true 才注入
        if (properties.resolveAgent().isWebSearchEnabled()) {
            all.addAll(Arrays.asList(ToolCallbacks.from(webSearchTool)));
        }

        // 数据库查询工具：默认关闭，需 app.ai.agent.db-query-enabled=true 才注入
        if (properties.resolveAgent().isDbQueryEnabled()) {
            all.addAll(Arrays.asList(ToolCallbacks.from(databaseQueryTool)));
        }

        // 邮件工具：默认关闭，需 app.ai.agent.email-enabled=true 才注入
        if (properties.resolveAgent().isEmailEnabled()) {
            all.addAll(Arrays.asList(ToolCallbacks.from(emailTool)));
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
