package xyz.ppmblszdp.ai.rag.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 文档对话精确引用元数据 DTO。
 *
 * @param citationId 引用序号/标识（如 "1", "2"）
 * @param docId 文档全局唯一标识
 * @param fileName 文档名称（如 "劳动合同.pdf"）
 * @param pageNumber 页码（如 "3"，纯文本/Markdown 时为 null 或 "1"）
 * @param paragraphIndex 段落编号（如 "2"）
 * @param snippet 引用的原始切片片段/文本摘要
 * @param similarityScore 相似度/相关度得分（可选）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocumentCitationDto(
        String citationId,
        String docId,
        String fileName,
        String pageNumber,
        String paragraphIndex,
        String snippet,
        Double similarityScore) {}
