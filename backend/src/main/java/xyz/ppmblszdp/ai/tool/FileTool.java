package xyz.ppmblszdp.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 文件读写工具：限定在后端安全临时目录 {@code <java.io.tmpdir>/agent-files/<userId>/} 内。
 *
 * <h3>Safe Directory 加固（回应路径穿越隐患）</h3>
 * 通过 {@code userDir.normalize().toAbsolutePath()} 规范化根目录，子路径经 {@code resolve(child).normalize()}
 * 后再严格 {@code startsWith(safeRoot)} 校验，杜绝 {@code ../} 等穿越攻击，确保不会写入约定目录之外。
 */
@Component
public class FileTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long MAX_READ_BYTES = 1_048_576; // 1MB
    private static final long MAX_WRITE_BYTES = 524_288; // 512KB

    @Tool(description = "读取安全目录内的文本文件内容，路径限定在 agent-files/<userId>/ 下")
    public String fileRead(
            @ToolParam(description = "相对路径，如 notes/todo.txt，不可包含 ../ 穿越") String path, ToolContext toolContext) {
        String argsJson = toJson("path", path);
        return ToolEventEmitter.from(toolContext).executeWithEvent("file_read", argsJson, toolContext, () -> {
            Path file = resolveSafe(toolContext, path);
            if (!Files.exists(file)) {
                throw new IllegalArgumentException("文件不存在: " + path);
            }
            try {
                if (Files.size(file) > MAX_READ_BYTES) {
                    throw new IllegalArgumentException("文件超过读取上限 1MB");
                }
                String content = Files.readString(file, StandardCharsets.UTF_8);
                return "{\"output\":" + MAPPER.writeValueAsString(content) + "}";
            } catch (IOException e) {
                throw new RuntimeException("读取文件失败: " + e.getMessage(), e);
            }
        });
    }

    @Tool(description = "向安全目录写入文本文件（自动创建父目录），路径限定在 agent-files/<userId>/ 下")
    public String fileWrite(
            @ToolParam(description = "相对路径，如 notes/todo.txt，不可包含 ../ 穿越") String path,
            @ToolParam(description = "要写入的文本内容") String content,
            ToolContext toolContext) {
        String argsJson = toJson("path", path);
        return ToolEventEmitter.from(toolContext).executeWithEvent("file_write", argsJson, toolContext, () -> {
            Path file = resolveSafe(toolContext, path);
            String text = content == null ? "" : content;
            if (text.getBytes(StandardCharsets.UTF_8).length > MAX_WRITE_BYTES) {
                throw new IllegalArgumentException("写入内容超过上限 512KB");
            }
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, text, StandardCharsets.UTF_8);
                return "{\"output\":\"已写入 " + text.length() + " 字符到 " + path + "\"}";
            } catch (IOException e) {
                throw new RuntimeException("写入文件失败: " + e.getMessage(), e);
            }
        });
    }

    /** 路径穿越防护：规范化根目录后解析子路径并二次校验前缀。 */
    private Path resolveSafe(ToolContext toolContext, String relativePath) {
        String userId = (String) toolContext.getContext().get(ToolEventEmitter.CTX_USER_ID);
        if (userId == null || userId.isBlank()) userId = "anonymous";
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("路径不能为空");
        }
        Path safeRoot = Paths.get(System.getProperty("java.io.tmpdir"), "agent-files", userId)
                .normalize()
                .toAbsolutePath();
        Path resolved = safeRoot.resolve(relativePath).normalize().toAbsolutePath();
        if (!resolved.startsWith(safeRoot)) {
            throw new SecurityException("非法路径穿越尝试被拒绝: " + relativePath);
        }
        return resolved;
    }

    private static String toJson(String key, String value) {
        try {
            return MAPPER.writeValueAsString(Map.of(key, value == null ? "" : value));
        } catch (Exception e) {
            return "{\"" + key + "\":\"\"}";
        }
    }
}
