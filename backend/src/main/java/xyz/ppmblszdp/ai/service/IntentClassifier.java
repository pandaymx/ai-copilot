package xyz.ppmblszdp.ai.service;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import xyz.ppmblszdp.ai.dto.ChatRequest;
import xyz.ppmblszdp.ai.factory.ChatOptionsFactory;
import xyz.ppmblszdp.ai.intent.IntentResult;
import xyz.ppmblszdp.ai.intent.IntentType;
import xyz.ppmblszdp.ai.registry.ResolvedModel;
import xyz.ppmblszdp.ai.registry.TaskModelRouter;

/**
 * 轻量级意图识别与智能路由服务。
 *
 * <p>短路分类顺序：
 * <ol>
 *   <li>显式斜杠命令 (`/code`, `/translate`, `/write`, `/search`, `/math`, `/image`, `/chat`, `/analysis`)</li>
 *   <li>多模态媒体附件 (`MULTIMODAL`)</li>
 *   <li>预编译正则表达式特征匹配 (`static final Pattern`)</li>
 *   <li>LLM 超时 Fail-safe 智能兜底 (500ms 超时，Temp=0.0，异常自动回落 CHAT)</li>
 * </ol>
 */
@Service
public class IntentClassifier {

    private static final Logger log = LoggerFactory.getLogger(IntentClassifier.class);

    private final TaskModelRouter taskModelRouter;

    public IntentClassifier(TaskModelRouter taskModelRouter) {
        this.taskModelRouter = taskModelRouter;
    }

    private static final Duration LLM_TIMEOUT = Duration.ofMillis(500);

    // 预编译正则表达式，避免每次请求重复编译
    private static final Pattern CODE_PATTERN = Pattern.compile(
            "(?i)(```|public\\s+class|def\\s+\\w+|function\\s+\\w+|class\\s+\\w+|import\\s+[\\w\\.]+|"
                    + "SELECT\\s+.*?\\s+FROM|git\\s+(commit|push|pull|clone)|docker\\s+(run|build)|npm\\s+(install|run)|"
                    + "怎么写代码|写一段代码|代码|报错|重构|Debug|Bug|算法|实现.*功能|用.*写一个|编写.*程序)",
            Pattern.DOTALL);

    private static final Pattern MATH_PATTERN =
            Pattern.compile("(?i)(\\\\frac|\\\\int|\\\\sum|\\\\matrix|\\\\sqrt|\\\\\\(|\\\\\\[|求解|求积分|求导|计算|方程|微分|"
                    + "矩阵|概率|行列式|几何|算术|数学题|\\b[0-9]+[\\+\\-\\*\\/\\=][0-9]+\\b)");

    private static final Pattern TRANSLATION_PATTERN = Pattern.compile(
            "(?i)(翻译[：:]?|translate|to\\s+english|to\\s+chinese|翻成中文|翻成英文|日文怎么说|英文怎么说|韩文怎么说|" + "把.*译成|中译英|英译中)");

    private static final Pattern SEARCH_PATTERN =
            Pattern.compile("(?i)(搜索|查找|搜一下|查一下|查查|最新新闻|实时资讯|天气预报|最新价格|最新消息|2026年|发生了什么|热搜)");

    private static final Pattern WRITING_PATTERN =
            Pattern.compile("(?i)(写一篇|作文|周报|新闻稿|演讲稿|邮件模板|润色|改写|公文|总结|提炼|观后感|邀请函|报告|文章)");

    private static final Pattern ANALYSIS_PATTERN =
            Pattern.compile("(?i)(分析|对比|评估|优缺点|架构设计|可行性|深度解析|归纳|SWOT|权衡|为什么|原因)");

