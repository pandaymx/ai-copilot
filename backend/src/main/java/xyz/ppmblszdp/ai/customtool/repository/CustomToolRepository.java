package xyz.ppmblszdp.ai.customtool.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import xyz.ppmblszdp.ai.customtool.dto.CustomToolDto;
import xyz.ppmblszdp.ai.customtool.dto.CustomToolDto.HttpConfigDto;
import xyz.ppmblszdp.ai.customtool.dto.CustomToolDto.PromptConfigDto;
import xyz.ppmblszdp.ai.customtool.dto.CustomToolDto.ScriptConfigDto;
import xyz.ppmblszdp.ai.customtool.model.CustomToolType;

/**
 * 自定义工具持久化仓库（PostgreSQL + JdbcTemplate）。
 *
 * <p>按 {@code user_id} 进行多租户数据隔离。
 */
@Repository
public class CustomToolRepository {

    private static final Logger log = LoggerFactory.getLogger(CustomToolRepository.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;

    public CustomToolRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initSchema() {
        try {
            jdbcTemplate.execute("""
					CREATE TABLE IF NOT EXISTS custom_tools (
						id VARCHAR(64) PRIMARY KEY,
						user_id VARCHAR(128) NOT NULL,
						name VARCHAR(64) NOT NULL,
						display_name VARCHAR(128),
						description TEXT,
						type VARCHAR(32) NOT NULL,
						enabled BOOLEAN DEFAULT TRUE,
						parameters_schema TEXT,
						config_json TEXT,
						created_at BIGINT NOT NULL,
						updated_at BIGINT NOT NULL
					);
					""");
            jdbcTemplate.execute(
                    "CREATE UNIQUE INDEX IF NOT EXISTS idx_custom_tools_user_name ON custom_tools(user_id, name);");
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_custom_tools_user_updated ON custom_tools(user_id, updated_at DESC);");
            log.info("PostgreSQL 自定义工具表 'custom_tools' 初始化/校验成功");
        } catch (Exception ex) {
            log.error("初始化 PostgreSQL 自定义工具表失败: {}", ex.getMessage(), ex);
        }
    }

    private final RowMapper<CustomToolDto> rowMapper = (rs, rowNum) -> {
        String id = rs.getString("id");
        String name = rs.getString("name");
        String displayName = rs.getString("display_name");
        String description = rs.getString("description");
        String typeStr = rs.getString("type");
        CustomToolType type = CustomToolType.valueOf(typeStr);
        boolean enabled = rs.getBoolean("enabled");
        String parametersSchema = rs.getString("parameters_schema");
        String configJson = rs.getString("config_json");
        long createdAt = rs.getLong("created_at");
        long updatedAt = rs.getLong("updated_at");

        HttpConfigDto httpConfig = null;
        ScriptConfigDto scriptConfig = null;
        PromptConfigDto promptConfig = null;

        if (configJson != null && !configJson.isBlank()) {
            try {
                Map<String, Object> map = MAPPER.readValue(configJson, new TypeReference<>() {});
                if (map.containsKey("httpConfig") && map.get("httpConfig") != null) {
                    httpConfig = MAPPER.convertValue(map.get("httpConfig"), HttpConfigDto.class);
                }
                if (map.containsKey("scriptConfig") && map.get("scriptConfig") != null) {
                    scriptConfig = MAPPER.convertValue(map.get("scriptConfig"), ScriptConfigDto.class);
                }
                if (map.containsKey("promptConfig") && map.get("promptConfig") != null) {
                    promptConfig = MAPPER.convertValue(map.get("promptConfig"), PromptConfigDto.class);
                }
            } catch (Exception e) {
                log.warn("解析 custom_tools config_json 异常 (id={}): {}", id, e.getMessage());
            }
        }

        return new CustomToolDto(
                id,
                name,
                displayName,
                description,
                type,
                enabled,
                parametersSchema,
                httpConfig,
                scriptConfig,
                promptConfig,
                createdAt,
                updatedAt);
    };

    /**
     * 查询指定用户的所有自定义工具列表（按更新时间倒序）。
     */
    public List<CustomToolDto> findByUserId(String userId) {
        String sql = "SELECT * FROM custom_tools WHERE user_id = ? ORDER BY updated_at DESC;";
        return jdbcTemplate.query(sql, rowMapper, userId);
    }

    /**
     * 查询指定用户的已启用工具列表。
     */
    public List<CustomToolDto> findByUserIdAndEnabledTrue(String userId) {
        String sql = "SELECT * FROM custom_tools WHERE user_id = ? AND enabled = TRUE ORDER BY updated_at DESC;";
        return jdbcTemplate.query(sql, rowMapper, userId);
    }

    /**
     * 根据 ID 和用户 ID 查询工具。
     */
    public Optional<CustomToolDto> findByIdAndUserId(String id, String userId) {
        String sql = "SELECT * FROM custom_tools WHERE id = ? AND user_id = ?;";
        List<CustomToolDto> list = jdbcTemplate.query(sql, rowMapper, id, userId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /**
     * 检查工具名称在当前用户下是否已被占用（可排除自身 ID）。
     */
    public boolean existsByNameAndUserId(String name, String userId, String excludeId) {
        String sql;
        if (excludeId != null && !excludeId.isBlank()) {
            sql = "SELECT COUNT(*) FROM custom_tools WHERE user_id = ? AND name = ? AND id <> ?;";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId, name, excludeId);
            return count != null && count > 0;
        } else {
            sql = "SELECT COUNT(*) FROM custom_tools WHERE user_id = ? AND name = ?;";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId, name);
            return count != null && count > 0;
        }
    }

    /**
     * 新增或更新自定义工具。
     */
    public void save(CustomToolDto tool, String userId) {
        String configJson = serializeConfig(tool);
        String sql = """
				INSERT INTO custom_tools (
					id, user_id, name, display_name, description, type,
					enabled, parameters_schema, config_json, created_at, updated_at
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (id) DO UPDATE SET
					name = EXCLUDED.name,
					display_name = EXCLUDED.display_name,
					description = EXCLUDED.description,
					type = EXCLUDED.type,
					enabled = EXCLUDED.enabled,
					parameters_schema = EXCLUDED.parameters_schema,
					config_json = EXCLUDED.config_json,
					updated_at = EXCLUDED.updated_at;
				""";
        jdbcTemplate.update(
                sql,
                tool.id(),
                userId,
                tool.name(),
                tool.displayName(),
                tool.description(),
                tool.type().name(),
                tool.enabled() != null ? tool.enabled() : true,
                tool.parametersSchema(),
                configJson,
                tool.createdAt() != null ? tool.createdAt() : System.currentTimeMillis(),
                tool.updatedAt() != null ? tool.updatedAt() : System.currentTimeMillis());
    }

    /**
     * 快速切换启用状态。
     */
    public boolean toggleEnabled(String id, String userId) {
        String sql = "UPDATE custom_tools SET enabled = NOT enabled, updated_at = ? WHERE id = ? AND user_id = ?;";
        int rows = jdbcTemplate.update(sql, System.currentTimeMillis(), id, userId);
        return rows > 0;
    }

    /**
     * 删除指定工具。
     */
    public boolean deleteByIdAndUserId(String id, String userId) {
        String sql = "DELETE FROM custom_tools WHERE id = ? AND user_id = ?;";
        int rows = jdbcTemplate.update(sql, id, userId);
        return rows > 0;
    }

    private String serializeConfig(CustomToolDto tool) {
        Map<String, Object> map = new java.util.HashMap<>();
        if (tool.httpConfig() != null) {
            map.put("httpConfig", tool.httpConfig());
        }
        if (tool.scriptConfig() != null) {
            map.put("scriptConfig", tool.scriptConfig());
        }
        if (tool.promptConfig() != null) {
            map.put("promptConfig", tool.promptConfig());
        }
        try {
            return MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }
}
