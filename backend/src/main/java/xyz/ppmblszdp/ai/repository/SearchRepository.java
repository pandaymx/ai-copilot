package xyz.ppmblszdp.ai.repository;

import jakarta.annotation.PostConstruct;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import xyz.ppmblszdp.ai.dto.SearchResponse.SearchResultItem;

/**
 * 聊天历史全文检索 Repository（基于 JdbcTemplate + PostgreSQL 全文检索能力）。
 *
 * <p>在 Spring AI 自动创建的 {@code spring_ai_chat_memory} 表之上，幂等地初始化：
 * <ul>
 *   <li>{@code pg_trgm} 扩展 —— 提供三元组（trigram）模糊匹配与子串检索；</li>
 *   <li>{@code content_tsv} 生成列（STORED）—— 由 PG 在写入/更新时自动维护
 *       {@code to_tsvector('simple', content)}，对 INSERT 透明，不入侵 Spring AI 写入路径；</li>
 *   <li>两类 GIN 索引 —— {@code content_tsv} 精准词项检索 + {@code content} 的
 *       {@code gin_trgm_ops} 子串模糊兜底，均为索引扫描（O(log n)）。</li>
 * </ul>
 *
 * <p>检索以「tsvector 匹配 OR pg_trgm 子串匹配」组合条件查询，并用 {@code ts_headline}
 * 在数据库侧一次性生成高亮片段，按相关度 {@code ts_rank} 降序返回。用户隔离通过
 * JOIN {@code chat_session(user_id)} 实现，与 {@code SessionController} 安全模型一致。
 *
 * <p>索引/扩展初始化具备容错性：数据库不可达或创建失败仅打 WARN（参考 AGENTS.md：
 * Redis 不可用不阻断启动），不会阻断应用启动。
 */
@Repository
public class SearchRepository {

    private static final Logger log = LoggerFactory.getLogger(SearchRepository.class);

    /** 消息表名（Spring AI 自动建表，运行实例中实际为小写 spring_ai_chat_memory） */
    private static final String MSG_TABLE = "spring_ai_chat_memory";
    /** 会话归属表名 */
    private static final String SESSION_TABLE = "chat_session";
    /** 全文检索字典：simple 对英文/代码符号/中文按基础拆分，兼容无中文分词插件的 PG */
    private static final String TS_DICT = "simple";
    /** 默认返回上限 */
    private static final int DEFAULT_LIMIT = 50;

    private static final int MAX_LIMIT = 200;

    private static final RowMapper<SearchResultItem> ROW_MAPPER = (rs, rowNum) -> new SearchResultItem(
            rs.getString("session_id"),
            rs.getLong("message_id"),
            rs.getString("role"),
            rs.getString("snippet"),
            rs.getTimestamp("ts").getTime());

    private final JdbcTemplate jdbcTemplate;

    public SearchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initSchema() {
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm;");
            // GENERATED 列对现有 INSERT 透明；ADD COLUMN IF NOT EXISTS 保证幂等
            jdbcTemplate.execute("ALTER TABLE " + MSG_TABLE
                    + " ADD COLUMN IF NOT EXISTS content_tsv tsvector"
                    + " GENERATED ALWAYS AS (to_tsvector('" + TS_DICT + "', content)) STORED;");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_" + MSG_TABLE + "_tsv ON " + MSG_TABLE
                    + " USING GIN(content_tsv);");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_" + MSG_TABLE + "_content_trgm ON " + MSG_TABLE
                    + " USING GIN(content gin_trgm_ops);");
            log.info("PostgreSQL 全文检索扩展/索引初始化成功（表 {}）", MSG_TABLE);
        } catch (Exception ex) {
            // 历史表结构或 PG 版本不兼容（如 H2 测试库、缺扩展权限）时不阻断启动
            log.warn("初始化 PostgreSQL 全文检索索引失败（可跳过，不影响其他功能）: {}", ex.getMessage());
        }
    }

    /**
     * 按当前用户隔离，检索其归属会话下的匹配消息。
     *
     * @param userId 当前用户 ID（来自 X-User-Id）
     * @param q      已转义（plainto_tsquery）的查询关键字
     * @param limit  返回上限，<=0 时用默认；超过上限时 clamp
     * @return 命中结果列表（按相关度降序）
     */
    public List<SearchResultItem> searchByUser(String userId, String q, int limit) {
        int effectiveLimit = (limit <= 0) ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        String sql = """
				SELECT
					m.conversation_id AS session_id,
					m.sequence_id     AS message_id,
					m.type            AS role,
					ts_headline('%s', m.content, plainto_tsquery('%s', ?),
						'StartSel=<b>,StopSel=</b>,MaxWords=20,MinWords=5') AS snippet,
					m."timestamp"     AS ts
				FROM %s m
				JOIN %s s ON s.id = m.conversation_id AND s.user_id = ?
				WHERE m.content_tsv @@ plainto_tsquery('%s', ?)
				   OR m.content ILIKE '%%' || ? || '%%'
				ORDER BY ts_rank(m.content_tsv, plainto_tsquery('%s', ?)) DESC
				LIMIT ?
				""".formatted(TS_DICT, TS_DICT, MSG_TABLE, SESSION_TABLE, TS_DICT, TS_DICT);

        return jdbcTemplate.query(sql, ROW_MAPPER, q, userId, q, q, q, effectiveLimit);
    }
}
