package xyz.ppmblszdp.ai.repository;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import xyz.ppmblszdp.ai.dto.ChatFeedbackRequest;

/**
 * 消息评价反馈 Repository（基于 JdbcTemplate 与 PostgreSQL 落盘存储）。
 *
 * <p>表结构支持完整的反馈质量闭环字段：
 * <ul>
 *   <li>model_id, intent — 用于按模型 / 意图聚合满意度</li>
 *   <li>user_prompt TEXT, assistant_reply TEXT — 无损存储（TOAST 自动压缩）</li>
 *   <li>reflection_analysis TEXT, reflection_category — 异步 ReflectionEngine 分析结果</li>
 *   <li>reflection_done BOOLEAN — 幂等标志，防止重复触发</li>
 * </ul>
 */
@Repository
public class FeedbackRepository {

    private static final Logger log = LoggerFactory.getLogger(FeedbackRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public FeedbackRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initSchema() {
        try {
            // 创建基础表（幂等）
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS chat_feedback (
                        id BIGSERIAL PRIMARY KEY,
                        conversation_id VARCHAR(128),
                        message_id VARCHAR(128),
                        rating VARCHAR(32) NOT NULL,
                        comment TEXT,
                        user_id VARCHAR(128) NOT NULL,
                        created_at BIGINT NOT NULL
                    );
                    """);
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_chat_feedback_cid ON chat_feedback(conversation_id);");

            // 安全扩展新列（幂等，ADD COLUMN IF NOT EXISTS — PostgreSQL 9.6+）
            String[] alterColumns = {
                "ALTER TABLE chat_feedback ADD COLUMN IF NOT EXISTS model_id VARCHAR(128)",
                "ALTER TABLE chat_feedback ADD COLUMN IF NOT EXISTS intent VARCHAR(64)",
                "ALTER TABLE chat_feedback ADD COLUMN IF NOT EXISTS user_prompt TEXT",
                "ALTER TABLE chat_feedback ADD COLUMN IF NOT EXISTS assistant_reply TEXT",
                "ALTER TABLE chat_feedback ADD COLUMN IF NOT EXISTS reflection_analysis TEXT",
                "ALTER TABLE chat_feedback ADD COLUMN IF NOT EXISTS reflection_category VARCHAR(64)",
                "ALTER TABLE chat_feedback ADD COLUMN IF NOT EXISTS reflection_done BOOLEAN DEFAULT FALSE"
            };
            for (String sql : alterColumns) {
                try {
                    jdbcTemplate.execute(sql);
                } catch (Exception ex) {
                    log.debug("跳过已存在列扩展（幂等）: {}", ex.getMessage());
                }
            }

            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_chat_feedback_rating ON chat_feedback(rating);");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_chat_feedback_model ON chat_feedback(model_id);");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_chat_feedback_intent ON chat_feedback(intent);");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_chat_feedback_mid ON chat_feedback(message_id);");

            log.info("PostgreSQL 用户反馈元数据表 'chat_feedback' 初始化/校验成功");
        } catch (Exception ex) {
            log.error("初始化 PostgreSQL 用户反馈表失败: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 保存用户对消息的点赞/点踩反馈记录（含扩展字段）。
     * userId 来自服务端受信任身份，不再信任请求体。
     */
    public void saveFeedback(String userId, ChatFeedbackRequest request) {
        if (request == null || request.rating() == null || request.rating().isBlank()) {
            log.warn("跳过无效的反馈保存请求: {}", request);
            return;
        }
        if (userId == null || userId.isBlank()) {
            log.warn("跳过缺少用户身份的反馈保存请求: {}", request);
            return;
        }
        String sql = """
                INSERT INTO chat_feedback
                    (conversation_id, message_id, rating, comment, user_id, created_at,
                     model_id, intent, user_prompt, assistant_reply)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                """;
        jdbcTemplate.update(
                sql,
                request.conversationId(),
                request.messageId(),
                request.rating().toUpperCase(),
                request.comment(),
                userId,
                System.currentTimeMillis(),
                request.modelId(),
                request.intent(),
                request.userPrompt(),
                request.assistantReply());
        log.info(
                "已保存用户反馈记录 [user={}, cid={}, msgId={}, rating={}, model={}, intent={}]",
                userId,
                request.conversationId(),
                request.messageId(),
                request.rating(),
                request.modelId(),
                request.intent());
    }

    /**
     * 将 ReflectionEngine 异步分析结果持久化回 chat_feedback 表。
     * 以 messageId 为 key，实现幂等更新（reflection_done = TRUE 后不再覆盖）。
     */
    public void updateReflection(String messageId, String analysis, String category) {
        if (messageId == null || messageId.isBlank()) return;
        String sql = """
                UPDATE chat_feedback
                SET reflection_analysis = ?,
                    reflection_category = ?,
                    reflection_done = TRUE
                WHERE message_id = ?
                  AND (reflection_done IS NULL OR reflection_done = FALSE)
                """;
        int rows = jdbcTemplate.update(sql, analysis, category, messageId);
        log.debug("反思结果写入完成 [messageId={}, rows={}]", messageId, rows);
    }

    /**
     * 查询 messageId 是否已完成 ReflectionEngine 分析（幂等守卫）。
     */
    public boolean isReflectionDone(String messageId) {
        if (messageId == null || messageId.isBlank()) return false;
        String sql = """
                SELECT COUNT(*) FROM chat_feedback
                WHERE message_id = ? AND reflection_done = TRUE
                """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, messageId);
        return count != null && count > 0;
    }

    // ====================== 聚合查询 ======================

    /**
     * 按日聚合最近 N 天的点赞/点踩趋势数据。
     * 返回 List，每条为 {date: String, thumbsUp: int, thumbsDown: int}
     */
    public List<Map<String, Object>> queryDailyTrend(int days) {
        String sql = """
                SELECT
                    TO_CHAR(TO_TIMESTAMP(created_at / 1000), 'YYYY-MM-DD') AS date,
                    COUNT(CASE WHEN rating = 'THUMBS_UP'   THEN 1 END) AS thumbs_up,
                    COUNT(CASE WHEN rating = 'THUMBS_DOWN' THEN 1 END) AS thumbs_down
                FROM chat_feedback
                WHERE created_at >= EXTRACT(EPOCH FROM NOW() - INTERVAL '1 day' * ?) * 1000
                GROUP BY date
                ORDER BY date ASC
                """;
        try {
            return jdbcTemplate.queryForList(sql, days);
        } catch (Exception e) {
            log.warn("queryDailyTrend 查询失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 按模型聚合满意度（仅含 model_id 非空的记录）。
     */
    public List<Map<String, Object>> queryModelSatisfaction() {
        String sql = """
                SELECT
                    model_id,
                    COUNT(CASE WHEN rating = 'THUMBS_UP'   THEN 1 END) AS thumbs_up,
                    COUNT(CASE WHEN rating = 'THUMBS_DOWN' THEN 1 END) AS thumbs_down,
                    COUNT(*) AS total
                FROM chat_feedback
                WHERE model_id IS NOT NULL
                GROUP BY model_id
                ORDER BY thumbs_up DESC
                """;
        try {
            return jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            log.warn("queryModelSatisfaction 查询失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 按意图聚合满意度（仅含 intent 非空的记录）。
     */
    public List<Map<String, Object>> queryIntentSatisfaction() {
        String sql = """
                SELECT
                    intent,
                    COUNT(CASE WHEN rating = 'THUMBS_UP'   THEN 1 END) AS thumbs_up,
                    COUNT(CASE WHEN rating = 'THUMBS_DOWN' THEN 1 END) AS thumbs_down,
                    COUNT(*) AS total
                FROM chat_feedback
                WHERE intent IS NOT NULL
                GROUP BY intent
                ORDER BY thumbs_down DESC
                """;
        try {
            return jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            log.warn("queryIntentSatisfaction 查询失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 查询最近 N 条点踩记录（含 Reflection 分析）作为低分案例展示。
     */
    public List<Map<String, Object>> queryLowScoreCases(int limit) {
        String sql = """
                SELECT
                    message_id, conversation_id, user_id, model_id, intent,
                    user_prompt, comment, reflection_analysis, reflection_category, created_at
                FROM chat_feedback
                WHERE rating = 'THUMBS_DOWN'
                ORDER BY created_at DESC
                LIMIT ?
                """;
        try {
            return jdbcTemplate.queryForList(sql, limit);
        } catch (Exception e) {
            log.warn("queryLowScoreCases 查询失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
