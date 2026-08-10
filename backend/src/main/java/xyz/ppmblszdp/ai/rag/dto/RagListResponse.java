package xyz.ppmblszdp.ai.rag.dto;

import java.util.List;
import java.util.Map;

/**
 * 知识库文档分页列表响应。
 *
 * @param items            当前页文档聚合项
 * @param total            满足过滤条件的总文档（source）数
 * @param sourceTypeCounts 各 sourceType 的文档（source）数分布，供前端过滤徽标
 */
public record RagListResponse(
        List<RagDocumentMeta> items,
        long total,
        Map<String, Long> sourceTypeCounts
) {}
