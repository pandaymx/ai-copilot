package xyz.ppmblszdp.ai.repository;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import xyz.ppmblszdp.ai.dto.ChatFeedbackRequest;

/**
 * 消息评价反馈 Repository（基于 JdbcTemplate 与 PostgreSQL 落盘存储）。
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
            log.info("PostgreSQL 用户反馈元数据表 'chat_feedback' 初始化/校验成功");
        } catch (Exception ex) {
            log.error("初始化 PostgreSQL 用户反馈表失败: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 保存用户对消息的点赞/点踩反馈记录。userId 来自服务端受信任身份，不再信任请求体。
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
				INSERT INTO chat_feedback (conversation_id, message_id, rating, comment, user_id, created_at)
				VALUES (?, ?, ?, ?, ?, ?);
				""";
        jdbcTemplate.update(
                sql,
                request.conversationId(),
                request.messageId(),
                request.rating().toUpperCase(),
                request.comment(),
                userId,
                System.currentTimeMillis());
        log.info(
                "已保存用户反馈记录 [user={}, cid={}, msgId={}, rating={}]",
                userId,
                request.conversationId(),
                request.messageId(),
                request.rating());
    }
}
