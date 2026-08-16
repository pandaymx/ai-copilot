package xyz.ppmblszdp.ai.dto;

import java.util.List;
import java.util.Map;

/**
 * AI 内容生成模板数据传输对象。
 */
public class ContentTemplateDto {

    public record TemplateField(String name, String label, String placeholder, boolean required, String type) {}

    public record ContentTemplateMetadata(
            String id, String name, String description, String category, String icon, List<TemplateField> fields) {}

    public record GenerateContentRequest(
            String templateId, String title, Map<String, String> inputs, String customPrompt) {}

    public record GenerateContentResponse(
            String id,
            String templateId,
            String title,
            String markdownContent,
            Map<String, Object> structuredSections,
            long createdAt) {}

    public record ContentGenerationHistoryItem(
            String id, String userId, String templateId, String title, String markdownContent, long createdAt) {}
}
