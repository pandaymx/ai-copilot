package xyz.ppmblszdp.ai.rag.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 文档切片与段落原文响应 DTO（供前端原文对照抽屉使用）。
 *
 * @param chunkId 切片唯一 ID
 * @param docId 文档 ID
 * @param fileName 文件名
 * @param pageNumber 页码（如存在）
 * @param paragraphIndex 段落/切片序号
 * @param content 切片原文内容
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocChunkResponse(
        String chunkId, String docId, String fileName, String pageNumber, String paragraphIndex, String content) {}
