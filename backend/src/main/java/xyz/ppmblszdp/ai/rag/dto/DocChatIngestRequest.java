package xyz.ppmblszdp.ai.rag.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 会话文档入库请求 DTO。
 *
 * @param conversationId 关联会话 ID（必填）
 * @param sourceType 来源类型（PDF / MARKDOWN / TIKA / URL / TEXT）
 * @param fileName 文件名或文档标题
 * @param rawText 原始文本（TEXT 类型用）
 * @param targetUrl 目标网页 URL（URL 类型用）
 * @param fileStoragePath 服务端已存文件路径（PDF/TIKA/MARKDOWN 用）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocChatIngestRequest(
        String conversationId,
        String sourceType,
        String fileName,
        String rawText,
        String targetUrl,
        String fileStoragePath) {}
