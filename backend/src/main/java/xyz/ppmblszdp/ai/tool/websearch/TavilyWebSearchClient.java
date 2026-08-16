package xyz.ppmblszdp.ai.tool.websearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import xyz.ppmblszdp.ai.config.AiProviderProperties;

/**
 * Tavily AI 搜索引擎客户端实现。
 */
public class TavilyWebSearchClient implements WebSearchClient {

    private static final Logger log = LoggerFactory.getLogger(TavilyWebSearchClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_BASE_URL = "https://api.tavily.com";

    private final RestClient restClient;
    private final String apiKey;

    public TavilyWebSearchClient(AiProviderProperties.WebSearchConfig config) {
        String baseUrl =
                (config != null && config.baseUrl() != null && !config.baseUrl().isBlank())
                        ? config.baseUrl()
                        : DEFAULT_BASE_URL;
        this.apiKey = (config != null && config.apiKey() != null) ? config.apiKey() : "";

        int timeoutMs =
                (config != null && config.resolveTimeoutSeconds() > 0) ? config.resolveTimeoutSeconds() * 1000 : 15000;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);

        this.restClient =
                RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public String getProviderName() {
        return "tavily";
    }

    @Override
    public WebSearchResult search(String query, int topK, String timeRange) {
        if (apiKey.isBlank()) {
            log.warn("Tavily API key 未配置，无法执行在线网络搜索");
            return WebSearchResult.empty(query);
        }

        try {
            Map<String, Object> req = new HashMap<>();
            req.put("api_key", apiKey);
            req.put("query", query);
            req.put("max_results", Math.min(Math.max(topK, 1), 10));
            if (timeRange != null && !timeRange.isBlank()) {
                req.put("time_range", timeRange.trim().toLowerCase());
            }

            String respBody = restClient
                    .post()
                    .uri("/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(MAPPER.writeValueAsString(req))
                    .retrieve()
                    .body(String.class);

            if (respBody == null || respBody.isBlank()) {
                return WebSearchResult.empty(query);
            }

            JsonNode root = MAPPER.readTree(respBody);
            JsonNode resultsNode = root.path("results");
            List<SearchResultItem> items = new ArrayList<>();

            if (resultsNode.isArray()) {
                for (JsonNode item : resultsNode) {
                    String title = item.path("title").asText("");
                    String snippet = item.has("content")
                            ? item.path("content").asText("")
                            : item.path("snippet").asText("");
                    String url = item.path("url").asText("");
                    String publishedAt = item.has("published_date")
                            ? item.path("published_date").asText("")
                            : null;
                    if (!title.isBlank() || !url.isBlank()) {
                        items.add(new SearchResultItem(title, snippet, url, publishedAt));
                    }
                }
            }

            return new WebSearchResult(query, items.size(), items);
        } catch (Exception e) {
            log.error("Tavily 搜索请求失败: query={}, error={}", query, e.getMessage());
            return WebSearchResult.empty(query);
        }
    }
}
