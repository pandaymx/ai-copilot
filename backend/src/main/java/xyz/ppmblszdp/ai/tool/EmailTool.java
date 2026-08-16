package xyz.ppmblszdp.ai.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import xyz.ppmblszdp.ai.repository.EmailRepository;
import xyz.ppmblszdp.ai.tool.email.EmailDto;
import xyz.ppmblszdp.ai.tool.email.EmailSecurityGate;
import xyz.ppmblszdp.ai.tool.email.MailSenderClient;

/**
 * 邮件工具（EmailTool）：让 AI Agent 具备代发邮件、草拟邮件与查询发件历史的能力。
 */
@Component
public class EmailTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final EmailRepository repository;
    private final EmailSecurityGate securityGate;
    private final MailSenderClient mailSender;

    public EmailTool(EmailRepository repository, EmailSecurityGate securityGate, MailSenderClient mailSender) {
        this.repository = repository;
        this.securityGate = securityGate;
        this.mailSender = mailSender;
    }

    @Tool(
            description = "邮件工具（email_tool）：支持发送邮件(SEND)、草拟邮件(DRAFT)与查询发送历史(LIST_HISTORY)。"
                    + "SEND 操作直接发送邮件并记录到发送历史；DRAFT 操作生成邮件草稿供用户预览与确认；"
                    + "LIST_HISTORY 查询当前用户的外发历史。")
    public String emailTool(
            @ToolParam(description = "操作类型：SEND | DRAFT | LIST_HISTORY") String action,
            @ToolParam(
                            description =
                                    "操作参数 JSON 字符串。SEND/DRAFT 字段: to(邮箱字符串数组), subject(主题), body(正文), isHtml(布尔,可选)。"
                                            + "LIST_HISTORY 字段: limit(数字,可选,默认20)。")
                    String payloadJson,
            ToolContext toolContext) {

        String argsJson = toJson(Map.of(
                "action", action != null ? action : "SEND", "payload", payloadJson != null ? payloadJson : "{}"));

        return ToolEventEmitter.from(toolContext).executeWithEvent("email_tool", argsJson, toolContext, () -> {
            String userId = (String) toolContext.getContext().get(ToolEventEmitter.CTX_USER_ID);
            String effectiveUser = (userId != null && !userId.isBlank()) ? userId : "default";

            String act = action != null ? action.trim().toUpperCase() : "SEND";
            JsonNode payload = parseJson(payloadJson);

            switch (act) {
                case "DRAFT" -> {
                    List<String> to = extractToList(payload);
                    String subject = payload.path("subject").asText("");
                    String body = payload.path("body").asText("");
                    boolean isHtml = payload.path("isHtml").asBoolean(false);

                    securityGate.validate(to, subject, body);

                    var draftResult = new EmailDto.EmailSendResult(
                            "draft_" + System.currentTimeMillis(),
                            to,
                            subject,
                            "DRAFT",
                            body.length() > 200 ? body.substring(0, 200) + "..." : body,
                            System.currentTimeMillis());

                    return toJson(Map.of("success", true, "action", "DRAFT", "draft", draftResult));
                }
                case "SEND" -> {
                    List<String> to = extractToList(payload);
                    String subject = payload.path("subject").asText("");
                    String body = payload.path("body").asText("");
                    boolean isHtml = payload.path("isHtml").asBoolean(false);

                    securityGate.validate(to, subject, body);

                    String msgId = mailSender.send(to, subject, body, isHtml);
                    long now = System.currentTimeMillis();

                    String snippet = body.length() > 200 ? body.substring(0, 200) + "..." : body;
                    var historyItem = new EmailDto.EmailHistoryItem(
                            msgId, effectiveUser, to, subject, snippet, isHtml, "SENT", now);
                    repository.save(historyItem);

                    var sendResult = new EmailDto.EmailSendResult(msgId, to, subject, "SENT", snippet, now);
                    return toJson(Map.of("success", true, "action", "SEND", "result", sendResult));
                }
                case "LIST_HISTORY" -> {
                    int limit = payload.path("limit").asInt(20);
                    List<EmailDto.EmailHistoryItem> history = repository.findByUserId(effectiveUser, limit);
                    return toJson(Map.of("success", true, "action", "LIST_HISTORY", "history", history));
                }
                default -> {
                    throw new IllegalArgumentException("未知的邮件操作类型: " + action + "，支持 SEND | DRAFT | LIST_HISTORY");
                }
            }
        });
    }

    private List<String> extractToList(JsonNode payload) {
        List<String> list = new ArrayList<>();
        JsonNode toNode = payload.path("to");
        if (toNode.isArray()) {
            for (JsonNode n : toNode) {
                if (n.isTextual() && !n.asText().isBlank()) {
                    list.add(n.asText().trim());
                }
            }
        } else if (toNode.isTextual() && !toNode.asText().isBlank()) {
            list.add(toNode.asText().trim());
        }
        return list;
    }

    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) {
            return MAPPER.createObjectNode();
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("无效的参数 JSON 格式: " + e.getMessage());
        }
    }

    private String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
