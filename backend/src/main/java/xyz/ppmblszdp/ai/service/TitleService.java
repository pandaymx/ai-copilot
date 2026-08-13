package xyz.ppmblszdp.ai.service;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import xyz.ppmblszdp.ai.factory.ChatOptionsFactory;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;

/**
 * 标题生成服务。
 *
 * <p>根据「用户问题 + AI 回答」调用 LLM 提炼总结出不超过 20 字的精炼中文会话标题。
 * 该调用与主对话链路完全解耦：独立、幂等、无副作用（不写记忆/存储），失败降级返回 {@code null}，
 * 由前端回退到本地截取，绝不向上抛 5xx 影响主对话。
 *
 * <p>可靠性与效果优化：
 * <ul>
 *   <li><b>Few-Shot 少样本提示词</b>：包含明确的反例与规范提炼示例，引导 LLM 概括提炼而非原样照抄提问；</li>
 *   <li><b>适当采样温度 (0.5)</b>：避免 0.2 温度过于贪婪导致直接照抄 Prompt 词汇；</li>
 *   <li><b>严格清洗</b>：去除模型惯用前缀、语气词、Markdown 符号及首尾标点符号。</li>
 * </ul>
 */
@Service
public class TitleService {

    private static final Logger log = LoggerFactory.getLogger(TitleService.class);

    /** 标题生成的硬超时（8s）。 */
    private static final Duration TITLE_TIMEOUT = Duration.ofSeconds(8);

    /** 入参截断长度：提问前 200 字、回答前 500 字已足以提炼主题。 */
    private static final int MAX_QUESTION_CHARS = 200;

    private static final int MAX_ANSWER_CHARS = 500;

    /** 标题硬上限，超出截断。 */
    private static final int MAX_TITLE_CHARS = 25;

    // 去除前缀：标题：/标题:/生成的标题为：/总结：/主题：
    private static final Pattern PREFIX_PATTERN =
            Pattern.compile("^\\s*(标题|主题|总结|概括|简述|会话标题|session title|title)\\s*[:：=]\\s*", Pattern.CASE_INSENSITIVE);

    // 去除疑问及祈使句口语词头：请问/如何/怎么/请帮我/帮我...
    private static final Pattern FILLER_PREFIX_PATTERN = Pattern.compile(
            "^\\s*(请问|如何|怎么|请帮我|帮我|我想|关于|请分析|请写一个|编写一个|写一个|设计一个|对比分析|分析|解析)\\s*", Pattern.CASE_INSENSITIVE);

    // 去除 Markdown 强调：**标题**、__标题__、*标题*、# 标题
    private static final Pattern MARKDOWN_PATTERN = Pattern.compile("^[#*_\\-\\s>]+|[#*_\\-\\s>]+$");

    // 去除首尾引号/书名号/句号/句号等标点
    private static final Pattern WRAP_PATTERN =
            Pattern.compile("^[\\\"'\"'「『（(【\\[。，,.！!]+|[\\\"'\"'」』）)】\\]。，,.！!]+$");

    private final ProviderRegistry registry;

    public TitleService(ProviderRegistry registry) {
        this.registry = registry;
    }

