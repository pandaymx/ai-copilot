package xyz.ppmblszdp.ai.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.tool.websearch.BingWebSearchClient;
import xyz.ppmblszdp.ai.tool.websearch.SerpApiWebSearchClient;
import xyz.ppmblszdp.ai.tool.websearch.TavilyWebSearchClient;
import xyz.ppmblszdp.ai.tool.websearch.WebSearchClient;
import xyz.ppmblszdp.ai.tool.websearch.WebSearchResult;

/**
 * Web 搜索工具（WebSearchTool）：供 Agent 在对话中进行互联网搜索，
 * 获取最新信息、新闻、文档与数据。
 *
 * <p>支持 Tavily / SerpAPI / Bing 等多种搜索引擎后端，默认配置化可切换。
 * 统一经 {@link ToolEventEmitter#executeWithEvent} 发送 SSE 帧并包装超时与错误处理。
 */
@Component
public class WebSearchTool {

    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 10;

    private final AiProviderProperties properties;

    public WebSearchTool(AiProviderProperties properties) {
        this.properties = properties;
    }

    @Tool(
            name = "web_search",
            description = "网络搜索工具：通过搜索引擎查询互联网上的最新信息、实时新闻、文档与数据。"
                    + "支持结构化返回标题(title)、摘要(snippet)、链接(url)与发布时间(publishedAt)。"
                    + "适用于需要获取 2024 年以后的最新事实、实时动态或特定外部网页内容的场景。")
    public String search(
            @ToolParam(description = "搜索查询词或关键短语，必填") String query,
            @ToolParam(description = "期望返回的结果数量，默认 5，最大 10") Integer topK,
            @ToolParam(description = "时间范围过滤：day | week | month | year，为空表示不限制") String timeRange,
            ToolContext toolContext) {

        int resolvedTopK = (topK != null && topK > 0) ? Math.min(topK, MAX_TOP_K) : DEFAULT_TOP_K;
        String argsJson = toJson(Map.of(
                "query", query == null ? "" : query,
                "topK", resolvedTopK,
                "timeRange", timeRange == null ? "" : timeRange));

        return ToolEventEmitter.from(toolContext).executeWithEvent("web_search", argsJson, toolContext, () -> {
            if (query == null || query.isBlank()) {
                return toJson(WebSearchResult.empty(""));
            }

            WebSearchClient client = resolveClient();
            log.info(
                    "执行 WebSearch [provider={}]: query={}, topK={}, timeRange={}",
                    client.getProviderName(),
                    query,
                    resolvedTopK,
                    timeRange);

            WebSearchResult result = client.search(query.trim(), resolvedTopK, timeRange);
            return toJson(result);
        });
    }

    WebSearchClient resolveClient() {
        AiProviderProperties.WebSearchConfig config = properties.resolveAgent().resolveWebSearch();
        String provider = config.resolveProvider();

        return switch (provider) {
            case "serpapi" -> new SerpApiWebSearchClient(config);
            case "bing" -> new BingWebSearchClient(config);
            case "tavily" -> new TavilyWebSearchClient(config);
            default -> {
                log.warn("未知的 web-search.provider [{}], 回退至 TavilyClient", provider);
                yield new TavilyWebSearchClient(config);
            }
        };
    }

    private static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{\"query\":\"\",\"count\":0,\"results\":[]}";
        }
    }
}