    /**
     * 对聊天请求进行意图分类与智能路由规划。
     */
    public IntentResult classify(ChatRequest request, ResolvedModel resolved) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            return buildDefaultResult(IntentType.CHAT);
        }

        String msg = request.message().trim();

        // 1. 显式斜杠命令短路
        IntentResult commandResult = checkSlashCommands(msg);
        if (commandResult != null) {
            log.debug("意图匹配 → 斜杠命令: {}", commandResult.intent());
            return commandResult;
        }

        // 2. 多模态媒体附件短路
        if (request.media() != null && !request.media().isEmpty()) {
            log.debug("意图匹配 → 多模态媒体附件");
            return buildDefaultResult(IntentType.MULTIMODAL);
        }

        // 3. 静态预编译正则特征短路
        IntentResult patternResult = checkPatternMatch(msg);
        if (patternResult != null) {
            log.debug("意图匹配 → 正则特征: {}", patternResult.intent());
            return patternResult;
        }

        // 4. LLM 智能分类器 (Fail-safe, 500ms 超时)
        if (resolved != null && resolved.chatModel() != null) {
            try {
                IntentResult llmResult = classifyViaLlm(msg, resolved);
                if (llmResult != null) {
                    log.debug("意图匹配 → LLM 分类器: {}", llmResult.intent());
                    return llmResult;
                }
            } catch (Exception e) {
                log.warn("LLM 意图识别超时/异常，Fail-safe 降级为 CHAT: {}", e.getMessage());
            }
        }

        return buildDefaultResult(IntentType.CHAT);
    }

    private IntentResult checkSlashCommands(String msg) {
        String lower = msg.toLowerCase();
        if (lower.startsWith("/code")) {
            return buildDefaultResult(IntentType.CODE);
        }
        if (lower.startsWith("/translate") || lower.startsWith("/trans")) {
            return buildDefaultResult(IntentType.TRANSLATION);
        }
        if (lower.startsWith("/write")) {
            return buildDefaultResult(IntentType.WRITING);
        }
        if (lower.startsWith("/search")) {
            return buildDefaultResult(IntentType.SEARCH);
        }
        if (lower.startsWith("/math") || lower.startsWith("/calc")) {
            return buildDefaultResult(IntentType.MATH);
        }
        if (lower.startsWith("/image") || lower.startsWith("/img")) {
            return buildDefaultResult(IntentType.IMAGE);
        }
        if (lower.startsWith("/analysis")) {
            return buildDefaultResult(IntentType.ANALYSIS);
        }
        if (lower.startsWith("/chat")) {
            return buildDefaultResult(IntentType.CHAT);
        }
        return null;
    }

    private IntentResult checkPatternMatch(String msg) {
        if (TRANSLATION_PATTERN.matcher(msg).find()) {
            return buildDefaultResult(IntentType.TRANSLATION);
        }
        if (CODE_PATTERN.matcher(msg).find()) {
            return buildDefaultResult(IntentType.CODE);
        }
        if (MATH_PATTERN.matcher(msg).find()) {
            return buildDefaultResult(IntentType.MATH);
        }
        if (SEARCH_PATTERN.matcher(msg).find()) {
            return buildDefaultResult(IntentType.SEARCH);
        }
        if (WRITING_PATTERN.matcher(msg).find()) {
            return buildDefaultResult(IntentType.WRITING);
        }
        if (ANALYSIS_PATTERN.matcher(msg).find()) {
            return buildDefaultResult(IntentType.ANALYSIS);
        }
        return null;
    }

    private IntentResult classifyViaLlm(String msg, ResolvedModel resolved) {
        // 意图分类为简单 JSON 枚举输出任务，显式改用当前供应商下最便宜的低成本模型，
        // 避免占用用户高等级（高成本）模型的额度。
        ResolvedModel classifyModel = taskModelRouter.resolve("INTENT_CLASSIFY", resolved);
        String systemPrompt = """
				你是一个极简意图分类器。请判断用户输入的意图类型，仅返回如下枚举值之一：
				CHAT, CODE, TRANSLATION, WRITING, ANALYSIS, SEARCH, MATH, MULTIMODAL, IMAGE
				请勿添加任何多余文字或标点符号。
				""";
        Prompt prompt = new Prompt(
                List.of(new SystemMessage(systemPrompt), new UserMessage(msg)),
                ChatOptionsFactory.forProvider(classifyModel, 0.0));

        return Mono.fromCallable(() -> classifyModel.chatModel().call(prompt))
                .timeout(LLM_TIMEOUT)
                .subscribeOn(Schedulers.boundedElastic())
                .map(resp -> {
                    if (resp != null
                            && resp.getResult() != null
                            && resp.getResult().getOutput() != null) {
                        String text = resp.getResult().getOutput().getText();
                        if (text != null) {
                            String clean = text.trim().toUpperCase();
                            for (IntentType type : IntentType.values()) {
                                if (clean.contains(type.name())) {
                                    return buildDefaultResult(type);
                                }
                            }
                        }
                    }
                    return buildDefaultResult(IntentType.CHAT);
                })
                .onErrorReturn(buildDefaultResult(IntentType.CHAT))
                .block();
    }

    /**
     * 构建意图对应的系统提示词模板与推荐路由选项。
     */
    public IntentResult buildDefaultResult(IntentType intent) {
        return switch (intent) {
            case CODE ->
                new IntentResult(
                        IntentType.CODE, false, true, "【智能路由 - 代码优化模式】请提供清晰、规范、高效的代码实现，包含关键代码注释，并解释主要逻辑与时间复杂度。");
            case TRANSLATION ->
                new IntentResult(
                        IntentType.TRANSLATION, false, false, "【智能路由 - 翻译模式】请进行准确、通顺、信达雅的翻译，保持原文语气与格式，无需进行多余问候。");
            case WRITING ->
                new IntentResult(IntentType.WRITING, false, false, "【智能路由 - 创作模式】请根据要求输出结构清晰、文笔优美、段落分明的文本，注意排版与语气调整。");
            case SEARCH ->
                new IntentResult(IntentType.SEARCH, true, true, "【智能路由 - 搜索分析模式】请优先利用搜索与外部工具检索最新资料，确保信息的时效性与准确性。");
            case MATH ->
                new IntentResult(IntentType.MATH, false, true, "【智能路由 - 数学推导模式】请给出分步骤的推导与计算过程，准确使用 LaTeX 公式格式格式化数学符号。");
            case ANALYSIS ->
                new IntentResult(IntentType.ANALYSIS, true, true, "【智能路由 - 深度分析模式】请从多维度进行系统化分析，对比优缺点与风险，给出客观、全面的结论。");
            case MULTIMODAL ->
                new IntentResult(IntentType.MULTIMODAL, false, false, "【智能路由 - 多模态模式】请结合用户上传的媒体内容进行细致解析与说明。");
            case IMAGE -> new IntentResult(IntentType.IMAGE, false, false, "【智能路由 - 绘图创作模式】提炼图像 Prompt 并调用图像生成组件。");
            case CHAT -> new IntentResult(IntentType.CHAT, false, false, null);
        };
    }
}
