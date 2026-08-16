package xyz.ppmblszdp.ai.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 日历事件持久化仓库（PostgreSQL + JdbcTemplate）。
 *
 * <p>按 {@code user_id} 多租户隔离；时间字段统一为 {@code TIMESTAMPTZ}（映射 {@link Instant} UTC），
 * 参与者列表以 JSON 文本存储。DDL 在启动时幂等建表。
 */
@Repository
public class CalendarEventRepository {

    private static final Logger log = LoggerFactory.getLogger(CalendarEventRepository.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;

    public CalendarEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initSchema() {
        try {
            jdbcTemplate.execute("""
					CREATE TABLE IF NOT EXISTS calendar_events (
						id VARCHAR(64) PRIMARY KEY,
						user_id VARCHAR(128) NOT NULL,
						title VARCHAR(256) NOT NULL,
						description TEXT,
						start_time TIMESTAMPTZ NOT NULL,
						end_time TIMESTAMPTZ,
						all_day BOOLEAN DEFAULT FALSE,
						reminder_minutes INTEGER DEFAULT 0,
						attendees TEXT,
						location VARCHAR(256),
						created_at BIGINT NOT NULL,
						updated_at BIGINT NOT NULL
					);
					""");
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_calendar_user_start ON calendar_events(user_id, start_time);");
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_calendar_user_updated ON calendar_events(user_id, updated_at DESC);");
            log.info("PostgreSQL 日历事件表 'calendar_events' 初始化/校验成功");
        } catch (Exception ex) {
            log.error("初始化 PostgreSQL 日历事件表失败: {}", ex.getMessage(), ex);
        }
    }

    private final RowMapper<CalendarEventDto> rowMapper = (rs, rowNum) -> {
        List<String> attendees = List.of();
        String attendeesJson = rs.getString("attendees");
        if (attendeesJson != null && !attendeesJson.isBlank()) {
            try {
                attendees = MAPPER.readValue(attendeesJson, new TypeReference<>() {});
            } catch (Exception e) {
                log.warn("解析 calendar_events attendees 异常 (id={}): {}", rs.getString("id"), e.getMessage());
            }
        }
        return new CalendarEventDto(
                rs.getString("id"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getObject("start_time", Instant.class),
                rs.getObject("end_time", Instant.class),
                rs.getBoolean("all_day"),
                rs.getInt("reminder_minutes"),
                attendees,
                rs.getString("location"),
                rs.getLong("created_at"),
                rs.getLong("updated_at"));
    };

    /** 新增或更新日历事件（按 id upsert），返回事件 id。 */
    public String save(CalendarEventDto event, String userId) {
        String id = event.id() != null && !event.id().isBlank()
                ? event.id()
                : UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        boolean exists = event.id() != null
                && jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM calendar_events WHERE id = ? AND user_id = ?;",
                                Integer.class,
                                event.id(),
                                userId)
                        > 0;
        long createdAt = exists ? event.createdAt() : now;
        long updatedAt = now;
        String attendeesJson = serializeAttendees(event.attendees());

        String sql = """
				INSERT INTO calendar_events (
					id, user_id, title, description, start_time, end_time,
					all_day, reminder_minutes, attendees, location, created_at, updated_at
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (id) DO UPDATE SET
					title = EXCLUDED.title,
					description = EXCLUDED.description,
					start_time = EXCLUDED.start_time,
					end_time = EXCLUDED.end_time,
					all_day = EXCLUDED.all_day,
					reminder_minutes = EXCLUDED.reminder_minutes,
					attendees = EXCLUDED.attendees,
					location = EXCLUDED.location,
					updated_at = EXCLUDED.updated_at;
				""";
        jdbcTemplate.update(
                sql,
                id,
                userId,
                event.title(),
                event.description(),
                event.start(),
                event.end(),
                event.allDay(),
                event.reminderMinutes(),
                attendeesJson,
                event.location(),
                createdAt,
                updatedAt);
        return id;
    }

    public List<CalendarEventDto> findByUserId(String userId) {
        String sql = "SELECT * FROM calendar_events WHERE user_id = ? ORDER BY start_time ASC;";
        return jdbcTemplate.query(sql, rowMapper, userId);
    }

    public List<CalendarEventDto> findByUserIdAndTimeRange(String userId, Instant startTime, Instant endTime) {
        String sql =
                "SELECT * FROM calendar_events WHERE user_id = ? AND start_time >= ? AND start_time <= ? ORDER BY start_time ASC;";
        return jdbcTemplate.query(sql, rowMapper, userId, startTime, endTime);
    }

    public CalendarEventDto findByIdAndUserId(String id, String userId) {
        String sql = "SELECT * FROM calendar_events WHERE id = ? AND user_id = ?;";
        List<CalendarEventDto> list = jdbcTemplate.query(sql, rowMapper, id, userId);
        return list.isEmpty() ? null : list.get(0);
    }

    public boolean deleteByIdAndUserId(String id, String userId) {
        String sql = "DELETE FROM calendar_events WHERE id = ? AND user_id = ?;";
        return jdbcTemplate.update(sql, id, userId) > 0;
    }

    private String serializeAttendees(List<String> attendees) {
        if (attendees == null || attendees.isEmpty()) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(attendees);
        } catch (Exception e) {
            return "[]";
        }
    }
}
