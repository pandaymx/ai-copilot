package xyz.ppmblszdp.ai.tool.email;

import java.util.List;

/**
 * 邮件工具相关数据传输对象。
 */
public class EmailDto {

    public record EmailSendRequest(List<String> to, String subject, String body, boolean isHtml, String action) {}

    public record EmailSendResult(
            String messageId, List<String> to, String subject, String status, String preview, long timestamp) {}

    public record EmailHistoryItem(
            String id,
            String userId,
            List<String> to,
            String subject,
            String bodySnippet,
            boolean isHtml,
            String status,
            long createdAt) {}
}
