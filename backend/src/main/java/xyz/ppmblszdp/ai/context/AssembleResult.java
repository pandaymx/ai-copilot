package xyz.ppmblszdp.ai.context;

import jakarta.annotation.Nullable;
import java.util.List;
import org.springframework.ai.chat.messages.Message;

/**
 * 上下文组装结果封装。
 *
 * @param messages            组装后直接用于模型调用的 Spring AI Message 列表
 * @param compressionMetadata 上下文压缩元数据（若触发了压缩或降级，否则为 null）
 */
public record AssembleResult(
        List<Message> messages, @Nullable CompressionMetadata compressionMetadata) {

    public AssembleResult(List<Message> messages) {
        this(messages, null);
    }

    public boolean hasCompression() {
        return compressionMetadata != null;
    }
}
