package xyz.ppmblszdp.ai.rag.dto;

/**
 * RAG 结构化抽取请求参数 DTO。
 *
 * @param query      提问或检索主题（若 rawText 为空，将利用 query 检索 RAG 文档库）
 * @param rawText    直接待抽取的原始文本（可选，若提供则优先抽取此文本）
 * @param userId     用户隔离 ID
 * @param sourceType 来源类型过滤
 * @param topK       检索 RAG 文档切片数量
 */
public record RagExtractRequest(String query, String rawText, String userId, String sourceType, Integer topK) {}
