package xyz.ppmblszdp.ai.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI Prompt 模板数据传输对象（PromptTemplateDto）。
 */
public record PromptTemplateDto(
        String id,
        String userId,
        String title,
        String description,
        String category,
        String body,
        List<String> variables,
        int rating,
        boolean favorite,
        boolean isSystem,
        long createdAt,
        long updatedAt) {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{([a-zA-Z0-9_\\-]+)\\}\\}");

    /**
     * 自动从模板正文提取 {{var}} 插槽变量名列表。
     */
    public static List<String> extractVariables(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        List<String> vars = new ArrayList<>();
        Matcher m = VAR_PATTERN.matcher(body);
        while (m.find()) {
            String v = m.group(1);
            if (!vars.contains(v)) {
                vars.add(v);
            }
        }
        return vars;
    }
}
