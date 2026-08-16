package xyz.ppmblszdp.ai.repository;

import java.time.Instant;
import java.util.List;

/**
 * 日历事件数据传输对象。
 *
 * <p>时间字段统一使用 {@link Instant}（UTC），入库映射为 PostgreSQL {@code TIMESTAMPTZ}，
 * 避免大模型生成的多变日期格式导致时区偏差。
 */
public record CalendarEventDto(
        String id,
        String title,
        String description,
        Instant start,
        Instant end,
        boolean allDay,
        int reminderMinutes,
        List<String> attendees,
        String location,
        long createdAt,
        long updatedAt) {

    public CalendarEventDto withId(String id) {
        return new CalendarEventDto(
                id, title, description, start, end, allDay, reminderMinutes, attendees, location, createdAt, updatedAt);
    }

    public CalendarEventDto withTimestamps(long createdAt, long updatedAt) {
        return new CalendarEventDto(
                id, title, description, start, end, allDay, reminderMinutes, attendees, location, createdAt, updatedAt);
    }
}
