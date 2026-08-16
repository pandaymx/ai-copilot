package xyz.ppmblszdp.ai.repository;

import jakarta.annotation.PostConstruct;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 红队安全对抗演练测试记录仓储（RedTeamRepository）。
 */
@Repository
public class RedTeamRepository {

    private static final Logger log = LoggerFactory.getLogger(RedTeamRepository.class);

    public record RedTeamRunRecord(
            String id,
            String userId,
            int totalTests,
            int blockedCount,
            int bypassCount,
            double hitRatePct,
            String detailsJson,
            long createdAt) {}

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<RedTeamRunRecord> rowMapper = (rs, rowNum) -> new RedTeamRunRecord(
            rs.getString("id"),
            rs.getString("user_id"),
            rs.getInt("total_tests"),
            rs.getInt("blocked_count"),
            rs.getInt("bypass_count"),
            rs.getDouble("hit_rate_pct"),
            rs.getString("details_json"),
            rs.getLong("created_at"));

    public RedTeamRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initSchema() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS redteam_runs (
                    id VARCHAR(64) PRIMARY KEY,
                    user_id VARCHAR(128) NOT NULL,
                    total_tests INT NOT NULL,
                    blocked_count INT NOT NULL,
                    bypass_count INT NOT NULL,
                    hit_rate_pct DOUBLE PRECISION NOT NULL,
                    details_json TEXT,
                    created_at BIGINT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_redteam_user ON redteam_runs(user_id, created_at DESC);
            """);
        } catch (Exception e) {
            log.warn("初始化 redteam_runs 表结构失败: {}", e.getMessage());
        }
    }

    public void save(RedTeamRunRecord record) {
        jdbcTemplate.update(
                """
            INSERT INTO redteam_runs (id, user_id, total_tests, blocked_count, bypass_count, hit_rate_pct, details_json, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
                record.id(),
                record.userId(),
                record.totalTests(),
                record.blockedCount(),
                record.bypassCount(),
                record.hitRatePct(),
                record.detailsJson(),
                record.createdAt());
    }

    public List<RedTeamRunRecord> listRuns(String userId, int limit) {
        return jdbcTemplate.query("""
            SELECT id, user_id, total_tests, blocked_count, bypass_count, hit_rate_pct, details_json, created_at
            FROM redteam_runs
            WHERE user_id = ?
            ORDER BY created_at DESC
            LIMIT ?
        """, rowMapper, userId, limit);
    }
}
