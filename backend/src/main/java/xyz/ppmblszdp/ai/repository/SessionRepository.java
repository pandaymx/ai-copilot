package xyz.ppmblszdp.ai.repository;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import xyz.ppmblszdp.ai.dto.SessionDto;

import java.util.List;
import java.util.Optional;

/**
 * 会话元数据 Repository（基于 JdbcTemplate 与 PostgreSQL 落盘存储）。
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
			rs.getBoolean("is_default_title")
	);

	@PostConstruct
	public void initSchema() {
		try {
			jdbcTemplate.execute("""
					CREATE TABLE IF NOT EXISTS chat_session (
						id VARCHAR(128) PRIMARY KEY,
						title VARCHAR(255) NOT NULL,
						updated_at BIGINT NOT NULL,
						is_default_title BOOLEAN DEFAULT TRUE
					);
					""");
			jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_chat_session_updated ON chat_session(updated_at DESC);");
			log.info("PostgreSQL 会话元数据表 'chat_session' 初始化/校验成功");
		} catch (Exception ex) {
			log.error("初始化 PostgreSQL 会话元数据表失败: {}", ex.getMessage(), ex);
		}
	}

	/** 新增或全量覆盖写入会话元数据 */
	public void upsertSession(String id, String title, long updatedAt, boolean isDefaultTitle) {
		String sql = """
				INSERT INTO chat_session (id, title, updated_at, is_default_title)
				VALUES (?, ?, ?, ?)
				ON CONFLICT (id) DO UPDATE SET
					title = EXCLUDED.title,
					updated_at = EXCLUDED.updated_at,
					is_default_title = EXCLUDED.is_default_title;
				""";
		jdbcTemplate.update(sql, id, title, updatedAt, isDefaultTitle);
	}

	/** 仅更新会话时间戳（已有会话发送消息时） */
	public void touchSession(String id, String fallbackTitle, long updatedAt) {
		String sql = """
				INSERT INTO chat_session (id, title, updated_at, is_default_title)
				VALUES (?, ?, ?, TRUE)
				ON CONFLICT (id) DO UPDATE SET
					updated_at = EXCLUDED.updated_at;
				""";
		jdbcTemplate.update(sql, id, fallbackTitle, updatedAt);
	}

	/** 更新会话标题 */
	public void updateTitle(String id, String newTitle, boolean isDefaultTitle) {
		String sql = "UPDATE chat_session SET title = ?, is_default_title = ?, updated_at = ? WHERE id = ?";
		jdbcTemplate.update(sql, newTitle, isDefaultTitle, System.currentTimeMillis(), id);
	}

	/** 获取所有会话元数据（按更新时间倒序） */
	public List<SessionDto> findAll() {
		String sql = "SELECT id, title, updated_at, is_default_title FROM chat_session ORDER BY updated_at DESC";
		return jdbcTemplate.query(sql, ROW_MAPPER);
	}

	/** 根据 ID 查询单个会话元数据 */
	public Optional<SessionDto> findById(String id) {
		String sql = "SELECT id, title, updated_at, is_default_title FROM chat_session WHERE id = ?";
		List<SessionDto> list = jdbcTemplate.query(sql, ROW_MAPPER, id);
		return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
	}

	/** 删除会话元数据 */
	public void deleteById(String id) {
		String sql = "DELETE FROM chat_session WHERE id = ?";
		jdbcTemplate.update(sql, id);
	}
}
