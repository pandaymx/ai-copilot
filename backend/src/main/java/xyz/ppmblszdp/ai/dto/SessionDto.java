package xyz.ppmblszdp.ai.dto;

import java.util.List;

public record SessionDto(String id, String title, long updatedAt, boolean isDefaultTitle) {
    public record SessionDetail(
            String id, String title, long updatedAt, boolean isDefaultTitle, List<MessageItem> messages) {}

    public record MessageItem(String id, String role, String content, List<MediaDto> media) {
        public MessageItem(String id, String role, String content) {
            this(id, role, content, null);
        }
    }

    public record RenameRequest(String title) {}
}
