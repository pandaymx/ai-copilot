package xyz.ppmblszdp.ai.repository;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * API Key 持久化仓库（PostgreSQL + JdbcTemplate）。
 *
 * <p>按 {@code user_id} 多租户严格隔离；Key 字段以密文（AES-256-GCM）落库。
 * 针对 (user_id, provider) 建立唯一约束，每个用户每个供应商只保留一条主配置。
 */
@Repository
public class ApiKeyRepository {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public ApiKeyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record ApiKeyEntity(
            String id,
            String userId,
            String provider,
            String encryptedKey,
            String status,
            String balance,
            String errorMessage,
            long createdAt,
            long updatedAt) {}

    @PostConstruct
    public void initSchema() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS api_keys (
                        id VARCHAR(64) PRIMARY KEY,
                        user_id VARCHAR(128) NOT NULL,
                        provider VARCHAR(64) NOT NULL,
                        encrypted_key TEXT NOT NULL,
                        status VARCHAR(32) NOT NULL DEFAULT 'UNTESTED',
                        balance VARCHAR(64),
                        error_message TEXT,
                        created_at BIGINT NOT NULL,
                        updated_at BIGINT NOT NULL,
                        CONSTRAINT uq_api_keys_user_provider UNIQUE(user_id, provider)
                    );
                    """);
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_api_keys_user ON api_keys(user_id);");
            log.info("PostgreSQL API Key 表 'api_keys' 初始化/校验成功");
        } catch (Exception ex) {
            log.error("初始化 PostgreSQL API Key 表失败: {}", ex.getMessage(), ex);
        }
    }

    private final RowMapper<ApiKeyEntity> rowMapper = (rs, rowNum) -> new ApiKeyEntity(
            rs.getString("id"),
            rs.getString("user_id"),
            rs.getString("provider"),
            rs.getString("encrypted_key"),
            rs.getString("status"),
            rs.getString("balance"),
            rs.getString("error_message"),
            rs.getLong("created_at"),
            rs.getLong("updated_at"));

    public String save(String userId, String provider, String encryptedKey) {
        String normalizedProvider = provider.trim().toLowerCase();
        long now = System.currentTimeMillis();

        Optional<ApiKeyEntity> existing = findByUserAndProvider(userId, normalizedProvider);
        if (existing.isPresent()) {
            ApiKeyEntity old = existing.get();
            jdbcTemplate.update("""
                    UPDATE api_keys
                    SET encrypted_key = ?, status = 'UNTESTED', error_message = NULL, updated_at = ?
                    WHERE id = ? AND user_id = ?;
                    """, encryptedKey, now, old.id(), userId);
            return old.id();
        } else {
            String id = "key_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            jdbcTemplate.update("""
                    INSERT INTO api_keys (id, user_id, provider, encrypted_key, status, balance, error_message, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 'UNTESTED', NULL, NULL, ?, ?);
                    """, id, userId, normalizedProvider, encryptedKey, now, now);
            return id;
        }
    }

    public List<ApiKeyEntity> findAllByUserId(String userId) {
        return jdbcTemplate.query(
                "SELECT * FROM api_keys WHERE user_id = ? ORDER BY updated_at DESC;", rowMapper, userId);
    }

    public Optional<ApiKeyEntity> findById(String id, String userId) {
        try {
            ApiKeyEntity entity = jdbcTemplate.queryForObject(
                    "SELECT * FROM api_keys WHERE id = ? AND user_id = ?;", rowMapper, id, userId);
            return Optional.ofNullable(entity);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<ApiKeyEntity> findByUserAndProvider(String userId, String provider) {
        try {
            ApiKeyEntity entity = jdbcTemplate.queryForObject(
                    "SELECT * FROM api_keys WHERE user_id = ? AND provider = ?;",
                    rowMapper,
                    userId,
                    provider.trim().toLowerCase());
            return Optional.ofNullable(entity);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public boolean delete(String id, String userId) {
        int rows = jdbcTemplate.update("DELETE FROM api_keys WHERE id = ? AND user_id = ?;", id, userId);
        return rows > 0;
    }

    public void updateStatus(String id, String userId, String status, String balance, String errorMessage) {
        long now = System.currentTimeMillis();
        jdbcTemplate.update("""
                UPDATE api_keys
                SET status = ?, balance = ?, error_message = ?, updated_at = ?
                WHERE id = ? AND user_id = ?;
                """, status, balance, errorMessage, now, id, userId);
    }
}
