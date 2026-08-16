package xyz.ppmblszdp.ai.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import xyz.ppmblszdp.ai.dto.InsightSummaryDto;

/**
 * 历史对话洞察聚合结果持久化仓储（InsightRepository）。
 */
@Repository
public class InsightRepository {

    private static final Logger log = LoggerFactory.getLogger(InsightRepository.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;

    public InsightRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initSchema() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS conversation_insights (
                    user_id VARCHAR(128) PRIMARY KEY,
                    summary_json TEXT NOT NULL,
                    generated_at BIGINT NOT NULL
                );
            """);
        } catch (Exception e) {
            log.warn("初始化 conversation_insights 表结构失败: {}", e.getMessage());
        }
    }

    public void saveInsight(String userId, InsightSummaryDto summary) {
        try {
            String json = MAPPER.writeValueAsString(summary);
            jdbcTemplate.update("""
                INSERT INTO conversation_insights (user_id, summary_json, generated_at)
                VALUES (?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET
                    summary_json = EXCLUDED.summary_json,
                    generated_at = EXCLUDED.generated_at
            """, userId, json, summary.generatedAt());
        } catch (Exception e) {
            log.error("保存洞察聚合数据失败: user={}, err={}", userId, e.getMessage());
        }
    }

    public Optional<InsightSummaryDto> findByUserId(String userId) {
        try {
            var list = jdbcTemplate.query(
                    "SELECT summary_json FROM conversation_insights WHERE user_id = ?",
                    (rs, rowNum) -> rs.getString("summary_json"),
                    userId);
            if (list.isEmpty()) {
                return Optional.empty();
            }
            return Optional.ofNullable(MAPPER.readValue(list.get(0), InsightSummaryDto.class));
        } catch (Exception e) {
            log.warn("读取洞察聚合数据失败: user={}, err={}", userId, e.getMessage());
            return Optional.empty();
        }
    }
}
