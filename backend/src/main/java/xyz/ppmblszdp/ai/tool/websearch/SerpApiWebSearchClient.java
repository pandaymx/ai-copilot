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
 * SerpAPI (Google Search) 客户端实现。
 */
public class SerpApiWebSearchClient implements WebSearchClient {

    private static final Logger log = LoggerFactory.getLogger(SerpApiWebSearchClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_BASE_URL = "https://serpapi.com";

    private final RestClient restClient;
    private final String apiKey;

    public SerpApiWebSearchClient(AiProviderProperties.WebSearchConfig config) {
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
        return "serpapi";
    }

    @Override
    public WebSearchResult search(String query, int topK, String timeRange) {
        if (apiKey.isBlank()) {
            log.warn("SerpAPI key 未配置，无法执行在线网络搜索");
            return WebSearchResult.empty(query);
        }

        try {
            int num = Math.min(Math.max(topK, 1), 10);
            StringBuilder uriBuilder = new StringBuilder("/search.json?engine=google");
            uriBuilder.append("&q=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
            uriBuilder.append("&num=").append(num);
            uriBuilder.append("&api_key=").append(URLEncoder.encode(apiKey, StandardCharsets.UTF_8));

            if (timeRange != null && !timeRange.isBlank()) {
                String tbs =
                        switch (timeRange.trim().toLowerCase()) {
                            case "day" -> "qdr:d";
                            case "week" -> "qdr:w";
                            case "month" -> "qdr:m";
                            case "year" -> "qdr:y";
                            default -> null;
                        };
                if (tbs != null) {
                    uriBuilder.append("&tbs=").append(tbs);
                }
            }

            String respBody =
                    restClient.get().uri(uriBuilder.toString()).retrieve().body(String.class);

            if (respBody == null || respBody.isBlank()) {
                return WebSearchResult.empty(query);
            }

            JsonNode root = MAPPER.readTree(respBody);
            JsonNode organic = root.path("organic_results");
            List<SearchResultItem> items = new ArrayList<>();

            if (organic.isArray()) {
                for (JsonNode item : organic) {
                    String title = item.path("title").asText("");
                    String snippet = item.path("snippet").asText("");
                    String url = item.path("link").asText("");
                    String publishedAt = item.has("date") ? item.path("date").asText("") : null;
                    if (!title.isBlank() || !url.isBlank()) {
                        items.add(new SearchResultItem(title, snippet, url, publishedAt));
                    }
                    if (items.size() >= num) {
                        break;
                    }
                }
            }

            return new WebSearchResult(query, items.size(), items);
        } catch (Exception e) {
            log.error("SerpAPI 搜索请求失败: query={}, error={}", query, e.getMessage());
            return WebSearchResult.empty(query);
        }
    }
}
