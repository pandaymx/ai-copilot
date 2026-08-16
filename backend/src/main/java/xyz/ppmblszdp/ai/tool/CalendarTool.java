package xyz.ppmblszdp.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;
import xyz.ppmblszdp.ai.repository.CalendarEventDto;
import xyz.ppmblszdp.ai.repository.CalendarEventRepository;

/**
 * 日历工具（CalendarTool）：供 Agent 在对话中创建 / 查询 / 修改 / 删除日历事件，
 * 并支持导出 iCal（RFC 5545）文本。
 *
 * <p>采用单一 {@code @Tool} 入口 + {@code action} 枚举分发（CREATE / GET / UPDATE / DELETE /
 * LIST / EXPORT_ICAL），避免细粒度工具过多消耗 Token，同时保留清晰的调用分支。
 *
 * <p>多租户隔离：{@code userId} 一律取自 {@link ToolEventEmitter#CTX_USER_ID}（服务端受信任身份），
 * 不信任工具入参中的任何 userId 字段。
 *
 * <p>时区处理：时间字段统一以 {@link Instant}（UTC）存储；入参时间字符串若为 ISO-8601 带偏移
 * （如 {@code 2026-08-16T15:00:00+08:00} 或 {@code ...Z}）则按偏移换算 UTC；若为无时区裸时间
 * （如 {@code 2026-08-16T15:00}）则按 UTC 解释。建议模型统一传入带 {@code Z} 的 ISO-8601 UTC 字符串，
 * 避免 8 小时偏差。
 */
