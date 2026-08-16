package xyz.ppmblszdp.ai.tool.websearch;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 结构化单条搜索结果模型。
 *
 * @param title       搜索结果标题
 * @param snippet     搜索结果摘要/片段
 * @param url         原文链接 URL
 * @param publishedAt 发布时间（可选）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchResultItem(String title, String snippet, String url, String publishedAt) {}
