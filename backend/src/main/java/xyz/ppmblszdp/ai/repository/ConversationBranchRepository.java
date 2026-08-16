package xyz.ppmblszdp.ai.repository;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 对话分支与分支消息持久化仓储（PostgreSQL + JdbcTemplate）。
 */
@Repository
public class ConversationBranchRepository {

    private static final Logger log = LoggerFactory.getLogger(ConversationBranchRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public ConversationBranchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record BranchEntity(
            String id,
            String sessionId,
            String userId,
            String branchLabel,
            String parentBranchId,
            String forkFromMessageId,
            long createdAt,
            long updatedAt) {}

    public record BranchMessageEntity(
            String id,
            String sessionId,
            String userId,
            String branchId,
            String parentId,
            String role,
            String content,
            long createdAt) {}

    private final RowMapper<BranchEntity> branchRowMapper = (rs, rowNum) -> new BranchEntity(
            rs.getString("id"),
            rs.getString("session_id"),
            rs.getString("user_id"),
            rs.getString("branch_label"),
            rs.getString("parent_branch_id"),
            rs.getString("fork_from_message_id"),
            rs.getLong("created_at"),
            rs.getLong("updated_at"));

    private final RowMapper<BranchMessageEntity> messageRowMapper = (rs, rowNum) -> new BranchMessageEntity(
            rs.getString("id"),
            rs.getString("session_id"),
            rs.getString("user_id"),
            rs.getString("branch_id"),
            rs.getString("parent_id"),
            rs.getString("role"),
            rs.getString("content"),
            rs.getLong("created_at"));

    @PostConstruct
    public void initSchema() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS conversation_branches (
                        id VARCHAR(128) PRIMARY KEY,
                        session_id VARCHAR(128) NOT NULL,
                        user_id VARCHAR(128) NOT NULL,
                        branch_label VARCHAR(128) NOT NULL,
                        parent_branch_id VARCHAR(128),
                        fork_from_message_id VARCHAR(128),
                        created_at BIGINT NOT NULL,
                        updated_at BIGINT NOT NULL
                    );
                    """);
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_cb_session_user ON conversation_branches(session_id, user_id);");

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS conversation_branch_messages (
                        id VARCHAR(128) PRIMARY KEY,
                        session_id VARCHAR(128) NOT NULL,
                        user_id VARCHAR(128) NOT NULL,
                        branch_id VARCHAR(128) NOT NULL,
                        parent_id VARCHAR(128),
                        role VARCHAR(32) NOT NULL,
                        content TEXT NOT NULL,
                        created_at BIGINT NOT NULL
                    );
                    """);
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_cbm_session_branch ON conversation_branch_messages(session_id, branch_id);");
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_cbm_parent ON conversation_branch_messages(parent_id);");
            log.info("PostgreSQL 对话分支表 'conversation_branches' 与 'conversation_branch_messages' 初始化成功");
        } catch (Exception e) {
            log.error("初始化对话分支数据表失败", e);
        }
    }

    public BranchEntity createBranch(
            String sessionId, String userId, String label, String parentBranchId, String forkFromMessageId) {
        String id = "br_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long now = System.currentTimeMillis();
        BranchEntity entity =
                new BranchEntity(id, sessionId, userId, label, parentBranchId, forkFromMessageId, now, now);
        jdbcTemplate.update(
                """
                INSERT INTO conversation_branches (id, session_id, user_id, branch_label, parent_branch_id, fork_from_message_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                entity.id(),
                entity.sessionId(),
                entity.userId(),
                entity.branchLabel(),
                entity.parentBranchId(),
                entity.forkFromMessageId(),
                entity.createdAt(),
                entity.updatedAt());
        return entity;
    }

    public Optional<BranchEntity> findBranchById(String branchId, String userId) {
        try {
            BranchEntity entity = jdbcTemplate.queryForObject(
                    "SELECT * FROM conversation_branches WHERE id = ? AND user_id = ?",
                    branchRowMapper,
                    branchId,
                    userId);
            return Optional.ofNullable(entity);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<BranchEntity> findBranchesBySession(String sessionId, String userId) {
        return jdbcTemplate.query(
                "SELECT * FROM conversation_branches WHERE session_id = ? AND user_id = ? ORDER BY created_at ASC",
                branchRowMapper,
                sessionId,
                userId);
    }

    public BranchMessageEntity insertMessage(
            String sessionId, String userId, String branchId, String parentId, String role, String content) {
        String id = "msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long now = System.currentTimeMillis();
        BranchMessageEntity entity =
                new BranchMessageEntity(id, sessionId, userId, branchId, parentId, role, content, now);
        jdbcTemplate.update(
                """
                INSERT INTO conversation_branch_messages (id, session_id, user_id, branch_id, parent_id, role, content, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                entity.id(),
                entity.sessionId(),
                entity.userId(),
                entity.branchId(),
                entity.parentId(),
                entity.role(),
                entity.content(),
                entity.createdAt());
        return entity;
    }

    public List<BranchMessageEntity> findMessagesByBranch(String sessionId, String branchId, String userId) {
        return jdbcTemplate.query(
                "SELECT * FROM conversation_branch_messages WHERE session_id = ? AND branch_id = ? AND user_id = ? ORDER BY created_at ASC",
                messageRowMapper,
                sessionId,
                branchId,
                userId);
    }

    public List<BranchMessageEntity> findAllMessagesBySession(String sessionId, String userId) {
        return jdbcTemplate.query(
                "SELECT * FROM conversation_branch_messages WHERE session_id = ? AND user_id = ? ORDER BY created_at ASC",
                messageRowMapper,
                sessionId,
                userId);
    }

    public Optional<BranchMessageEntity> findMessageById(String messageId, String userId) {
        try {
            BranchMessageEntity entity = jdbcTemplate.queryForObject(
                    "SELECT * FROM conversation_branch_messages WHERE id = ? AND user_id = ?",
                    messageRowMapper,
                    messageId,
                    userId);
            return Optional.ofNullable(entity);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public int countMessagesInBranch(String sessionId, String branchId, String userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM conversation_branch_messages WHERE session_id = ? AND branch_id = ? AND user_id = ?",
                Integer.class,
                sessionId,
                branchId,
                userId);
        return count != null ? count : 0;
    }
}
