package xyz.ppmblszdp.ai.rag.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 会话挂载文档元数据响应 DTO。
 *
 * @param docId 文档唯一标识
 * @param conversationId 所属会话 ID
 * @param fileName 文件名
 * @param sourceType 来源类型
 * @param chunkCount 切片数量
 * @param ingestedAt 入库时间 ISO-8601
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocChatDocResponse(
        String docId, String conversationId, String fileName, String sourceType, int chunkCount, String ingestedAt) {}
