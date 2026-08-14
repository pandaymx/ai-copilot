package xyz.ppmblszdp.ai.reflection;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;

/**
 * AI 自我反思与纠错核心判定引擎。
 * 负责在模型回答后执行事实一致性、逻辑完整性与幻觉自检。
 */
@Service
public class ReflectionEngine {

    private static final Logger log = LoggerFactory.getLogger(ReflectionEngine.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern JSON_BLOCK_PATTERN =
            Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);

    private static final String REFLECTION_SYSTEM_PROMPT = """
			你是一个具备极强自省与批判性思维的高级 AI 自我反思审核器（Self-Reflection Evaluator）。
			请审查【用户问题 (Prompt)】以及【AI 生成的回答 (Answer)】，并参考可能存在的【背景上下文 (Context)】。

			请从以下维度进行快速自省检查：
			1. 事实自洽性 (Factuality): 是否存在明显的技术常识性错误、捏造不存在的 API/参数或与 Context 发生事实矛盾？
			2. 逻辑完整性 (Completeness): 是否遗漏了用户 Prompt 中的关键硬性约束（如指定的输出语言、特定结构或边界条件）？
			3. 判定标准: 若回答总体准确无硬伤，则 passed 必须为 true；若存在技术事实错误或重大遗漏，passed 为 false。

			请严格输出标准 JSON 格式：
			{
			  "passed": true 或 false,
			  "factualityScore": 0.0 ~ 1.0,
			  "completenessScore": 0.0 ~ 1.0,
			  "issues": ["简短问题1", "简短问题2"],
			  "correctionExplanation": "为什么需要纠偏（若 passed 为 true 可为空）",
			  "supplementalCorrection": "精确的修正与补充说明文本（若 passed 为 true 可为空）"
			}
			""";

    private final ProviderRegistry providerRegistry;
    private final ReflectionProperties properties;

    public ReflectionEngine(ProviderRegistry providerRegistry, ReflectionProperties properties) {
        this.providerRegistry = providerRegistry;
        this.properties = properties;
    }

    /**
     * 对生成的回答进行自我反思与质量自检。
     *
     * @param userPrompt 用户原始提问
     * @param assistantReply AI 助手生成的初步回答
     * @param context 关联的参考上下文（如 RAG 检索结果，可为空）
     * @return 评估与纠错结果
     */
    public ReflectionAssessment evaluate(String userPrompt, String assistantReply, String context) {
        if (!properties.isEnabled()) {
            return ReflectionAssessment.ofPassed();
        }

        if (assistantReply == null
                || assistantReply.isBlank()
                || assistantReply.length() < properties.getMinContentLength()) {
            return ReflectionAssessment.ofPassed();
        }

        try {
            ResolvedModel resolved = providerRegistry.resolve(null, null);
            ChatClient client = resolved.chatClient();

            String userContent = "【用户问题 (Prompt)】:\n" + userPrompt + "\n\n"
                    + "【背景上下文 (Context)】:\n" + (context != null && !context.isBlank() ? context : "无") + "\n\n"
                    + "【AI 生成的回答 (Answer)】:\n" + assistantReply;

            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> client.prompt()
                    .system(REFLECTION_SYSTEM_PROMPT)
                    .user(userContent)
                    .call()
                    .content());

            String rawJson = future.get(properties.getTimeoutMs(), TimeUnit.MILLISECONDS);
            ReflectionPayload payload = parsePayload(rawJson);

            if (payload == null) {
                return ReflectionAssessment.ofPassed();
            }

            if (Boolean.FALSE.equals(payload.passed)) {
                log.info(
                        "🔍 [ReflectionEngine] 触发自我纠错: issues={}, explanation={}",
                        payload.issues,
                        payload.correctionExplanation);
                return ReflectionAssessment.needsCorrection(
                        payload.factualityScore,
                        payload.completenessScore,
                        payload.issues,
                        payload.correctionExplanation,
                        payload.supplementalCorrection);
            }

            return ReflectionAssessment.ofPassed();
        } catch (Exception e) {
            log.debug("自我反思流程跳过或超时 (fail-safe): {}", e.getMessage());
            return ReflectionAssessment.ofPassed();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ReflectionPayload {
        @JsonProperty("passed")
        public Boolean passed;

        @JsonProperty("factualityScore")
        public Double factualityScore;

        @JsonProperty("completenessScore")
        public Double completenessScore;

        @JsonProperty("issues")
        public List<String> issues;

        @JsonProperty("correctionExplanation")
        public String correctionExplanation;

        @JsonProperty("supplementalCorrection")
        public String supplementalCorrection;
    }

    private ReflectionPayload parsePayload(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            String clean = extractJson(raw);
            return MAPPER.readValue(clean, ReflectionPayload.class);
        } catch (Exception e) {
            log.warn("解析自我反思 JSON 失败，回退默认通过: {}", e.getMessage());
            return null;
        }
    }

    private String extractJson(String raw) {
        Matcher m = JSON_BLOCK_PATTERN.matcher(raw);
        if (m.find()) {
            return m.group(1).trim();
        }
        int start = raw.indexOf("{");
        int end = raw.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1).trim();
        }
        return raw.trim();
    }
}
