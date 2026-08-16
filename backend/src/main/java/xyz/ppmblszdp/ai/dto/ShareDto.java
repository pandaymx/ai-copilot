package xyz.ppmblszdp.ai.dto;

/**
 * 会话在线分享与快照数据传输对象（Share DTOs）。
 */
public class ShareDto {

    public record ShareCreateRequest(String title, String messagesJson, Long expireAt, String password) {}

    public record ShareResolveRequest(String password) {}

    public record ShareMetaDto(
            String token,
            String sessionId,
            String userId,
            String title,
            Long expireAt,
            boolean hasPassword,
            long viewCount,
            long createdAt) {}

    public record ShareSnapshotView(String token, String title, String messagesJson, long createdAt, long viewCount) {}
}
