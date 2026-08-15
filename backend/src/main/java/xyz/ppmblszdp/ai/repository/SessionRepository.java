package xyz.ppmblszdp.ai.repository;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import xyz.ppmblszdp.ai.dto.SessionDto;

/**
 * 会话元数据 Repository（基于 JdbcTemplate 与 PostgreSQL 落盘存储）。
 *
 * <p>会话按 {@code user_id} 做多租户隔离：所有读写均绑定用户身份，跨用户的
 * 会话不会被返回或删除。存量行可能 user_id 为 NULL（迁移前），属历史数据，
 * 对认证用户不可见，可后续按需回填。
 */
@Repository
public class SessionRepository {

    private static final Logger log = LoggerFactory.getLogger(SessionRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public SessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<SessionDto> ROW_MAPPER = (rs, rowNum) -> new SessionDto(
            rs.getString("id"),
            rs.getString("title"),
            rs.getLong("updated_at"),
            rs.getBoolean("is_default_title"),
            rs.getString("parent_session_id"),
            rs.getString("inherited_context_json"));

    @PostConstruct
    public void initSchema() {
        try {
            jdbcTemplate.execute("""
					CREATE TABLE IF NOT EXISTS chat_session (
						id VARCHAR(128) PRIMARY KEY,
						title VARCHAR(255) NOT NULL,
						updated_at BIGINT NOT NULL,
						is_default_title BOOLEAN DEFAULT TRUE,
						user_id VARCHAR(128),
						parent_session_id VARCHAR(128),
						inherited_context_json TEXT
					);
					""");
            // 兼容存量表：缺列则补列
            jdbcTemplate.execute("ALTER TABLE chat_session ADD COLUMN IF NOT EXISTS user_id VARCHAR(128);");
            jdbcTemplate.execute("ALTER TABLE chat_session ADD COLUMN IF NOT EXISTS parent_session_id VARCHAR(128);");
            jdbcTemplate.execute("ALTER TABLE chat_session ADD COLUMN IF NOT EXISTS inherited_context_json TEXT;");
            // 复合索引覆盖原单列索引，按 (user_id, updated_at DESC) 查询会话列表
            jdbcTemplate.execute("DROP INDEX IF EXISTS idx_chat_session_updated;");
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_chat_session_user_updated ON chat_session(user_id, updated_at DESC);");
            log.info("PostgreSQL 会话元数据表 'chat_session' 初始化/校验成功");
        } catch (Exception ex) {
            log.error("初始化 PostgreSQL 会话元数据表失败: {}", ex.getMessage(), ex);
        }
    }

    /** 新增或全量覆盖写入会话元数据（绑定用户） */
    public void upsertSession(String id, String userId, String title, long updatedAt, boolean isDefaultTitle) {
        String sql = """
				INSERT INTO chat_session (id, user_id, title, updated_at, is_default_title)
				VALUES (?, ?, ?, ?, ?)
				ON CONFLICT (id) DO UPDATE SET
					user_id = EXCLUDED.user_id,
					title = EXCLUDED.title,
					updated_at = EXCLUDED.updated_at,
					is_default_title = EXCLUDED.is_default_title;
				""";
        jdbcTemplate.update(sql, id, userId, title, updatedAt, isDefaultTitle);
    }

    /** 写入带上下文继承关联的会话元数据 */
    public void upsertSessionWithInheritance(
            String id,
            String userId,
            String title,
            long updatedAt,
            boolean isDefaultTitle,
            String parentSessionId,
            String inheritedContextJson) {
        String sql = """
				INSERT INTO chat_session (id, user_id, title, updated_at, is_default_title, parent_session_id, inherited_context_json)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (id) DO UPDATE SET
					user_id = EXCLUDED.user_id,
					title = EXCLUDED.title,
					updated_at = EXCLUDED.updated_at,
					is_default_title = EXCLUDED.is_default_title,
					parent_session_id = EXCLUDED.parent_session_id,
					inherited_context_json = EXCLUDED.inherited_context_json;
				""";
        jdbcTemplate.update(sql, id, userId, title, updatedAt, isDefaultTitle, parentSessionId, inheritedContextJson);
    }

    /** 仅更新会话时间戳（已有会话发送消息时，绑定用户） */
    public void touchSession(String id, String userId, String fallbackTitle, long updatedAt) {
        // title 列 NOT NULL：fallbackTitle 为 null/blank 时回落默认标题，避免会话元数据落库失败
        String title = (fallbackTitle == null || fallbackTitle.isBlank()) ? "新会话" : fallbackTitle;
        String sql = """
				INSERT INTO chat_session (id, user_id, title, updated_at, is_default_title)
				VALUES (?, ?, ?, ?, TRUE)
				ON CONFLICT (id) DO UPDATE SET
					user_id = EXCLUDED.user_id,
					updated_at = EXCLUDED.updated_at;
				""";
        jdbcTemplate.update(sql, id, userId, title, updatedAt);
    }

    /** 更新会话标题（按用户隔离） */
    public void updateTitle(String id, String userId, String newTitle, boolean isDefaultTitle) {
        String sql =
                "UPDATE chat_session SET title = ?, is_default_title = ?, updated_at = ? WHERE id = ? AND user_id = ?";
        jdbcTemplate.update(sql, newTitle, isDefaultTitle, System.currentTimeMillis(), id, userId);
    }

    /** 获取指定用户的所有会话元数据（按更新时间倒序） */
    public List<SessionDto> findAllByUserId(String userId) {
        String sql =
                "SELECT id, title, updated_at, is_default_title, parent_session_id, inherited_context_json FROM chat_session WHERE user_id = ? ORDER BY updated_at DESC";
        return jdbcTemplate.query(sql, ROW_MAPPER, userId);
    }

    /** 根据 ID 与用户查询单个会话元数据（归属校验） */
    public Optional<SessionDto> findByIdAndUserId(String id, String userId) {
        String sql =
                "SELECT id, title, updated_at, is_default_title, parent_session_id, inherited_context_json FROM chat_session WHERE id = ? AND user_id = ?";
        List<SessionDto> list = jdbcTemplate.query(sql, ROW_MAPPER, id, userId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /** 删除指定用户的会话元数据，返回受影响行数（0 表示会话不存在或不属于该用户） */
    public int deleteByIdAndUserId(String id, String userId) {
        String sql = "DELETE FROM chat_session WHERE id = ? AND user_id = ?";
        return jdbcTemplate.update(sql, id, userId);
    }
}
