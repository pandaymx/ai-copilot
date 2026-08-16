package xyz.ppmblszdp.ai.repository;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 会话快照与在线分享持久化层（ShareRepository）。
 */
@Repository
public class ShareRepository {

    private static final Logger log = LoggerFactory.getLogger(ShareRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public record ShareEntity(
            String token,
            String sessionId,
            String userId,
            String title,
            String snapshotJson,
            Long expireAt,
            String passwordHash,
            long viewCount,
            long createdAt) {}

    private final RowMapper<ShareEntity> rowMapper = (rs, rowNum) -> new ShareEntity(
            rs.getString("token"),
            rs.getString("session_id"),
            rs.getString("user_id"),
            rs.getString("title"),
            rs.getString("snapshot_json"),
            rs.getObject("expire_at") != null ? rs.getLong("expire_at") : null,
            rs.getString("password_hash"),
            rs.getLong("view_count"),
            rs.getLong("created_at"));

    public ShareRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initSchema() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS share_snapshots (
                    token VARCHAR(64) PRIMARY KEY,
                    session_id VARCHAR(128) NOT NULL,
                    user_id VARCHAR(128) NOT NULL,
                    title VARCHAR(256),
                    snapshot_json TEXT NOT NULL,
                    expire_at BIGINT,
                    password_hash VARCHAR(256),
                    view_count BIGINT NOT NULL DEFAULT 0,
                    created_at BIGINT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_share_snapshots_session ON share_snapshots(session_id);
                CREATE INDEX IF NOT EXISTS idx_share_snapshots_user ON share_snapshots(user_id);
            """);
        } catch (Exception e) {
            log.warn("初始化 share_snapshots 表结构失败: {}", e.getMessage());
        }
    }

    public void insert(ShareEntity entity) {
        jdbcTemplate.update(
                """
            INSERT INTO share_snapshots (token, session_id, user_id, title, snapshot_json, expire_at, password_hash, view_count, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
                entity.token(),
                entity.sessionId(),
                entity.userId(),
                entity.title(),
                entity.snapshotJson(),
                entity.expireAt(),
                entity.passwordHash(),
                entity.viewCount(),
                entity.createdAt());
    }

    public Optional<ShareEntity> findByToken(String token) {
        List<ShareEntity> list = jdbcTemplate.query(
                "SELECT token, session_id, user_id, title, snapshot_json, expire_at, password_hash, view_count, created_at FROM share_snapshots WHERE token = ?",
                rowMapper,
                token);
        return list.stream().findFirst();
    }

    public void incrementViewCount(String token) {
        jdbcTemplate.update("UPDATE share_snapshots SET view_count = view_count + 1 WHERE token = ?", token);
    }

    public int deleteByTokenAndUserId(String token, String userId) {
        return jdbcTemplate.update("DELETE FROM share_snapshots WHERE token = ? AND user_id = ?", token, userId);
    }

    public List<ShareEntity> listBySessionId(String sessionId, String userId) {
        return jdbcTemplate.query(
                "SELECT token, session_id, user_id, title, snapshot_json, expire_at, password_hash, view_count, created_at FROM share_snapshots WHERE session_id = ? AND user_id = ? ORDER BY created_at DESC",
                rowMapper,
                sessionId,
                userId);
    }
}
