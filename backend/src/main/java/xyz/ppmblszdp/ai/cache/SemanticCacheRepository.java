package xyz.ppmblszdp.ai.cache;

import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 语义缓存持久层（5-#4）。
 *
 * <p>复用主 PostgreSQL 实例，自建物理隔离的 {@code ai_semantic_cache} 表（与 RAG 的
 * pgvector 表分离）。向量列使用 pgvector 的 {@code vector} 类型，按 {@code user_id}
 * 维度做多租户隔离；相似度检索使用余弦距离运算符 {@code <#>}。
 *
 * <p>建表语句幂等（{@code CREATE TABLE IF NOT EXISTS}），满足多租户铁律：
 * 含 {@code user_id VARCHAR(128) NOT NULL} 与对应索引。
 */
@Repository
public class SemanticCacheRepository {

    private static final Logger log = LoggerFactory.getLogger(SemanticCacheRepository.class);

    private final ObjectProvider<DataSource> dataSourceProvider;
    private volatile boolean initialized = false;

    public SemanticCacheRepository(ObjectProvider<DataSource> dataSourceProvider) {
        this.dataSourceProvider = dataSourceProvider;
    }

    private JdbcTemplate jdbc() {
        DataSource ds = dataSourceProvider.getIfAvailable();
        if (ds == null) {
            return null;
        }
        return new JdbcTemplate(ds);
    }

    /** 幂等建表与索引，首次访问时执行一次。 */
    public synchronized void ensureTable() {
        if (initialized) {
            return;
        }
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            log.warn("语义缓存：DataSource 不可用，跳过建表");
            return;
        }
        try {
            jdbc.execute("CREATE TABLE IF NOT EXISTS ai_semantic_cache ("
                    + "id BIGSERIAL PRIMARY KEY, "
                    + "user_id VARCHAR(128) NOT NULL, "
                    + "req_hash VARCHAR(64) NOT NULL, "
                    + "embedding vector, "
                    + "payload JSONB NOT NULL, "
                    + "provider VARCHAR(64), "
                    + "model VARCHAR(64), "
                    + "created_at TIMESTAMPTZ NOT NULL DEFAULT now(), "
                    + "expires_at TIMESTAMPTZ NOT NULL DEFAULT now() + interval '7 days', "
                    + "hits INTEGER NOT NULL DEFAULT 0)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_semantic_cache_user ON ai_semantic_cache(user_id)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_semantic_cache_expiry ON ai_semantic_cache(expires_at)");
            initialized = true;
            log.info("语义缓存表 ai_semantic_cache 已就绪");
        } catch (Exception e) {
            log.warn("语义缓存建表失败（忽略，功能降级）: {}", e.getMessage());
        }
    }

    /**
     * 检索与查询向量语义最相似的缓存条目（按 user_id 隔离，未过期）。
     *
     * @param userId    用户维度（多租户隔离）
     * @param vec       已归一化查询向量
     * @param threshold 余弦相似度阈值（如 0.92）
     * @return 最相似条目的 payload（JSON），无命中返回 null
     */
    public String findSimilar(String userId, float[] vec, double threshold) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return null;
        }
        ensureTable();
        double maxDist = 1.0 - threshold;
        String sql = "SELECT payload FROM ai_semantic_cache "
                + "WHERE user_id = ? AND expires_at > now() "
                + "AND (embedding <#> ?::vector) < ? "
                + "ORDER BY embedding <#> ?::vector ASC LIMIT 1";
        try {
            return jdbc.queryForObject(
                    sql, (rs, i) -> rs.getString("payload"), userId, toSqlVector(vec), maxDist, toSqlVector(vec));
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            log.debug("语义缓存检索异常（忽略）: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 写入一条语义缓存。
     *
     * @param userId   用户维度
     * @param vec      已归一化向量
     * @param reqHash  请求精确哈希（防跨模型误命中辅助字段）
     * @param payload  响应 payload（JSON）
     * @param provider 模型供应商
     * @param model    模型 id
     */
    public void save(String userId, float[] vec, String reqHash, String payload, String provider, String model) {
        JdbcTemplate jdbc = jdbc();
        if (jdbc == null) {
            return;
        }
        ensureTable();
        try {
            jdbc.update(
                    "INSERT INTO ai_semantic_cache(user_id, req_hash, embedding, payload, provider, model) "
                            + "VALUES (?, ?, ?::vector, ?::jsonb, ?, ?)",
                    userId,
                    reqHash,
                    toSqlVector(vec),
                    payload,
                    provider,
                    model);
        } catch (Exception e) {
            log.debug("语义缓存写入异常（忽略）: {}", e.getMessage());
        }
    }

    /** 归一化向量序列化为 pgvector 文本格式 "[v0,v1,...]"。 */
    static String toSqlVector(float[] vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vec[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
