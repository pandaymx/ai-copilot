package xyz.ppmblszdp.ai.tool.websearch;

/**
 * 搜索引擎统一客户端策略接口。
 */
public interface WebSearchClient {

    /**
     * 获取当前提供商名称（如 tavily / serpapi / bing）。
     */
    String getProviderName();

    /**
     * 执行网络搜索。
     *
     * @param query     搜索关键词
     * @param topK      结果数量（1~10）
     * @param timeRange 时间范围过滤（day/week/month/year 或 null）
     * @return 结构化搜索结果
     */
    WebSearchResult search(String query, int topK, String timeRange);
}
