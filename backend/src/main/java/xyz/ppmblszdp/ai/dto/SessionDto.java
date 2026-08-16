package xyz.ppmblszdp.ai.dto;

import java.util.List;

public record SessionDto(
        String id,
        String title,
        long updatedAt,
        boolean isDefaultTitle,
        String parentSessionId,
        String inheritedContextJson) {

    public SessionDto(String id, String title, long updatedAt, boolean isDefaultTitle) {
        this(id, title, updatedAt, isDefaultTitle, null, null);
    }

    public record SessionDetail(
            String id,
            String title,
            long updatedAt,
            boolean isDefaultTitle,
            String parentSessionId,
            String inheritedContextJson,
            List<MessageItem> messages) {

        public SessionDetail(
                String id, String title, long updatedAt, boolean isDefaultTitle, List<MessageItem> messages) {
            this(id, title, updatedAt, isDefaultTitle, null, null, messages);
        }
    }

    public record MessageItem(String id, String role, String content, List<MediaDto> media) {
        public MessageItem(String id, String role, String content) {
            this(id, role, content, null);
        }
    }

    public record Participant(String userId, String role) {}

    public record RenameRequest(String title) {}
}