    /**
     * 生成会话标题。
     *
     * @return 清洗后的标题；任何失败/降级场景返回 {@code null}（前端兜底）
     */
    public Mono<String> generateTitle(String userMessage, String answer, String provider, String model) {
        String question = truncate(userMessage, MAX_QUESTION_CHARS);
        String content = truncate(answer, MAX_ANSWER_CHARS);
        if (question.isBlank() && content.isBlank()) {
            return Mono.empty();
        }

        ResolvedModel resolved;
        try {
            resolved = registry.resolve(provider, model);
        } catch (Exception ex) {
            log.warn("标题生成：模型解析失败 → {}", ex.getMessage());
            return Mono.empty();
        }

        ChatOptions options = buildOptions(resolved);
        String userPrompt = "【用户问题】：\n" + question + (content.isBlank() ? "" : "\n\n【AI 回答要点】：\n" + content);

        return Mono.fromCallable(() -> {
                    Prompt prompt =
                            new Prompt(List.of(new SystemMessage(SYSTEM_PROMPT), new UserMessage(userPrompt)), options);
                    ChatResponse resp = resolved.chatModel().call(prompt);
                    if (resp == null
                            || resp.getResult() == null
                            || resp.getResult().getOutput() == null) {
                        return null;
                    }
                    return resp.getResult().getOutput().getText();
                })
                .map(this::cleanTitle)
                .filter(t -> t != null && !t.isBlank())
                .timeout(TITLE_TIMEOUT)
                .onErrorResume(ex -> {
                    log.warn(
                            "标题生成失败 → 供应商={}, 模型={}: {}",
                            resolved.provider().providerId(),
                            resolved.model().id(),
                            ex.getMessage());
                    return Mono.empty();
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 去除思考链、惯用前缀、口语语气词、Markdown 符号并做长度硬截断。 */
    private String cleanTitle(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();

        // 去除思考链 <think>...</think> 残留
        if (t.contains("</think>")) {
            t = t.substring(t.lastIndexOf("</think>") + "</think>".length()).trim();
        }
        int thinkEndIndex = t.lastIndexOf("</think:");
        if (thinkEndIndex >= 0) {
            int closeBracket = t.indexOf(">", thinkEndIndex);
            if (closeBracket >= 0) {
                t = t.substring(closeBracket + 1).trim();
            }
        }

        // 去掉常见前缀与语气头
        t = PREFIX_PATTERN.matcher(t).replaceFirst("");
        t = FILLER_PREFIX_PATTERN.matcher(t).replaceFirst("");

        // 逐层去除 Markdown 强调与包裹符
        for (int i = 0; i < 3; i++) {
            String prev = t;
            t = MARKDOWN_PATTERN.matcher(t).replaceAll("").trim();
            if (t.equals(prev)) {
                break;
            }
        }
        t = WRAP_PATTERN.matcher(t).replaceAll("").trim();

        // 再次清洗语气头（防止前缀清洗后暴露）
        t = FILLER_PREFIX_PATTERN.matcher(t).replaceFirst("").trim();

        // 长度硬截断
        if (t.length() > MAX_TITLE_CHARS) {
            t = t.substring(0, MAX_TITLE_CHARS).trim();
        }
        return t.isBlank() ? null : t;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        String trimmed = s.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    /** 标题生成的采样温度设置为 0.5，赋予模型适度总结与改写能力，防止贪婪原样抄写 Prompt。 */
    private ChatOptions buildOptions(ResolvedModel resolved) {
        return ChatOptionsFactory.forProvider(resolved, 0.5);
    }

    private static final String SYSTEM_PROMPT = """
			你是一个专业的 AI 会话主题提炼与标题生成专家。
			你的唯一任务是：分析【用户问题】和【AI 回答要点】，归纳提炼出一个高度精炼、专业且准确的短标题（4~15 字）。

			【严格遵守以下法则】：
			1. 归纳概括而非抄写：提炼问题涉及的核心技术领域或业务主题，严禁原样照抄用户的整句提问！必须去掉“如何”、“请问”、“怎么”、“请帮我”、“写一个”等口语化短语。
			2. 简明名词短语：使用紧凑的名词短语或主谓短语（例如：“Spring WebFlux SSE 控制器”、“Java 虚拟线程与协程对比”、“React 组件死循环排查”）。
			3. 结合回答要点：综合结合 AI 的解答，确保标题准确描述整个对话的中心议题。
			4. 纯文本输出：只输出标题本身，不要任何前缀（如“标题：”）、标点符号、引号、Markdown 符号或换行说明。

			【少样本示例参照】：
			示例 1
			用户问题：用 Spring Boot 4.x 写一个 Reactive WebFlux SSE 流式控制器
			AI 回答要点：首先引入 spring-boot-starter-webflux，然后编写 SseEmitter 或 Flux<ServerSentEvent>...
			会话标题：Spring WebFlux SSE 控制器

			示例 2
			用户问题：对比分析 Java 25 Virtual Threads 与 Kotlin 协程在 IO 密集场景的差异
			AI 回答要点：Virtual Threads 由 JVM 调度，Kotlin 协程为编译期 CPS 变换...
			会话标题：Java 虚拟线程与协程对比

			示例 3
			用户问题：我的 React 组件为什么每次 state 更新都会死循环重新渲染？
			AI 回答要点：这是因为在 useEffect 中直接调用了 setState 且依赖项设置不当...
			会话标题：React 组件死循环重渲染排查
			""";
}