@Component
public class CalendarTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final CalendarEventRepository repository;

    public CalendarTool(CalendarEventRepository repository) {
        this.repository = repository;
    }

    @Tool(
            description = "日历工具（Calendar）：管理用户日历事件。支持创建(CREATE)、查询单个(GET)、更新(UPDATE)、"
                    + "删除(DELETE)、列出(LIST)、导出 iCal(EXPORT_ICAL)。"
                    + "时间字段统一使用 ISO-8601 UTC 格式（例如 2026-08-16T07:00:00Z）。"
                    + "更新/删除/查询单个事件时必须提供 eventId；列出时可指定时间范围 [startFrom, startTo]（UTC）。"
                    + "提醒用 reminderMinutes（提前分钟数，0 表示不提醒），attendees 为参与者邮箱或名称数组。")
    public String calendar(
            @ToolParam(description = "操作类型枚举：CREATE | GET | UPDATE | DELETE | LIST | EXPORT_ICAL") String action,
            @ToolParam(
                            description = "操作参数 JSON 对象。通用字段：eventId(UPDATE/DELETE/GET 必填)。"
                                    + "CREATE/UPDATE 字段：title, description, start(ISO-8601 UTC), end(可选 ISO-8601 UTC),"
                                    + " allDay(布尔,默认 false), reminderMinutes(整数,默认 0), attendees(字符串数组), location(字符串)。"
                                    + "LIST 字段：startFrom(可选 ISO-8601 UTC), startTo(可选 ISO-8601 UTC)。"
                                    + "EXPORT_ICAL 字段：eventId(可选；缺省导出全部)。")
                    String payloadJson,
            ToolContext toolContext) {

        String argsJson = toJson(Map.of("action", action == null ? "" : action, "payload", payloadJson));
        return ToolEventEmitter.from(toolContext).executeWithEvent("calendar_tool", argsJson, toolContext, () -> {
            String userId = resolveUserId(toolContext);
            JsonNode payload = parsePayload(payloadJson);
            return switch (normalizeAction(action)) {
                case "CREATE" -> doCreate(payload, userId);
                case "UPDATE" -> doUpdate(payload, userId);
                case "GET" -> doGet(payload, userId);
                case "DELETE" -> doDelete(payload, userId);
                case "LIST" -> doList(payload, userId);
                case "EXPORT_ICAL" -> doExportIcal(payload, userId);
                default -> errorJson("未知 action: " + action);
            };
        });
    }

    private String doCreate(JsonNode p, String userId) {
        CalendarEventDto event = mapEvent(p, null);
        return blockDb(() -> {
            String id = repository.save(event, userId);
            return okJson("created", Map.of("eventId", id, "event", repository.findByIdAndUserId(id, userId)));
        });
    }

    private String doUpdate(JsonNode p, String userId) {
        String eventId = requireEventId(p);
        CalendarEventDto existing = blockDb(() -> repository.findByIdAndUserId(eventId, userId));
        if (existing == null) {
            return errorJson("事件不存在或无权访问: " + eventId);
        }
        CalendarEventDto updated = mapEvent(p, existing);
        return blockDb(() -> {
            repository.save(updated, userId);
            return okJson(
                    "updated", Map.of("eventId", eventId, "event", repository.findByIdAndUserId(eventId, userId)));
        });
    }

    private String doGet(JsonNode p, String userId) {
        String eventId = requireEventId(p);
        CalendarEventDto event = blockDb(() -> repository.findByIdAndUserId(eventId, userId));
        if (event == null) {
            return errorJson("事件不存在或无权访问: " + eventId);
        }
        return okJson("found", Map.of("event", event));
    }

    private String doDelete(JsonNode p, String userId) {
        String eventId = requireEventId(p);
        boolean removed = blockDb(() -> repository.deleteByIdAndUserId(eventId, userId));
        if (!removed) {
            return errorJson("事件不存在或无权访问: " + eventId);
        }
        return okJson("deleted", Map.of("eventId", eventId));
    }

    private String doList(JsonNode p, String userId) {
        List<CalendarEventDto> list = blockDb(() -> {
            JsonNode from = p.get("startFrom");
            JsonNode to = p.get("startTo");
            if (from != null && !from.isNull() && to != null && !to.isNull()) {
                return repository.findByUserIdAndTimeRange(
                        userId, parseInstant(from.asText()), parseInstant(to.asText()));
            }
            return repository.findByUserId(userId);
        });
        return okJson("listed", Map.of("count", list.size(), "events", list));
    }

    private String doExportIcal(JsonNode p, String userId) {
        List<CalendarEventDto> events = blockDb(() -> {
            JsonNode idNode = p.get("eventId");
            if (idNode != null && !idNode.isNull()) {
                CalendarEventDto one = repository.findByIdAndUserId(idNode.asText(), userId);
                return one == null ? List.<CalendarEventDto>of() : List.of(one);
            }
            return repository.findByUserId(userId);
        });
        String ical = toIcal(events);
        return okJson("exported", Map.of("count", events.size(), "ical", ical));
    }

    /** 将 JSON 参数映射为事件 DTO（eventId 已存在时保留原 id/时间戳）。 */
    private CalendarEventDto mapEvent(JsonNode p, CalendarEventDto existing) {
        String id = existing != null ? existing.id() : (p.has("eventId") ? textOrNull(p, "eventId") : null);
        String title = textOrDefault(p, "title", existing != null ? existing.title() : "");
        String description = p.has("description")
                ? textOrNull(p, "description")
                : (existing != null ? existing.description() : null);
        Instant start = p.has("start") && !p.get("start").isNull()
                ? parseInstant(p.get("start").asText())
                : (existing != null ? existing.start() : Instant.now());
        Instant end = p.has("end") && !p.get("end").isNull()
                ? parseInstant(p.get("end").asText())
                : null;
        boolean allDay = p.has("allDay") ? p.get("allDay").asBoolean(false) : (existing != null && existing.allDay());
        int reminder = p.has("reminderMinutes") && !p.get("reminderMinutes").isNull()
                ? p.get("reminderMinutes").asInt(0)
                : (existing != null ? existing.reminderMinutes() : 0);
        List<String> attendees = p.has("attendees") && p.get("attendees").isArray()
                ? streamArray(p.get("attendees"))
                : (existing != null ? existing.attendees() : List.of());
        String location =
                p.has("location") ? textOrNull(p, "location") : (existing != null ? existing.location() : null);
        long createdAt = existing != null ? existing.createdAt() : System.currentTimeMillis();
        long updatedAt = System.currentTimeMillis();
        return new CalendarEventDto(
                id, title, description, start, end, allDay, reminder, attendees, location, createdAt, updatedAt);
    }

    /** 解析 ISO-8601 时间字符串为 Instant（UTC）。无偏移裸时间按 UTC 解释。 */
    static Instant parseInstant(String s) {
        if (s == null || s.isBlank()) {
            return Instant.now();
        }
        try {
            return OffsetDateTime.parse(s).toInstant();
        } catch (DateTimeParseException e1) {
            try {
                return OffsetDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        .toInstant();
            } catch (DateTimeParseException e2) {
                // 无时区裸时间（如 2026-08-16T15:00:00 / 2026-08-16T15:00）→ 按 UTC
                try {
                    return java.time.LocalDateTime.parse(s).toInstant(ZoneOffset.UTC);
                } catch (DateTimeParseException e3) {
                    try {
                        return java.time.LocalDate.parse(s)
                                .atStartOfDay(ZoneOffset.UTC)
                                .toInstant();
                    } catch (DateTimeParseException e4) {
                        return Instant.now();
                    }
                }
            }
        }
    }

    /** 生成 RFC 5545 iCal 文本。 */
    private static String toIcal(List<CalendarEventDto> events) {
        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//ai-copilot//CalendarTool//EN\r\n");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
        for (CalendarEventDto e : events) {
            sb.append("BEGIN:VEVENT\r\n");
            sb.append("UID:").append(e.id()).append("\r\n");
            sb.append("SUMMARY:").append(e.title() == null ? "" : e.title()).append("\r\n");
            if (e.description() != null) {
                sb.append("DESCRIPTION:").append(e.description()).append("\r\n");
            }
            if (e.location() != null) {
                sb.append("LOCATION:").append(e.location()).append("\r\n");
            }
            if (e.start() != null) {
                sb.append("DTSTART:").append(fmt.format(e.start())).append("\r\n");
            }
            if (e.end() != null) {
                sb.append("DTEND:").append(fmt.format(e.end())).append("\r\n");
            }
            if (e.reminderMinutes() > 0 && e.start() != null) {
                Instant trigger = e.start().minusSeconds((long) e.reminderMinutes() * 60);
                sb.append("BEGIN:VALARM\r\nACTION:DISPLAY\r\nDESCRIPTION:Reminder\r\nTRIGGER:-PT")
                        .append(e.reminderMinutes())
                        .append("M\r\nEND:VALARM\r\n");
            }
            for (String a : e.attendees()) {
                sb.append("ATTENDEE:").append(a).append("\r\n");
            }
            sb.append("END:VEVENT\r\n");
        }
        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    private static String resolveUserId(ToolContext toolContext) {
        Object o = toolContext.getContext().get(ToolEventEmitter.CTX_USER_ID);
        if (o instanceof String uid && !uid.isBlank()) {
            return uid;
        }
        throw new IllegalStateException("ToolContext 中缺少 userId（服务端身份），拒绝执行日历工具");
    }

    private static String normalizeAction(String action) {
        return action == null ? "" : action.trim().toUpperCase();
    }

    private static String requireEventId(JsonNode p) {
        if (p.has("eventId") && !p.get("eventId").isNull()) {
            return p.get("eventId").asText();
        }
        throw new IllegalArgumentException("缺少必填参数 eventId");
    }

    /** 在 boundedElastic 线程池执行阻塞 JDBC 调用，避免阻塞 Netty EventLoop。 */
    private <T> T blockDb(java.util.function.Supplier<T> blockingOp) {
        return reactor.core.publisher.Mono.fromCallable(blockingOp::get)
                .subscribeOn(Schedulers.boundedElastic())
                .block();
    }

    private static JsonNode parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return MAPPER.createObjectNode();
        }
        try {
            return MAPPER.readTree(payloadJson);
        } catch (Exception e) {
            return MAPPER.createObjectNode();
        }
    }

    private static List<String> streamArray(JsonNode arr) {
        List<String> out = new java.util.ArrayList<>();
        arr.forEach(n -> out.add(n.asText()));
        return out;
    }

    private static String textOrDefault(JsonNode p, String key, String dflt) {
        JsonNode n = p.get(key);
        return (n == null || n.isNull()) ? dflt : n.asText();
    }

    private static String textOrNull(JsonNode p, String key) {
        JsonNode n = p.get(key);
        return (n == null || n.isNull()) ? null : n.asText();
    }

    private static String okJson(String action, Map<String, Object> data) {
        Map<String, Object> wrap = new java.util.LinkedHashMap<>();
        wrap.put("status", "success");
        wrap.put("action", action);
        wrap.putAll(data);
        return toJson(wrap);
    }

    private static String errorJson(String msg) {
        return toJson(Map.of("status", "error", "message", msg));
    }

    private static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"status\":\"error\",\"message\":\"序列化失败\"}";
        }
    }
}
