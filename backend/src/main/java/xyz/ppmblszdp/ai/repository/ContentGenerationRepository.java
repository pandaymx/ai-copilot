package xyz.ppmblszdp.ai.repository;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import xyz.ppmblszdp.ai.dto.ContentTemplateDto;

/**
 * 结构化内容生成历史记录持久化仓储（ContentGenerationRepository）。
 */
@Repository
public class ContentGenerationRepository {

    private static final Logger log = LoggerFactory.getLogger(ContentGenerationRepository.class);

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<ContentTemplateDto.ContentGenerationHistoryItem> rowMapper =
            (rs, rowNum) -> new ContentTemplateDto.ContentGenerationHistoryItem(
                    rs.getString("id"),
                    rs.getString("user_id"),
                    rs.getString("template_id"),
                    rs.getString("title"),
                    rs.getString("markdown_content"),
                    rs.getLong("created_at"));

    public ContentGenerationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initSchema() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS content_generations (
                    id VARCHAR(64) PRIMARY KEY,
                    user_id VARCHAR(128) NOT NULL,
                    template_id VARCHAR(64) NOT NULL,
                    title VARCHAR(256) NOT NULL,
                    inputs_json TEXT,
                    markdown_content TEXT NOT NULL,
                    created_at BIGINT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_content_gen_user ON content_generations(user_id, created_at DESC);
            """);
        } catch (Exception e) {
            log.warn("初始化 content_generations 表结构失败: {}", e.getMessage());
        }
    }

    public void save(
            String id,
            String userId,
            String templateId,
            String title,
            String inputsJson,
            String markdownContent,
            long createdAt) {
        jdbcTemplate.update("""
            INSERT INTO content_generations (id, user_id, template_id, title, inputs_json, markdown_content, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """, id, userId, templateId, title, inputsJson, markdownContent, createdAt);
    }

    public List<ContentTemplateDto.ContentGenerationHistoryItem> listByUserId(String userId, int limit) {
        return jdbcTemplate.query("""
            SELECT id, user_id, template_id, title, markdown_content, created_at
            FROM content_generations
            WHERE user_id = ?
            ORDER BY created_at DESC
            LIMIT ?
        """, rowMapper, userId, limit);
    }

    public Optional<ContentTemplateDto.ContentGenerationHistoryItem> findById(String userId, String id) {
        var list = jdbcTemplate.query("""
            SELECT id, user_id, template_id, title, markdown_content, created_at
            FROM content_generations
            WHERE user_id = ? AND id = ?
        """, rowMapper, userId, id);
        return list.stream().findFirst();
    }

    public void deleteById(String userId, String id) {
        jdbcTemplate.update("DELETE FROM content_generations WHERE user_id = ? AND id = ?", userId, id);
    }
}
