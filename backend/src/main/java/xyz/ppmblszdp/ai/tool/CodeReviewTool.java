package xyz.ppmblszdp.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import xyz.ppmblszdp.ai.service.CodeReviewService;
import xyz.ppmblszdp.ai.service.CodeReviewService.ReviewRequest;
import xyz.ppmblszdp.ai.service.CodeReviewService.WorkspaceContext;
import xyz.ppmblszdp.ai.tool.dto.CodeReviewDto.CodeReviewReport;

/**
 * 代码审查工具：供 Agent 自主调用，对代码片段 / Git diff / 工作区文件进行多维度审查，
 * 输出结构化分级报告（critical / warning / suggestion），并给出建议测试点。
 *
 * <p>与 {@link CodeExecutionTool} 的联动保持 ReAct 范式：本工具仅产出报告与
 * {@code suggestedTests}，由 Agent 自行决策是否再调用 {@code code_execution} 生成并执行测试，
 * 不在工具内部写死联动逻辑。
 *
 * <p>SSE 帧发送沿用 {@link ToolEventEmitter#executeWithEvent}，异常不冒泡中断聊天流。
 */
@Component
@Profile("!disable-tools")
public class CodeReviewTool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CodeReviewService codeReviewService;

    public CodeReviewTool(CodeReviewService codeReviewService) {
        this.codeReviewService = codeReviewService;
    }

    @Tool(
            description = "对代码片段、Git Diff 或工作区文件进行 AI 代码审查。审查维度：安全漏洞、性能问题、代码风格、最佳实践、复杂度。"
                    + "输出结构化报告（critical/warning/suggestion 分级），并给出建议测试点。需要时可使用 code_execution 工具执行生成的测试。")
    public String codeReview(
            @ToolParam(description = "待审查的代码片段文本（与 gitDiff / filePath 至少提供其一）") String codeSnippet,
            @ToolParam(description = "待审查的 Git Diff 文本（行号应对应变更后新文件）") String gitDiff,
            @ToolParam(description = "工作区内相对文件路径；仅提供时将从沙箱工作区读取该文件内容") String filePath,
            @ToolParam(description = "代码语言，如 java / python / typescript（可选）") String language,
            @ToolParam(description = "用于报告中标识的相对路径/文件名（可选）") String relativePath,
            @ToolParam(description = "审查范围描述，如 '仅审查认证模块'（可选）") String scope,
            @ToolParam(description = "会话工作区标识 workspaceId（filePath 读取时用于定位沙箱根目录，可选）") String workspaceId,
            @ToolParam(description = "工作区 Git 仓库地址（可选，用于定位沙箱根目录）") String gitUrl,
            ToolContext toolContext) {

        String argsJson;
        try {
            var args = new java.util.LinkedHashMap<String, Object>();
            args.put("language", language);
            args.put("relativePath", relativePath);
            args.put("scope", scope);
            args.put("filePath", filePath);
            argsJson = OBJECT_MAPPER.writeValueAsString(args);
        } catch (Exception e) {
            argsJson = "{}";
        }

        return ToolEventEmitter.from(toolContext).executeWithEvent("code_review", argsJson, toolContext, () -> {
            WorkspaceContext wsCtx =
                    (workspaceId != null || gitUrl != null) ? new WorkspaceContext(workspaceId, gitUrl) : null;
            ReviewRequest req = new ReviewRequest(codeSnippet, gitDiff, filePath, language, relativePath, scope);
            CodeReviewReport report = codeReviewService.review(req, wsCtx);
            try {
                return OBJECT_MAPPER.writeValueAsString(report);
            } catch (Exception e) {
                return "{\"summary\":\"审查结果序列化失败\",\"findings\":[],\"suggestedTests\":[]}";
            }
        });
    }
}
