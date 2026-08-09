package xyz.ppmblszdp.ai.rag.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import xyz.ppmblszdp.ai.memory.SafeEmbeddingModel;
import xyz.ppmblszdp.ai.memory.SafeVectorStore;
import xyz.ppmblszdp.ai.rag.RagProperties;

import javax.sql.DataSource;

/**
 * RAG 独立向量库配置：构造独立 pgvector 表 {@code ai_rag_documents}，与 {@code ai_long_term_memory}
 * 物理隔离。复用同一 PostgreSQL DataSource/JdbcTemplate，但使用独立表名与索引。
 *
 * <p>仅在 {@code app.ai.rag.enabled=true} 时装配。如果数据源不可用或 EmbeddingModel 未就绪，
 * 将静默降级（记录 WARN，不阻断启动），通过 {@link SafeVectorStore} 容错包装保证对话主流程不中断。
 *
 * <p>使用手动构造 {@link PgVectorStore} 而非依赖 Spring AI autoconfigure 的默认 VectorStore Bean，
 * 实现精确的表名控制与独立集合。
 */
@Configuration
@ConditionalOnProperty(prefix = "app.ai.rag", name = "enabled", havingValue = "true")
public class RagVectorStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(RagVectorStoreConfig.class);

    public static final int VECTOR_DIMENSIONS = 1536;

    /**
     * RAG 专用的容错 VectorStore Bean。
     * <p>手动 new PgVectorStore 以绑定独立表 {@code ai_rag_documents}，区别于
     * autoconfigure 提供的默认 VectorStore（指向 {@code ai_long_term_memory}）。
     */
    @Bean
    public VectorStore ragVectorStore(
            ObjectProvider<DataSource> dataSource,
            ObjectProvider<SafeEmbeddingModel> safeEmbedding,
            RagProperties properties) {

        DataSource ds = dataSource.getIfAvailable();
        SafeEmbeddingModel embedding = safeEmbedding.getIfAvailable();

        if (ds == null || embedding == null) {
            log.warn("RAG 向量库不可用（DataSource={} EmbeddingModel={}），降级为 No-op SafeVectorStore",
                    ds != null ? "OK" : "MISSING",
                    embedding != null ? "OK" : "MISSING");
            return new SafeVectorStore(null);
        }

        JdbcTemplate jdbcTemplate = new JdbcTemplate(ds);
        String collectionName = properties.resolveCollectionName();

        PgVectorStore store = PgVectorStore.builder(jdbcTemplate, embedding)
                .vectorTableName(collectionName)
                .schemaName("public")
                .dimensions(VECTOR_DIMENSIONS)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .removeExistingVectorStoreTable(false)
                .initializeSchema(true)
                .build();

        log.info("RAG 独立向量库装配完成: table={} dimensions={} indexType=HNSW distanceType=COSINE",
                collectionName, VECTOR_DIMENSIONS);
        return new SafeVectorStore(store);
    }
}
