package xyz.ppmblszdp.ai.tool.websearch;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 结构化网络搜索统一返回对象。
 * 与前端 ToolResultRenderer (WebSearchRenderer) 数据结构对齐。
 *
 * @param query   检索关键词
 * @param count   结果条数
 * @param results 检索结果列表
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WebSearchResult(String query, int count, List<SearchResultItem> results) {

    public static WebSearchResult empty(String query) {
        return new WebSearchResult(query, 0, List.of());
    }
}
