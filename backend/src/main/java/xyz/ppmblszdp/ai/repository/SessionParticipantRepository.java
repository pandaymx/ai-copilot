package xyz.ppmblszdp.ai.repository;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 共享会话参与者关联表（session_participant）。
 *
 * <p>在现有严格多租户隔离（chat_session.user_id）之上叠加协作层：
 * 会话所有者（OWNER）显式邀请协作者后，协作者即可通过本表获得对该会话的访问权。
 * 表结构与 chat_session 保持一致（无 ORM，纯 JDBC）。
 */
@Repository
public class SessionParticipantRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<SessionParticipant> ROW_MAPPER = (rs, rowNum) -> new SessionParticipant(
            rs.getString("session_id"), rs.getString("user_id"), SessionParticipant.Role.valueOf(rs.getString("role")));

    public SessionParticipantRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 建表（幂等，仅首次启动时执行）。 */
    public void createTableIfNotExists() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS session_participant (
                    session_id VARCHAR(255) NOT NULL,
                    user_id VARCHAR(255) NOT NULL,
                    role VARCHAR(20) NOT NULL,
                    created_at BIGINT NOT NULL,
                    PRIMARY KEY (session_id, user_id)
                )
                """);
    }

    /** 新增/更新参与者角色。 */
    public Mono<Void> upsert(String sessionId, String userId, SessionParticipant.Role role) {
        return Mono.fromRunnable(
                () -> jdbcTemplate.update("""
                                INSERT INTO session_participant
                                    (session_id, user_id, role, created_at)
                                VALUES (?, ?, ?, ?)
                                ON CONFLICT (session_id, user_id)
                                DO UPDATE SET role = EXCLUDED.role
                                """, sessionId, userId, role.name(), System.currentTimeMillis()));
    }

    /** 移除参与者（不移除所有者自身）。 */
    public Mono<Void> remove(String sessionId, String userId) {
        return Mono.fromRunnable(() -> jdbcTemplate.update(
                "DELETE FROM session_participant WHERE session_id = ? AND user_id = ? AND role <> 'OWNER'",
                sessionId,
                userId));
    }

    /** 查询某用户在会话中的角色；非参与者返回 null。 */
    public Mono<SessionParticipant.Role> roleOf(String sessionId, String userId) {
        return Mono.fromCallable(() -> {
            List<String> roles = jdbcTemplate.query(
                    "SELECT role FROM session_participant WHERE session_id = ? AND user_id = ?",
                    (rs, rn) -> rs.getString("role"),
                    sessionId,
                    userId);
            return roles.isEmpty() ? null : SessionParticipant.Role.valueOf(roles.get(0));
        });
    }

    /** 列出会话全部参与者（含所有者）。 */
    public Mono<List<SessionParticipant>> listBySession(String sessionId) {
        return Mono.fromCallable(() -> jdbcTemplate.query(
                "SELECT session_id, user_id, role FROM session_participant WHERE session_id = ? ORDER BY created_at ASC",
                ROW_MAPPER,
                sessionId));
    }

    /** 将所有者也写入参与者表（创建会话或首次协作时调用）。 */
    public Mono<Void> ensureOwner(String sessionId, String ownerId) {
        return Mono.fromRunnable(() -> jdbcTemplate.update("""
                                INSERT INTO session_participant
                                    (session_id, user_id, role, created_at)
                                VALUES (?, ?, 'OWNER', ?)
                                ON CONFLICT (session_id, user_id) DO NOTHING
                                """, sessionId, ownerId, System.currentTimeMillis()));
    }
}
