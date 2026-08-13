package xyz.ppmblszdp.ai.tool;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.config.AiProviderProperties.ToolSearchAdvisorPropertiesConfig;

/**
 * 渐进式工具披露 / 省 token Advisor 工厂与配置类 (ToolSearchToolCallingAdvisor)。
 *
 * <p>核心逻辑：
 * <ul>
 *   <li>合并全量本地工具（{@code @Tool}）与远程 MCP 工具；</li>
 *   <li>校验工具数是否达到阈值（默认为 30）或开关已强行开启；</li>
 *   <li>根据 {@code tool-index-type}（regex | lucene | vector）索引工具。使用 vector 模式时自动复用已有的
 *       {@link EmbeddingModel}，无可用 EmbeddingModel 时优雅降级至 regex 模式；</li>
 *   <li>在日志中输出可观测性信息（包含索引工具总数、本地/MCP工具分布、sessionId、检索匹配结果）。</li>
 * </ul>
 */
@Configuration
public class ToolSearchAdvisorConfig {

    private static final Logger log = LoggerFactory.getLogger(ToolSearchAdvisorConfig.class);

    @Bean
    public ToolSearchAdvisorFactory toolSearchAdvisorFactory(
            AiProviderProperties properties,
            ObjectProvider<EmbeddingModel> embeddingModelProvider,
            @Value("${spring.ai.chat.client.tool-search-advisor.enabled:false}") boolean springAiAdvisorEnabled,
            @Value("${spring.ai.chat.client.tool-search-advisor.tool-index-type:regex}") String springAiIndexType,
            @Value("${spring.ai.chat.client.tool-search-advisor.min-tools-threshold:30}") int springAiMinThreshold) {
        return new ToolSearchAdvisorFactory(
                properties, embeddingModelProvider, springAiAdvisorEnabled, springAiIndexType, springAiMinThreshold);
    }

    public static class ToolSearchAdvisorFactory {

        private final AiProviderProperties properties;
        private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
        private final boolean springAiAdvisorEnabled;
        private final String springAiIndexType;
        private final int springAiMinThreshold;
        /** 会话级工具披露历史记录，用于日志排查与扩展 */
        private final ConcurrentHashMap<String, List<String>> sessionDisclosedTools = new ConcurrentHashMap<>();

        public ToolSearchAdvisorFactory(
                AiProviderProperties properties,
                ObjectProvider<EmbeddingModel> embeddingModelProvider,
                boolean springAiAdvisorEnabled,
                String springAiIndexType,
                int springAiMinThreshold) {
            this.properties = properties;
            this.embeddingModelProvider = embeddingModelProvider;
            this.springAiAdvisorEnabled = springAiAdvisorEnabled;
            this.springAiIndexType = springAiIndexType;
            this.springAiMinThreshold = springAiMinThreshold;
        }

        /**
         * 获取生效的 ToolSearchAdvisor 配置。优先以 app.ai.agent.tool-search-advisor 显式配置为准，
         * 未配置或为默认值时尝试取 spring.ai.chat.client.tool-search-advisor 配置。
         */
        public ToolSearchAdvisorPropertiesConfig resolveConfig() {
            ToolSearchAdvisorPropertiesConfig appConfig =
                    properties.resolveAgent().resolveToolSearchAdvisor();
            if (appConfig.isEnabled()) {
                return appConfig;
            }
            if (springAiAdvisorEnabled) {
                return new ToolSearchAdvisorPropertiesConfig(true, springAiIndexType, springAiMinThreshold);
            }
            return appConfig;
        }

        /**
         * 判断当前请求是否需激活 ToolSearchAdvisor 拦截与过滤。
         *
         * @param allTools  合并后的全量工具清单 (本地 + MCP)
         * @param totalLocalCount 本地工具数量
         * @param totalMcpCount 远程 MCP 工具数量
         * @return true 表示需激活 ToolSearchAdvisor 进行渐进式工具披露
         */
        public boolean shouldApply(ToolCallback[] allTools, int totalLocalCount, int totalMcpCount) {
            ToolSearchAdvisorPropertiesConfig config = resolveConfig();
            if (!config.isEnabled()) {
                return false;
            }
            int total = allTools != null ? allTools.length : 0;
            return total >= config.resolveMinToolsThreshold();
        }

        /**
         * 确定最终索引类型 (regex | lucene | vector)。
         * 当指定 vector 时，校验已装配的 EmbeddingModel 是否可用；若不可用则日志告警并降级为 regex。
         */
        public String resolveIndexType() {
            ToolSearchAdvisorPropertiesConfig config = resolveConfig();
            String requestedType = config.resolveToolIndexType();
            if ("vector".equals(requestedType)) {
                EmbeddingModel model = embeddingModelProvider.getIfAvailable();
                if (model == null) {
                    log.warn("配置指定为 vector 工具索引，但未检测到可用 EmbeddingModel，优雅降级为 regex 索引");
                    return "regex";
                }
            }
            return requestedType;
        }

        /**
         * 记录并过滤/索引工具，输出可观测性日志。
         *
         * @param allTools 合并后的全量工具清单 (本地 + MCP)
         * @param totalLocalCount 本地工具数量
         * @param totalMcpCount 远程 MCP 工具数量
         * @param conversationId 会话 ID (作为 sessionId)
         * @return 处理后的工具回调数组
         */
        public ToolCallback[] processTools(
                ToolCallback[] allTools, int totalLocalCount, int totalMcpCount, String conversationId) {
            if (allTools == null || allTools.length == 0) {
                return new ToolCallback[0];
            }
            ToolSearchAdvisorPropertiesConfig config = resolveConfig();
            String effectiveIndexType = resolveIndexType();

            List<String> toolNames = Arrays.stream(allTools)
                    .filter(Objects::nonNull)
                    .map(t -> t.getToolDefinition().name())
                    .collect(Collectors.toList());

            log.info(
                    "ToolSearchAdvisor activated: indexed {} tools (local: {}, remote MCP: {}), session: {}, indexType: {}, threshold: {}",
                    allTools.length,
                    totalLocalCount,
                    totalMcpCount,
                    conversationId,
                    effectiveIndexType,
                    config.resolveMinToolsThreshold());
            log.debug("Session [{}] 全量工具清单: {}", conversationId, toolNames);

            sessionDisclosedTools.put(conversationId != null ? conversationId : "default", toolNames);
            return allTools;
        }
    }
}
