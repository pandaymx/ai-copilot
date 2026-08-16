package xyz.ppmblszdp.ai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import reactor.core.publisher.Sinks;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.dto.ChatChunkDto;
import xyz.ppmblszdp.ai.tool.websearch.BingWebSearchClient;
import xyz.ppmblszdp.ai.tool.websearch.SearchResultItem;
import xyz.ppmblszdp.ai.tool.websearch.SerpApiWebSearchClient;
import xyz.ppmblszdp.ai.tool.websearch.TavilyWebSearchClient;
import xyz.ppmblszdp.ai.tool.websearch.WebSearchClient;
import xyz.ppmblszdp.ai.tool.websearch.WebSearchResult;

class WebSearchToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WebSearchTool webSearchTool;
    private ToolContext toolContext;
    private Sinks.Many<ChatChunkDto> sink;
    private AiProviderProperties props;
    private AiProviderProperties.AgentConfig agentConfig;

    @BeforeEach
    void setUp() {
        props = mock(AiProviderProperties.class);
        agentConfig = mock(AiProviderProperties.AgentConfig.class);
        when(props.resolveAgent()).thenReturn(agentConfig);
        when(agentConfig.resolveMaxToolCalls()).thenReturn(5);
        when(agentConfig.resolveTimeoutSeconds()).thenReturn(30);

        AiProviderProperties.WebSearchConfig webSearchConfig =
                new AiProviderProperties.WebSearchConfig("tavily", "test-key", "https://api.tavily.com", 15);
        when(agentConfig.resolveWebSearch()).thenReturn(webSearchConfig);

        webSearchTool = new WebSearchTool(props);

        ToolEventEmitter emitter = new ToolEventEmitter(props);
        sink = emitter.newSink();

        Map<String, Object> ctxMap = new HashMap<>();
        ctxMap.put(ToolEventEmitter.CTX_EMITTER, emitter);
        ctxMap.put("eventSink", sink);
        toolContext = new ToolContext(ctxMap);
    }

    @Test
    @DisplayName("当 query 为空时应返回空搜索结果")
    void shouldReturnEmptyResultWhenQueryIsBlank() throws Exception {
        String result = webSearchTool.search("", 5, null, toolContext);
        JsonNode node = MAPPER.readTree(result);
        assertThat(node.path("count").asInt()).isEqualTo(0);
        assertThat(node.path("results").size()).isEqualTo(0);
    }

    @Test
    @DisplayName("应该根据配置正确解析各搜索引擎客户端")
    void shouldResolveConfiguredClient() {
        when(agentConfig.resolveWebSearch())
                .thenReturn(new AiProviderProperties.WebSearchConfig("serpapi", "test-key", null, 15));
        WebSearchClient serpClient = webSearchTool.resolveClient();
        assertThat(serpClient).isInstanceOf(SerpApiWebSearchClient.class);
        assertThat(serpClient.getProviderName()).isEqualTo("serpapi");

        when(agentConfig.resolveWebSearch())
                .thenReturn(new AiProviderProperties.WebSearchConfig("bing", "test-key", null, 15));
        WebSearchClient bingClient = webSearchTool.resolveClient();
        assertThat(bingClient).isInstanceOf(BingWebSearchClient.class);
        assertThat(bingClient.getProviderName()).isEqualTo("bing");

        when(agentConfig.resolveWebSearch())
                .thenReturn(new AiProviderProperties.WebSearchConfig("tavily", "test-key", null, 15));
        WebSearchClient tavilyClient = webSearchTool.resolveClient();
        assertThat(tavilyClient).isInstanceOf(TavilyWebSearchClient.class);
        assertThat(tavilyClient.getProviderName()).isEqualTo("tavily");
    }

    @Test
    @DisplayName("API Key 未配置时应安全返回空结果而非抛出未捕获异常")
    void shouldHandleMissingApiKeyGracefully() throws Exception {
        AiProviderProperties.WebSearchConfig noKeyConfig =
                new AiProviderProperties.WebSearchConfig("tavily", "", null, 15);
        TavilyWebSearchClient client = new TavilyWebSearchClient(noKeyConfig);
        WebSearchResult res = client.search("Spring Boot 4.0", 5, null);

        assertThat(res.count()).isEqualTo(0);
        assertThat(res.results()).isEmpty();
    }

    @Test
    @DisplayName("正常执行搜索并发出 tool_call 与 tool_result SSE 帧")
    void shouldEmitEventsOnSearch() {
        WebSearchTool customTool = new WebSearchTool(props) {
            @Override
            WebSearchClient resolveClient() {
                return new WebSearchClient() {
                    @Override
                    public String getProviderName() {
                        return "mock";
                    }

                    @Override
                    public WebSearchResult search(String query, int topK, String timeRange) {
                        return new WebSearchResult(
                                query,
                                1,
                                List.of(new SearchResultItem(
                                        "Spring Boot Official",
                                        "Spring framework news",
                                        "https://spring.io",
                                        "2026-08-01")));
                    }
                };
            }
        };

        String json = customTool.search("Spring AI 2.0", 3, "week", toolContext);
        assertThat(json).contains("https://spring.io");
        assertThat(json).contains("Spring Boot Official");

        List<ChatChunkDto> chunks = sink.asFlux().take(2).collectList().block();
        assertThat(chunks).isNotNull();
        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).type()).isEqualTo("tool_call");
        assertThat(chunks.get(1).type()).isEqualTo("tool_result");
        assertThat(chunks.get(1).isError()).isFalse();
    }
}
