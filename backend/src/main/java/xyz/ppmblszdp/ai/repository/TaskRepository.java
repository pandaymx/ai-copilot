package xyz.ppmblszdp.ai.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 任务持久化仓库（PostgreSQL + JdbcTemplate）。
 *
 * <p>按 {@code user_id} 多租户隔离；标签与依赖关系以 JSON 文本存储；状态受 {@link TaskDto#VALID_STATUSES} 约束。
 */
@Repository
public class TaskRepository {

    private static final Logger log = LoggerFactory.getLogger(TaskRepository.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;

    public TaskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initSchema() {
        try {
            jdbcTemplate.execute("""
					CREATE TABLE IF NOT EXISTS tasks (
						id VARCHAR(64) PRIMARY KEY,
						user_id VARCHAR(128) NOT NULL,
						title VARCHAR(256) NOT NULL,
						description TEXT,
						status VARCHAR(32) NOT NULL DEFAULT 'TODO',
						priority INTEGER NOT NULL DEFAULT 3,
						due_date TIMESTAMPTZ,
						tags TEXT,
						assignee VARCHAR(128),
						dependencies TEXT,
						created_at BIGINT NOT NULL,
						updated_at BIGINT NOT NULL
					);
					""");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_tasks_user_status ON tasks(user_id, status);");
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_tasks_user_updated ON tasks(user_id, updated_at DESC);");
            log.info("PostgreSQL 任务表 'tasks' 初始化/校验成功");
        } catch (Exception ex) {
            log.error("初始化 PostgreSQL 任务表失败: {}", ex.getMessage(), ex);
        }
    }

    private final RowMapper<TaskDto> rowMapper = (rs, rowNum) -> new TaskDto(
            rs.getString("id"),
            rs.getString("title"),
            rs.getString("description"),
            rs.getString("status"),
            rs.getInt("priority"),
            rs.getObject("due_date", Instant.class),
            parseJsonList(rs.getString("tags")),
            rs.getString("assignee"),
            parseJsonList(rs.getString("dependencies")),
            rs.getLong("created_at"),
            rs.getLong("updated_at"));

    /** 新增或更新任务（按 id upsert），返回任务 id。 */
    public String save(TaskDto task, String userId) {
        String id = task.id() != null && !task.id().isBlank()
                ? task.id()
                : UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        boolean exists = task.id() != null
                && jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM tasks WHERE id = ? AND user_id = ?;",
                                Integer.class,
                                task.id(),
                                userId)
                        > 0;
        long createdAt = exists ? task.createdAt() : now;
        long updatedAt = now;

        String sql = """
				INSERT INTO tasks (
					id, user_id, title, description, status, priority,
					due_date, tags, assignee, dependencies, created_at, updated_at
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (id) DO UPDATE SET
					title = EXCLUDED.title,
					description = EXCLUDED.description,
					status = EXCLUDED.status,
					priority = EXCLUDED.priority,
					due_date = EXCLUDED.due_date,
					tags = EXCLUDED.tags,
					assignee = EXCLUDED.assignee,
					dependencies = EXCLUDED.dependencies,
					updated_at = EXCLUDED.updated_at;
				""";
        jdbcTemplate.update(
                sql,
                id,
                userId,
                task.title(),
                task.description(),
                TaskDto.isValidStatus(task.status()) ? task.status() : "TODO",
                task.priority() > 0 ? task.priority() : 3,
                task.dueDate(),
                serializeJsonList(task.tags()),
                task.assignee(),
                serializeJsonList(task.dependencies()),
                createdAt,
                updatedAt);
        return id;
    }

    public List<TaskDto> findByUserId(String userId) {
        String sql = "SELECT * FROM tasks WHERE user_id = ? ORDER BY priority ASC, updated_at DESC;";
        return jdbcTemplate.query(sql, rowMapper, userId);
    }

    public List<TaskDto> findByUserIdAndFilters(String userId, String status, Integer priority, List<String> tags) {
        StringBuilder sql = new StringBuilder("SELECT * FROM tasks WHERE user_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        if (priority != null && priority > 0) {
            sql.append(" AND priority = ?");
            args.add(priority);
        }
        sql.append(" ORDER BY priority ASC, updated_at DESC;");
        return jdbcTemplate.query(sql.toString(), rowMapper, args.toArray());
    }

    public TaskDto findByIdAndUserId(String id, String userId) {
        String sql = "SELECT * FROM tasks WHERE id = ? AND user_id = ?;";
        List<TaskDto> list = jdbcTemplate.query(sql, rowMapper, id, userId);
        return list.isEmpty() ? null : list.get(0);
    }

    public boolean updateStatus(String id, String userId, String status) {
        if (!TaskDto.isValidStatus(status)) {
            return false;
        }
        String sql = "UPDATE tasks SET status = ?, updated_at = ? WHERE id = ? AND user_id = ?;";
        return jdbcTemplate.update(sql, status, System.currentTimeMillis(), id, userId) > 0;
    }

    public boolean deleteByIdAndUserId(String id, String userId) {
        String sql = "DELETE FROM tasks WHERE id = ? AND user_id = ?;";
        return jdbcTemplate.update(sql, id, userId) > 0;
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String serializeJsonList(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }
}
