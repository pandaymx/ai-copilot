package xyz.ppmblszdp.ai.rag.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import xyz.ppmblszdp.ai.rag.RagProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 全文与模糊检索 Repository（基于 PostgreSQL {@code tsvector} + {@code pg_trgm}）。
 *
 * <p>在独立 pgvector 表（默认为 {@code ai_rag_documents}）之上，幂等地初始化全文检索生成列与 GIN 索引，
 * 提供中文/代码文本的全词与模糊子串匹配能力，作为向量检索的强补位召回。
 */
@Repository
@ConditionalOnProperty(prefix = "app.ai.rag", name = "enabled", havingValue = "true")
public class RagSearchRepository {

    private static final Logger log = LoggerFactory.getLogger(RagSearchRepository.class);
    private static final String TS_DICT = "simple";

    private final JdbcTemplate jdbcTemplate;
    private final RagProperties properties;
    private final ObjectMapper objectMapper;

    public RagSearchRepository(JdbcTemplate jdbcTemplate, RagProperties properties, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initSchema() {
        String table = properties.resolveCollectionName();
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm;");
            jdbcTemplate.execute(
                    "ALTER TABLE " + table
                            + " ADD COLUMN IF NOT EXISTS content_tsv tsvector"
                            + " GENERATED ALWAYS AS (to_tsvector('" + TS_DICT + "', content)) STORED;");
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_" + table + "_tsv ON " + table + " USING GIN(content_tsv);");
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_" + table + "_content_trgm ON " + table
                            + " USING GIN(content gin_trgm_ops);");
            jdbcTemplate.execute(
                    "ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS structured_knowledge JSONB;");
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_" + table + "_struct_meta ON " + table
                            + " USING GIN ((metadata->'structuredKnowledge'));");
            log.info("RAG 全文检索与结构化索引初始化成功（表 {}）", table);
        } catch (Exception ex) {
            log.warn("初始化 RAG 全文与结构化检索索引失败（非 PG 数据库或缺扩展权限时自动降级，不阻断启动）: {}", ex.getMessage());
        }
    }

    /**
     * 执行全文 + 模糊检索。
     *
     * @param query      查询关键词
     * @param userId     用户隔离 ID（可选）
     * @param sourceType 来源类型过滤（可选）
     * @param limit      召回数量上限
     * @return 匹配的 Document 列表
     */
    public List<Document> searchFullText(String query, String userId, String sourceType, int limit) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        String table = properties.resolveCollectionName();
        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();

        sql.append("SELECT id, content, metadata FROM ").append(table).append(" WHERE (")
                .append("content_tsv @@ plainto_tsquery('").append(TS_DICT).append("', ?) ")
                .append("OR content ILIKE '%' || ? || '%'")
                .append(")");
        params.add(query);
        params.add(query);

        if (userId != null && !userId.isBlank()) {
            sql.append(" AND metadata->>'userId' = ?");
            params.add(userId);
        }

        if (sourceType != null && !sourceType.isBlank()) {
            sql.append(" AND metadata->>'sourceType' = ?");
            params.add(sourceType);
        }

        sql.append(" ORDER BY ts_rank(content_tsv, plainto_tsquery('").append(TS_DICT).append("', ?)) DESC");
        params.add(query);

        int effectiveLimit = limit > 0 ? limit : 20;
        sql.append(" LIMIT ?");
        params.add(effectiveLimit);

        try {
            return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
                String id = rs.getString("id");
                String content = rs.getString("content");
                String metaJson = rs.getString("metadata");
                Map<String, Object> metadata = parseMetadata(metaJson);
                return new Document(id, content, metadata);
            }, params.toArray());
        } catch (Exception e) {
            log.warn("RAG 全文检索查询失败（已降级为空）: query=... error={}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 执行结构化知识查询（基于 PostgreSQL JSONB 匹配 metadata 中的 structuredKnowledge 实体/摘要/事实）。
     *
     * @param query      查询关键词/实体名
     * @param userId     用户隔离 ID（可选）
     * @param sourceType 来源类型过滤（可选）
     * @param limit      召回数量上限
     * @return 匹配的 Document 列表
     */
    public List<Document> searchStructuredKnowledge(String query, String userId, String sourceType, int limit) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        String table = properties.resolveCollectionName();
        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();

        sql.append("SELECT id, content, metadata FROM ").append(table).append(" WHERE (")
                .append("COALESCE(structured_knowledge, metadata->'structuredKnowledge')->>'title' ILIKE '%' || ? || '%' ")
                .append("OR COALESCE(structured_knowledge, metadata->'structuredKnowledge')->>'summary' ILIKE '%' || ? || '%' ")
                .append("OR COALESCE(structured_knowledge, metadata->'structuredKnowledge')->'entities'::text ILIKE '%' || ? || '%' ")
                .append("OR COALESCE(structured_knowledge, metadata->'structuredKnowledge')->'keyFacts'::text ILIKE '%' || ? || '%'")
                .append(")");
        params.add(query);
        params.add(query);
        params.add(query);
        params.add(query);

        if (userId != null && !userId.isBlank()) {
            sql.append(" AND metadata->>'userId' = ?");
            params.add(userId);
        }

        if (sourceType != null && !sourceType.isBlank()) {
            sql.append(" AND metadata->>'sourceType' = ?");
            params.add(sourceType);
        }

        int effectiveLimit = limit > 0 ? limit : 20;
        sql.append(" LIMIT ?");
        params.add(effectiveLimit);

        try {
            return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
                String id = rs.getString("id");
                String content = rs.getString("content");
                String metaJson = rs.getString("metadata");
                Map<String, Object> metadata = parseMetadata(metaJson);
                return new Document(id, content, metadata);
            }, params.toArray());
        } catch (Exception e) {
            log.warn("RAG 结构化知识检索查询失败（已降级为空）: query=... error={}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.debug("解析 RAG metadata JSON 异常: {}", e.getMessage());
            return new HashMap<>();
        }
    }
}
