package xyz.ppmblszdp.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 跨会话上下文继承核心 DTO 集合。
 */
public final class ContextInheritanceDto {

    private ContextInheritanceDto() {}

    /**
     * 关键决策条目
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record KeyDecision(String decision, String rationale, String category, Long timestamp) {}

    /**
     * 核心代码片段
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CodeSnippet(String language, String code, String description, String filePath) {}

    /**
     * 文件与文档引用
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FileReference(String fileName, String fileType, String description, String referenceUrl) {}

    /**
     * 未决问题与待跟进事项
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PendingQuestion(String question, String context, String priority) {}

    /**
     * 实体关系
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EntityRelation(String subject, String relation, String object, String description) {}

    /**
     * 导出的结构化继承上下文
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InheritedContext(
            String sourceSessionId,
            String sourceSessionTitle,
            String contextSummary,
            List<KeyDecision> keyDecisions,
            List<CodeSnippet> codeSnippets,
            List<FileReference> fileReferences,
            List<PendingQuestion> pendingQuestions,
            List<EntityRelation> entityRelations,
            long exportedAt,
            int estimatedTokens,
            String extractionMode // "LLM" 或 "RULE_FALLBACK"
            ) {}

    /**
     * 导入上下文请求
     */
    public record ImportContextRequest(
            InheritedContext context,
            List<String> selectedModules, // e.g. ["summary", "decisions", "code", "files", "questions", "entities"]
            String customNote,
            String targetTitle) {}

    /**
     * 导入上下文响应
     */
    public record ImportContextResponse(
            boolean success,
            String targetSessionId,
            String targetTitle,
            List<String> importedModules,
            String formattedContextPreview,
            long importedAt) {}
}
