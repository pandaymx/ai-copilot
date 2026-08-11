package xyz.ppmblszdp.ai.rag.dto;

import java.util.List;

/**
 * 结构化知识抽取 DTO（用于 Spring AI BeanOutputConverter 转换与结构化问答/知识图谱构建）。
 *
 * @param title    检索或提取文档的主题/标题
 * @param summary  核心摘要总结
 * @param entities 抽取出的实体列表
 * @param keyFacts 关键事实与结论列表
 */
public record StructuredKnowledge(
        String title,
        String summary,
        List<EntityItem> entities,
        List<String> keyFacts
) {
    /**
     * 抽取出的单个知识实体项。
     *
     * @param name        实体名称
     * @param type        实体类型（如：人物、机构、技术术语、产品、概念等）
     * @param description 实体属性描述与上下文关联
     */
    public record EntityItem(
            String name,
            String type,
            String description
    ) {}
}
