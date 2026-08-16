package xyz.ppmblszdp.ai.tool.websearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import xyz.ppmblszdp.ai.config.AiProviderProperties;

/**
 * Microsoft Bing Web Search API 客户端实现。
 */
public class BingWebSearchClient implements WebSearchClient {

    private static final Logger log = LoggerFactory.getLogger(BingWebSearchClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_BASE_URL = "https://api.bing.microsoft.com";

    private final RestClient restClient;
    private final String apiKey;

    public BingWebSearchClient(AiProviderProperties.WebSearchConfig config) {
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
        return "bing";
    }

    @Override
    public WebSearchResult search(String query, int topK, String timeRange) {
        if (apiKey.isBlank()) {
            log.warn("Bing API key 未配置，无法执行在线网络搜索");
            return WebSearchResult.empty(query);
        }

        try {
            int count = Math.min(Math.max(topK, 1), 10);
            StringBuilder uriBuilder = new StringBuilder("/v7.0/search");
            uriBuilder.append("?q=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
            uriBuilder.append("&count=").append(count);

            if (timeRange != null && !timeRange.isBlank()) {
                String freshness =
                        switch (timeRange.trim().toLowerCase()) {
                            case "day" -> "Day";
                            case "week" -> "Week";
                            case "month" -> "Month";
                            default -> null;
                        };
                if (freshness != null) {
                    uriBuilder.append("&freshness=").append(freshness);
                }
            }

            String respBody = restClient
                    .get()
                    .uri(uriBuilder.toString())
                    .header("Ocp-Apim-Subscription-Key", apiKey)
                    .retrieve()
                    .body(String.class);

            if (respBody == null || respBody.isBlank()) {
                return WebSearchResult.empty(query);
            }

            JsonNode root = MAPPER.readTree(respBody);
            JsonNode values = root.path("webPages").path("value");
            List<SearchResultItem> items = new ArrayList<>();

            if (values.isArray()) {
                for (JsonNode item : values) {
                    String title = item.path("name").asText("");
                    String snippet = item.path("snippet").asText("");
                    String url = item.path("url").asText("");
                    String publishedAt = item.has("datePublished")
                            ? item.path("datePublished").asText("")
                            : null;
                    if (!title.isBlank() || !url.isBlank()) {
                        items.add(new SearchResultItem(title, snippet, url, publishedAt));
                    }
                    if (items.size() >= count) {
                        break;
                    }
                }
            }

            return new WebSearchResult(query, items.size(), items);
        } catch (Exception e) {
            log.error("Bing 搜索请求失败: query={}, error={}", query, e.getMessage());
            return WebSearchResult.empty(query);
        }
    }
}
