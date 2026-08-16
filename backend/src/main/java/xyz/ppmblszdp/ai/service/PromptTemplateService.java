package xyz.ppmblszdp.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import xyz.ppmblszdp.ai.dto.PromptTemplateDto;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;
import xyz.ppmblszdp.ai.repository.PromptTemplateRepository;
import xyz.ppmblszdp.ai.repository.PromptTemplateRepository.PromptTemplateEntity;

/**
 * Prompt 模板业务逻辑服务（PromptTemplateService）。
 */
@Service
public class PromptTemplateService {

    private static final Logger log = LoggerFactory.getLogger(PromptTemplateService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{([a-zA-Z0-9_\\-]+)\\}\\}");

    private final PromptTemplateRepository repository;
    private final ProviderRegistry providerRegistry;

    public PromptTemplateService(PromptTemplateRepository repository, ProviderRegistry providerRegistry) {
        this.repository = repository;
        this.providerRegistry = providerRegistry;
    }

    public List<PromptTemplateDto> list(String userId, String category, String keyword) {
        return repository.findAllByUser(userId, category, keyword).stream()
                .map(this::toDto)
                .toList();
    }

    public PromptTemplateDto get(String id, String userId) {
        return repository
                .findById(id, userId)
                .map(this::toDto)
                .orElseThrow(() -> new IllegalArgumentException("未找到指定的 Prompt 模板: " + id));
    }

    public String create(PromptTemplateDto dto, String userId) {
        long now = System.currentTimeMillis();
        String id = (dto.id() != null && !dto.id().isBlank())
                ? dto.id()
                : "tpl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        PromptTemplateEntity entity = new PromptTemplateEntity(
                id,
                userId,
                dto.title() != null ? dto.title().trim() : "新建模板",
                dto.description() != null ? dto.description().trim() : "",
                dto.category() != null && !dto.category().isBlank()
                        ? dto.category().trim().toLowerCase()
                        : "general",
                dto.body() != null ? dto.body() : "",
                dto.rating() > 0 ? dto.rating() : 5,
                dto.favorite(),
                false,
                now,
                now);

        repository.insert(entity);
        log.info("创建 Prompt 模板成功: id={}, userId={}, title={}", id, userId, entity.title());
        return id;
    }

    public void update(PromptTemplateDto dto, String userId) {
        PromptTemplateEntity existing = repository
                .findById(dto.id(), userId)
                .orElseThrow(() -> new IllegalArgumentException("模板不存在: " + dto.id()));

        if (existing.isSystem()) {
            throw new IllegalStateException("系统内置模板只读，不可直接修改");
        }

        long now = System.currentTimeMillis();
        PromptTemplateEntity updated = new PromptTemplateEntity(
                dto.id(),
                userId,
                dto.title() != null ? dto.title().trim() : existing.title(),
                dto.description() != null ? dto.description().trim() : existing.description(),
                dto.category() != null ? dto.category().trim().toLowerCase() : existing.category(),
                dto.body() != null ? dto.body() : existing.body(),
                dto.rating() > 0 ? dto.rating() : existing.rating(),
                dto.favorite(),
                false,
                existing.createdAt(),
                now);

        repository.update(updated);
        log.info("更新 Prompt 模板: id={}, userId={}", dto.id(), userId);
    }

    public void delete(String id, String userId) {
        repository.delete(id, userId);
        log.info("删除 Prompt 模板: id={}, userId={}", id, userId);
    }

    public void toggleFavorite(String id, String userId) {
        repository.toggleFavorite(id, userId);
    }

    public void rate(String id, String userId, int rating) {
        repository.updateRating(id, userId, rating);
    }

    /**
     * 将模板中的 {{variable}} 插槽占位符替换为用户输入的变量值。
     */
    public String render(String id, String userId, Map<String, String> variables) {
        PromptTemplateDto dto = get(id, userId);
        return renderText(dto.body(), variables);
    }

    public static String renderText(String templateText, Map<String, String> variables) {
        if (templateText == null || templateText.isBlank()) {
            return "";
        }
        if (variables == null || variables.isEmpty()) {
            return templateText;
        }

        Matcher matcher = VAR_PATTERN.matcher(templateText);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String val = variables.get(key);
            if (val != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(val));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 智能填充：根据给定的上下文或自然语言描述，由 LLM 自动提取并建议各变量插槽的取值。
     */
    public Map<String, String> smartFill(String id, String userId, String context) {
        PromptTemplateDto dto = get(id, userId);
        List<String> vars = dto.variables();
        if (vars.isEmpty() || context == null || context.isBlank()) {
            return Map.of();
        }

        String prompt = """
                根据用户提供的上下文信息，为以下 Prompt 模板的各个变量提取或推测最恰当的取值。

                【模板标题】%s
                【模板正文】
                %s

                【需要填充的变量名列表】
                %s

                【用户给出的上下文】
                %s

                【输出要求】
                必须且仅返回标准 JSON 对象，键为变量名，值为推测的内容字符串。示例格式：
                {"topic": "微服务架构", "language": "Java"}
                严禁输出任何额外说明或 Markdown 标签！
                """.formatted(dto.title(), dto.body(), vars.toString(), context);

        try {
            ResolvedModel resolved = resolveLowCostModel();
            ChatClient client = resolved.chatClient();
            String res = client.prompt().user(prompt).call().content();

            if (res != null) {
                String clean = res.replaceAll("^```(?:json)?\\s*", "")
                        .replaceAll("\\s*```$", "")
                        .trim();
                return MAPPER.readValue(clean, new TypeReference<Map<String, String>>() {});
            }
        } catch (Exception e) {
            log.warn("智能填充变量异常: {}", e.getMessage());
        }

        return Map.of();
    }

    private ResolvedModel resolveLowCostModel() {
        try {
            return providerRegistry.resolve("deepseek", "deepseek-chat");
        } catch (Exception e) {
            return providerRegistry.resolve(null, null);
        }
    }

    private PromptTemplateDto toDto(PromptTemplateEntity entity) {
        return new PromptTemplateDto(
                entity.id(),
                entity.userId(),
                entity.title(),
                entity.description(),
                entity.category(),
                entity.body(),
                PromptTemplateDto.extractVariables(entity.body()),
                entity.rating(),
                entity.favorite(),
                entity.isSystem(),
                entity.createdAt(),
                entity.updatedAt());
    }
}
