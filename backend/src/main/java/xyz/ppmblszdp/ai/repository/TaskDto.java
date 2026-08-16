package xyz.ppmblszdp.ai.repository;

import java.time.Instant;
import java.util.List;

/**
 * 任务数据传输对象。
 *
 * <p>优先级用整数表示（1=最高，4=最低）；状态为受控枚举字符串
 * {@code TODO / IN_PROGRESS / DONE / BLOCKED}。
 */
public record TaskDto(
        String id,
        String title,
        String description,
        String status,
        int priority,
        Instant dueDate,
        List<String> tags,
        String assignee,
        List<String> dependencies,
        long createdAt,
        long updatedAt) {

    public static final List<String> VALID_STATUSES = List.of("TODO", "IN_PROGRESS", "DONE", "BLOCKED");

    public static boolean isValidStatus(String status) {
        return status != null && VALID_STATUSES.contains(status);
    }

    public TaskDto withStatus(String status) {
        return new TaskDto(
                id, title, description, status, priority, dueDate, tags, assignee, dependencies, createdAt, updatedAt);
    }

    public TaskDto withTimestamps(long createdAt, long updatedAt) {
        return new TaskDto(
                id, title, description, status, priority, dueDate, tags, assignee, dependencies, createdAt, updatedAt);
    }
}
