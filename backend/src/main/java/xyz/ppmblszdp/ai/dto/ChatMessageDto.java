package xyz.ppmblszdp.ai.dto;

/**
 * 与前端 {@code SpringAiStreamMessage} 严格对齐的消息结构。
 *
 * @param role    "user" | "assistant" | "system"
 * @param content 消息正文
 */
public record ChatMessageDto(String role, String content) {
    public static ChatMessageDto user(String content) {
        return new ChatMessageDto("user", content);
    }

    public static ChatMessageDto assistant(String content) {
        return new ChatMessageDto("assistant", content);
    }

    public static ChatMessageDto system(String content) {
        return new ChatMessageDto("system", content);
    }
}
