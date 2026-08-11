package xyz.ppmblszdp.ai.rag.controller;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import xyz.ppmblszdp.ai.rag.dto.ConflictPolicy;

/**
 * 入库请求联合 DTO（回应风险4：REST 多源扩展性）。
 *
 * <p>三选一/多选一的联合结构，future-proof 避免二进制上传造成破坏性变更：
 * <ul>
 *   <li>{@code rawText}：纯文本内容</li>
 *   <li>{@code targetUrl}：网页 URL（经 SsrfGuard 校验后抓取）</li>
 *   <li>{@code fileStoragePath}：服务器端文件路径（PDF/Office/Markdown）</li>
 * </ul>
 *
 * @param sourceType       文档源类型（PDF / TIKA / MARKDOWN / URL / TEXT）
 * @param rawText          原始文本（TEXT 类型）
 * @param targetUrl        目标网页 URL（URL 类型）
 * @param fileStoragePath  服务器文件路径（PDF/TIKA/MARKDOWN 类型）
 * @param fileName         文件名标识（供元数据记录用），可为空
 * @param conflictPolicy   冲突策略（SKIP / OVERWRITE / FORCE_ADD）
 */
public record IngestRequest(
        @NotBlank String sourceType,
        String rawText,
        String targetUrl,
        String fileStoragePath,
        String fileName,
        @Nullable ConflictPolicy conflictPolicy
) {}
