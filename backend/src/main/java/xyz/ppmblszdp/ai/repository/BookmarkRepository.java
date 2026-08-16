package xyz.ppmblszdp.ai.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import xyz.ppmblszdp.ai.dto.MessageMetaDto;

/**
 * 消息固定（Pin）、收藏（Bookmark）与标签持久化仓储（BookmarkRepository）。
 */
@Repository
public class BookmarkRepository {

    private static final Logger log = LoggerFactory.getLogger(BookmarkRepository.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<MessageMetaDto.MessageBookmarkDto> rowMapper = (rs, rowNum) -> {
        List<String> tagsList = new ArrayList<>();
        try {
            String tagsJson = rs.getString("tags");
            if (tagsJson != null && !tagsJson.isBlank()) {
                tagsList = MAPPER.readValue(tagsJson, new TypeReference<List<String>>() {});
            }
        } catch (Exception ignored) {
        }

        return new MessageMetaDto.MessageBookmarkDto(
                rs.getString("id"),
                rs.getString("user_id"),
                rs.getString("session_id"),
                rs.getString("message_id"),
                rs.getString("role"),
                rs.getString("content"),
                tagsList,
                rs.getBoolean("pinned"),
                rs.getBoolean("bookmarked"),
                rs.getLong("created_at"));
    };

    public BookmarkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initSchema() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS message_bookmarks (
                    id VARCHAR(64) PRIMARY KEY,
                    user_id VARCHAR(128) NOT NULL,
                    session_id VARCHAR(128) NOT NULL,
                    message_id VARCHAR(128) NOT NULL,
                    role VARCHAR(32) NOT NULL,
                    content TEXT NOT NULL,
                    tags TEXT,
                    pinned BOOLEAN NOT NULL DEFAULT false,
                    bookmarked BOOLEAN NOT NULL DEFAULT false,
                    created_at BIGINT NOT NULL
                );
                CREATE UNIQUE INDEX IF NOT EXISTS uidx_msg_bookmark ON message_bookmarks(user_id, message_id);
                CREATE INDEX IF NOT EXISTS idx_bookmark_user ON message_bookmarks(user_id, bookmarked);
                CREATE INDEX IF NOT EXISTS idx_bookmark_session_pinned ON message_bookmarks(session_id, pinned);
            """);
        } catch (Exception e) {
            log.warn("初始化 message_bookmarks 表结构失败: {}", e.getMessage());
        }
    }

    public MessageMetaDto.MessageStatusResponse toggleBookmark(
            String userId, String sessionId, String messageId, String role, String content, List<String> tags) {
        var existing = findByMessageId(userId, messageId);
        long now = System.currentTimeMillis();

        if (existing.isPresent()) {
            boolean nextState = !existing.get().bookmarked();
            jdbcTemplate.update("""
                UPDATE message_bookmarks
                SET bookmarked = ?, role = ?, content = ?
                WHERE user_id = ? AND message_id = ?
            """, nextState, role, content, userId, messageId);

            return new MessageMetaDto.MessageStatusResponse(
                    existing.get().pinned(), nextState, existing.get().tags());
        } else {
            String id = "bm_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            String tagsJson = "[]";
            try {
                if (tags != null) tagsJson = MAPPER.writeValueAsString(tags);
            } catch (Exception ignored) {
            }

            jdbcTemplate.update("""
                INSERT INTO message_bookmarks (id, user_id, session_id, message_id, role, content, tags, pinned, bookmarked, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, false, true, ?)
            """, id, userId, sessionId, messageId, role, content, tagsJson, now);

            return new MessageMetaDto.MessageStatusResponse(false, true, tags != null ? tags : List.of());
        }
    }

    public MessageMetaDto.MessageStatusResponse togglePin(
            String userId, String sessionId, String messageId, String role, String content) {
        var existing = findByMessageId(userId, messageId);
        long now = System.currentTimeMillis();

        if (existing.isPresent()) {
            boolean nextState = !existing.get().pinned();
            jdbcTemplate.update("""
                UPDATE message_bookmarks
                SET pinned = ?, role = ?, content = ?
                WHERE user_id = ? AND message_id = ?
            """, nextState, role, content, userId, messageId);

            return new MessageMetaDto.MessageStatusResponse(
                    nextState, existing.get().bookmarked(), existing.get().tags());
        } else {
            String id = "bm_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            jdbcTemplate.update("""
                INSERT INTO message_bookmarks (id, user_id, session_id, message_id, role, content, tags, pinned, bookmarked, created_at)
                VALUES (?, ?, ?, ?, ?, ?, '[]', true, false, ?)
            """, id, userId, sessionId, messageId, role, content, now);

            return new MessageMetaDto.MessageStatusResponse(true, false, List.of());
        }
    }

    public void updateTags(String userId, String messageId, List<String> tags) {
        String tagsJson = "[]";
        try {
            if (tags != null) tagsJson = MAPPER.writeValueAsString(tags);
        } catch (Exception ignored) {
        }

        jdbcTemplate.update(
                "UPDATE message_bookmarks SET tags = ? WHERE user_id = ? AND message_id = ?",
                tagsJson,
                userId,
                messageId);
    }

    public Optional<MessageMetaDto.MessageBookmarkDto> findByMessageId(String userId, String messageId) {
        var list = jdbcTemplate.query(
                "SELECT id, user_id, session_id, message_id, role, content, tags, pinned, bookmarked, created_at FROM message_bookmarks WHERE user_id = ? AND message_id = ?",
                rowMapper,
                userId,
                messageId);
        return list.stream().findFirst();
    }

    public List<MessageMetaDto.MessageBookmarkDto> listUserBookmarks(String userId) {
        return jdbcTemplate.query(
                "SELECT id, user_id, session_id, message_id, role, content, tags, pinned, bookmarked, created_at FROM message_bookmarks WHERE user_id = ? AND bookmarked = true ORDER BY created_at DESC",
                rowMapper,
                userId);
    }

    public List<MessageMetaDto.MessageBookmarkDto> listSessionPinned(String userId, String sessionId) {
        return jdbcTemplate.query(
                "SELECT id, user_id, session_id, message_id, role, content, tags, pinned, bookmarked, created_at FROM message_bookmarks WHERE user_id = ? AND session_id = ? AND pinned = true ORDER BY created_at ASC",
                rowMapper,
                userId,
                sessionId);
    }
}
