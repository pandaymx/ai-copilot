package xyz.ppmblszdp.ai.dto;

import java.util.List;

/**
 * 消息固定（Pin）、收藏（Bookmark）与标签（Tags）数据传输对象。
 */
public class MessageMetaDto {

    public record MessageBookmarkDto(
            String id,
            String userId,
            String sessionId,
            String messageId,
            String role,
            String content,
            List<String> tags,
            boolean pinned,
            boolean bookmarked,
            long createdAt) {}

    public record ToggleBookmarkRequest(String sessionId, String role, String content, List<String> tags) {}

    public record TogglePinRequest(String sessionId, String role, String content) {}

    public record UpdateTagsRequest(List<String> tags) {}

    public record MessageStatusResponse(boolean pinned, boolean bookmarked, List<String> tags) {}
}
