package xyz.ppmblszdp.ai.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import xyz.ppmblszdp.ai.tool.email.EmailDto;

/**
 * 邮件发送历史与记录持久化仓储（EmailRepository）。
 */
@Repository
public class EmailRepository {

    private static final Logger log = LoggerFactory.getLogger(EmailRepository.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<EmailDto.EmailHistoryItem> rowMapper = (rs, rowNum) -> {
        List<String> toList;
        try {
            toList = MAPPER.readValue(rs.getString("recipients"), new TypeReference<List<String>>() {});
        } catch (Exception e) {
            toList = Collections.singletonList(rs.getString("recipients"));
        }
        return new EmailDto.EmailHistoryItem(
                rs.getString("id"),
                rs.getString("user_id"),
                toList,
                rs.getString("subject"),
                rs.getString("body_snippet"),
                rs.getBoolean("is_html"),
                rs.getString("status"),
                rs.getLong("created_at"));
    };

    public EmailRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initSchema() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS email_history (
                    id VARCHAR(64) PRIMARY KEY,
                    user_id VARCHAR(128) NOT NULL,
                    recipients TEXT NOT NULL,
                    subject VARCHAR(256),
                    body_snippet TEXT,
                    is_html BOOLEAN NOT NULL DEFAULT false,
                    status VARCHAR(32) NOT NULL,
                    created_at BIGINT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_email_user_created ON email_history(user_id, created_at DESC);
            """);
        } catch (Exception e) {
            log.warn("初始化 email_history 表结构失败: {}", e.getMessage());
        }
    }

    public void save(EmailDto.EmailHistoryItem item) {
        String recJson;
        try {
            recJson = MAPPER.writeValueAsString(item.to());
        } catch (Exception e) {
            recJson = "[]";
        }
        jdbcTemplate.update(
                """
            INSERT INTO email_history (id, user_id, recipients, subject, body_snippet, is_html, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
                item.id(),
                item.userId(),
                recJson,
                item.subject(),
                item.bodySnippet(),
                item.isHtml(),
                item.status(),
                item.createdAt());
    }

    public List<EmailDto.EmailHistoryItem> findByUserId(String userId, int limit) {
        return jdbcTemplate.query(
                "SELECT id, user_id, recipients, subject, body_snippet, is_html, status, created_at FROM email_history WHERE user_id = ? ORDER BY created_at DESC LIMIT ?",
                rowMapper,
                userId,
                limit > 0 ? limit : 20);
    }
}
