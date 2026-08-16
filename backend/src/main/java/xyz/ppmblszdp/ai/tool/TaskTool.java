package xyz.ppmblszdp.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;
import xyz.ppmblszdp.ai.repository.TaskDto;
import xyz.ppmblszdp.ai.repository.TaskRepository;

/**
 * 任务工具（TaskTool）：供 Agent 在对话中创建 / 查询 / 修改 / 分配 / 完成任务。
 *
 * <p>采用单一 {@code @Tool} 入口 + {@code action} 枚举分发（CREATE / GET / UPDATE / ASSIGN /
 * COMPLETE / DELETE / LIST）。任务含优先级（1=最高，4=最低）、截止日期（ISO-8601 UTC）、
 * 标签数组、依赖数组、负责人。
 *
 * <p>多租户隔离：{@code userId} 一律取自 {@link ToolEventEmitter#CTX_USER_ID}（服务端受信任身份）。
 *
 * <p>时区处理：截止日期统一以 {@link Instant}（UTC）存储，入参按 {@link CalendarTool#parseInstant}
 * 规则解析（带偏移换算 UTC，裸时间按 UTC）。
 */
@Component
public class TaskTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final TaskRepository repository;

    public TaskTool(TaskRepository repository) {
        this.repository = repository;
    }

    @Tool(
            description = "任务工具（Task）：管理用户 TODO 任务。支持创建(CREATE)、查询单个(GET)、更新(UPDATE)、"
                    + "分配(ASSIGN)、完成(COMPLETE)、删除(DELETE)、列出(LIST)。"
                    + "状态枚举：TODO | IN_PROGRESS | DONE | BLOCKED（COMPLETE 等价于置为 DONE）。"
                    + "优先级 priority 为整数 1(最高)~4(最低)，默认 3。截止日期 dueDate 使用 ISO-8601 UTC 格式。"
                    + "tags 与 dependencies 为字符串数组。更新/删除/完成/分配/查询单个任务时必须提供 taskId。")
    public String task(
            @ToolParam(description = "操作类型枚举：CREATE | GET | UPDATE | ASSIGN | COMPLETE | DELETE | LIST") String action,
            @ToolParam(
                            description = "操作参数 JSON。通用字段：taskId(UPDATE/ASSIGN/COMPLETE/DELETE/GET 必填)。"
                                    + "CREATE/UPDATE 字段：title, description, status(枚举), priority(1-4),"
                                    + " dueDate(ISO-8601 UTC,可选), tags(字符串数组), assignee(字符串,可选),"
                                    + " dependencies(字符串数组,任务 id 列表)。"
                                    + "ASSIGN 字段：assignee。COMPLETE 无额外字段。"
                                    + "LIST 字段：status(可选枚举过滤), priority(可选整数过滤), tags(可选字符串数组过滤)。")
                    String payloadJson,
            ToolContext toolContext) {

        String argsJson = toJson(Map.of("action", action == null ? "" : action, "payload", payloadJson));
        return ToolEventEmitter.from(toolContext).executeWithEvent("task_tool", argsJson, toolContext, () -> {
            String userId = resolveUserId(toolContext);
            JsonNode payload = parsePayload(payloadJson);
            return switch (normalizeAction(action)) {
                case "CREATE" -> doCreate(payload, userId);
                case "UPDATE" -> doUpdate(payload, userId);
                case "GET" -> doGet(payload, userId);
                case "ASSIGN" -> doAssign(payload, userId);
                case "COMPLETE" -> doComplete(payload, userId);
                case "DELETE" -> doDelete(payload, userId);
                case "LIST" -> doList(payload, userId);
                default -> errorJson("未知 action: " + action);
            };
        });
    }

    private String doCreate(JsonNode p, String userId) {
        TaskDto task = mapTask(p, null);
        return blockDb(() -> {
            String id = repository.save(task, userId);
            return okJson("created", Map.of("taskId", id, "task", repository.findByIdAndUserId(id, userId)));
        });
    }

    private String doUpdate(JsonNode p, String userId) {
        String taskId = requireTaskId(p);
        TaskDto existing = blockDb(() -> repository.findByIdAndUserId(taskId, userId));
        if (existing == null) {
            return errorJson("任务不存在或无权访问: " + taskId);
        }
        TaskDto updated = mapTask(p, existing);
        return blockDb(() -> {
            repository.save(updated, userId);
            return okJson("updated", Map.of("taskId", taskId, "task", repository.findByIdAndUserId(taskId, userId)));
        });
    }

    private String doGet(JsonNode p, String userId) {
        String taskId = requireTaskId(p);
        TaskDto task = blockDb(() -> repository.findByIdAndUserId(taskId, userId));
        if (task == null) {
            return errorJson("任务不存在或无权访问: " + taskId);
        }
        return okJson("found", Map.of("task", task));
    }

    private String doAssign(JsonNode p, String userId) {
        String taskId = requireTaskId(p);
        String assignee = p.has("assignee") && !p.get("assignee").isNull()
                ? p.get("assignee").asText()
                : null;
        if (assignee == null || assignee.isBlank()) {
            return errorJson("ASSIGN 操作缺少 assignee");
        }
        TaskDto existing = blockDb(() -> repository.findByIdAndUserId(taskId, userId));
        if (existing == null) {
            return errorJson("任务不存在或无权访问: " + taskId);
        }
        TaskDto updated = new TaskDto(
                existing.id(),
                existing.title(),
                existing.description(),
                existing.status(),
                existing.priority(),
                existing.dueDate(),
                existing.tags(),
                assignee,
                existing.dependencies(),
                existing.createdAt(),
                System.currentTimeMillis());
        return blockDb(() -> {
            repository.save(updated, userId);
            return okJson(
                    "assigned",
                    Map.of(
                            "taskId",
                            taskId,
                            "assignee",
                            assignee,
                            "task",
                            repository.findByIdAndUserId(taskId, userId)));
        });
    }

    private String doComplete(JsonNode p, String userId) {
        String taskId = requireTaskId(p);
        boolean ok = blockDb(() -> repository.updateStatus(taskId, userId, "DONE"));
        if (!ok) {
            return errorJson("任务不存在或无权访问: " + taskId);
        }
        return okJson(
                "completed",
                Map.of("taskId", taskId, "task", blockDb(() -> repository.findByIdAndUserId(taskId, userId))));
    }

    private String doDelete(JsonNode p, String userId) {
        String taskId = requireTaskId(p);
        boolean removed = blockDb(() -> repository.deleteByIdAndUserId(taskId, userId));
        if (!removed) {
            return errorJson("任务不存在或无权访问: " + taskId);
        }
        return okJson("deleted", Map.of("taskId", taskId));
    }

    private String doList(JsonNode p, String userId) {
        String status =
                p.has("status") && !p.get("status").isNull() ? p.get("status").asText() : null;
        Integer priority = p.has("priority") && !p.get("priority").isNull()
                ? p.get("priority").asInt(0)
                : null;
        List<String> tags = p.has("tags") && p.get("tags").isArray() ? streamArray(p.get("tags")) : null;
        List<TaskDto> list = blockDb(() -> repository.findByUserIdAndFilters(userId, status, priority, tags));
        return okJson("listed", Map.of("count", list.size(), "tasks", list));
    }

    private TaskDto mapTask(JsonNode p, TaskDto existing) {
        String id = existing != null ? existing.id() : (p.has("taskId") ? textOrNull(p, "taskId") : null);
        String title = textOrDefault(p, "title", existing != null ? existing.title() : "");
        String description = p.has("description")
                ? textOrNull(p, "description")
                : (existing != null ? existing.description() : null);
        String status = p.has("status") && !p.get("status").isNull()
                ? p.get("status").asText()
                : (existing != null ? existing.status() : "TODO");
        int priority = p.has("priority") && !p.get("priority").isNull()
                ? p.get("priority").asInt(3)
                : (existing != null ? existing.priority() : 3);
        Instant dueDate = p.has("dueDate") && !p.get("dueDate").isNull()
                ? CalendarTool.parseInstant(p.get("dueDate").asText())
                : (existing != null ? existing.dueDate() : null);
        List<String> tags = p.has("tags") && p.get("tags").isArray()
                ? streamArray(p.get("tags"))
                : (existing != null ? existing.tags() : new ArrayList<>());
        String assignee =
                p.has("assignee") ? textOrNull(p, "assignee") : (existing != null ? existing.assignee() : null);
        List<String> deps = p.has("dependencies") && p.get("dependencies").isArray()
                ? streamArray(p.get("dependencies"))
                : (existing != null ? existing.dependencies() : new ArrayList<>());
        long createdAt = existing != null ? existing.createdAt() : System.currentTimeMillis();
        long updatedAt = System.currentTimeMillis();
        return new TaskDto(
                id, title, description, status, priority, dueDate, tags, assignee, deps, createdAt, updatedAt);
    }

    private static String resolveUserId(ToolContext toolContext) {
        Object o = toolContext.getContext().get(ToolEventEmitter.CTX_USER_ID);
        if (o instanceof String uid && !uid.isBlank()) {
            return uid;
        }
        throw new IllegalStateException("ToolContext 中缺少 userId（服务端身份），拒绝执行任务工具");
    }

    private static String normalizeAction(String action) {
        return action == null ? "" : action.trim().toUpperCase();
    }

    private static String requireTaskId(JsonNode p) {
        if (p.has("taskId") && !p.get("taskId").isNull()) {
            return p.get("taskId").asText();
        }
        throw new IllegalArgumentException("缺少必填参数 taskId");
    }

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
        List<String> out = new ArrayList<>();
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
