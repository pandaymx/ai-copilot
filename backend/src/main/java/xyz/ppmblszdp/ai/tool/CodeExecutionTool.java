package xyz.ppmblszdp.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import xyz.ppmblszdp.ai.service.CodeExecutionService;

/**
 * 代码执行沙箱工具（Code Interpreter Tool）：
 * 供 Spring AI Agent 在隔离容器/沙箱中执行 Python / JavaScript 代码，支持数据科学分析与 Matplotlib 图表自动捕获。
 */
@Component
public class CodeExecutionTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final CodeExecutionService codeExecutionService;

    public CodeExecutionTool(CodeExecutionService codeExecutionService) {
        this.codeExecutionService = codeExecutionService;
    }

    @Tool(
            description =
                    "代码执行沙箱（Code Interpreter）：在安全隔离的无头 Linux 容器中执行 Python 或 JavaScript 代码。适用于复杂数学计算、算法推导、数据统计与图表绘制。"
                            + "环境约定：严禁调用阻塞式 GUI 方法（如 plt.show() 或 cv2.imshow()）；若生成图表请直接保存为当前工作区图片（例如 plt.savefig('plot.png')），系统将自动捕获图表并渲染给用户。")
    public String executeCode(
            @ToolParam(description = "编程语言，支持 'python'（默认）或 'javascript'") String language,
            @ToolParam(description = "待执行的完整代码。请确保逻辑完整，若生成图表直接保存为当前目录下的图片文件（例如 plt.savefig('output.png')）。")
                    String code,
            ToolContext toolContext) {

        String argsJson =
                toJson(Map.of("language", language == null ? "python" : language, "code", code == null ? "" : code));

        return ToolEventEmitter.from(toolContext).executeWithEvent("code_execution", argsJson, toolContext, () -> {
            CodeExecutionService.ExecutionResponse response = codeExecutionService.execute(language, code);
            return response.toJson();
        });
    }

    private static String toJson(Map<String, Object> map) {
        try {
            return MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }
}
