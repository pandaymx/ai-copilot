package xyz.ppmblszdp.ai.clarification;

import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

/**
 * AI 主动澄清智能评估引擎。
 *
 * <p>核心机制：
 * <ol>
 *   <li><b>防循环追问死锁 (Anti-Clarification Loop)</b>：检测上一轮助手消息是否包含澄清提问标记，若用户正在回答追问，无条件放行；</li>
 *   <li><b>多领域模糊模式识别</b>：识别代码生成、错误排查、性能优化、部署运维、方案选型与指代不明等经典模糊场景；</li>
 *   <li><b>动态生成结构化提问</b>：支持严格阻断模式（STRICT）与柔性追问引导模式（SOFT）。</li>
 * </ol>
 */
@Service
public class ClarificationEngine {

    private static final Logger log = LoggerFactory.getLogger(ClarificationEngine.class);

    private final ClarificationProperties properties;

    // ── 预编译正则特征矩阵 ──
    private static final Pattern CODE_AMBIGUOUS_PATTERN = Pattern.compile(
            "^(帮我)?(写个|写一段|写一个|做个|实现)?(脚本|爬虫|代码|程序|系统|小工具|demo|Demo|demo项目|功能|登录|注册|增删改查|接口)$"
                    + "|^(怎么写|如何写|如何实现)(代码|程序|系统|功能)?$"
                    + "|^(帮我写代码|写个代码|写代码)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ERROR_AMBIGUOUS_PATTERN = Pattern.compile(
            "^(运行)?(报错了|出错了|挂了|崩了|失败了|有bug|有Bug|跑不通|启动失败|请求失败)(怎么办|怎么解决|如何解决|为什么|求助)?$"
                    + "|^(这个)?(报错|异常|Error|error|Bug|bug|Exception)(怎么解决|如何解决|怎么调|怎么修)?$"
                    + "|^(为什么运行不了|为什么报错|报错怎么解决|程序报错)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern OPTIMIZATION_AMBIGUOUS_PATTERN = Pattern.compile(
            "^(帮我)?(优化一下|怎么优化|如何优化|优化性能|性能优化|做下重构|代码重构|改进一下|怎么提升性能)(吗|吧)?$" + "|^(优化代码|重构代码|优化速度|优化内存)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DEPLOY_AMBIGUOUS_PATTERN = Pattern.compile(
            "^(怎么|如何)?(部署|上线|发布|配置服务器|发布应用|做自动化部署)(呢|吗|吧|呀)?$" + "|^(怎么部署项目|如何部署|项目怎么上线)$", Pattern.CASE_INSENSITIVE);

    private static final Pattern ARCH_AMBIGUOUS_PATTERN = Pattern.compile(
            "^(给个|做个|设计个)?(方案|架构方案|设计方案|技术选型|选型方案|技术方案)(吧|呗|吗)?$" + "|^(哪个好|选哪个|哪个性能高|用什么好)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern GENERAL_AMBIGUOUS_PATTERN =
            Pattern.compile("^(为什么不行|怎么搞|怎么做|有什么区别|区别是什么|啥区别|怎么办)(呢|啊|呀|吧|吗)?$", Pattern.CASE_INSENSITIVE);

    public ClarificationEngine(ClarificationProperties properties) {
        this.properties = properties;
    }

    /**
     * 评估用户提问的清晰度与完整性。
     *
     * @param userText        用户本次提问文本
     * @param historyMessages 历史对话记录（用于防循环追问检测）
     * @param requestMode     请求级指定的模式（可选覆盖）
     * @param isAgentMode     是否为 Agent / 工具调用模式
     * @return 评估结果 {@link ClarificationAssessment}
     */
    public ClarificationAssessment evaluate(
            String userText, List<Message> historyMessages, ClarificationMode requestMode, boolean isAgentMode) {

        if (!properties.isEnabled()) {
            return ClarificationAssessment.clear(ClarificationMode.DISABLED);
        }

        ClarificationMode activeMode = resolveActiveMode(requestMode, isAgentMode);
        if (activeMode == ClarificationMode.DISABLED) {
            return ClarificationAssessment.clear(activeMode);
        }

        if (userText == null || userText.isBlank()) {
            return ClarificationAssessment.clear(activeMode);
        }

        String trimmed = userText.trim();

        // 1. 防循环追问死锁 (Anti-Clarification Loop)
        if (isAnsweringPreviousClarification(historyMessages)) {
            log.debug("🛡️ [ClarificationEngine] 检测到用户正在响应上一轮澄清提问，无条件放行");
            return ClarificationAssessment.clear(activeMode);
        }

        // 2. 斜杠命令短路
        if (properties.getSkipCommands() != null) {
            String lower = trimmed.toLowerCase();
            for (String cmd : properties.getSkipCommands()) {
                if (lower.startsWith(cmd)) {
                    log.debug("🛡️ [ClarificationEngine] 命中跳过命令 [{}], 直接放行", cmd);
                    return ClarificationAssessment.clear(activeMode);
                }
            }
        }

        // 3. 详细长文本与代码块短路（信息丰富度充足）
        if (trimmed.length() > 100
                || trimmed.contains("```")
                || trimmed.contains("\n\n")
                || trimmed.contains("{") && trimmed.contains("}")) {
            return ClarificationAssessment.clear(activeMode);
        }

        // 4. 模糊场景模式匹配
        ClarificationScenario scenario = matchScenario(trimmed);
        if (scenario == null) {
            return ClarificationAssessment.clear(activeMode);
        }

        // 5. 组装缺失要素与澄清文案
        List<String> missingAspects = scenario.missingAspects;
        if (missingAspects.size() > properties.getMaxQuestions()) {
            missingAspects = missingAspects.subList(0, properties.getMaxQuestions());
        }

        String formattedMessage = buildClarificationMessage(scenario.category, missingAspects);
        log.info(
                "🎯 [ClarificationEngine] 捕获模糊提问 → 类别: {}, 模式: {}, 缺失要素: {}",
                scenario.category,
                activeMode,
                missingAspects);

        return ClarificationAssessment.ambiguous(activeMode, scenario.category, missingAspects, formattedMessage);
    }

    /**
     * 判断上一轮是否由 AI 抛出过主动澄清提问。
     */
    public boolean isAnsweringPreviousClarification(List<Message> historyMessages) {
        if (historyMessages == null || historyMessages.isEmpty()) {
            return false;
        }
        // 倒序寻找最近一条 Assistant 消息
        for (int i = historyMessages.size() - 1; i >= 0; i--) {
            Message msg = historyMessages.get(i);
            if (msg instanceof AssistantMessage am) {
                String text = am.getText();
                if (text != null
                        && (text.contains(ClarificationAssessment.CLARIFICATION_MARKER)
                                || text.contains("我需要向您确认以下几个关键信息")
                                || text.contains("为了更准确地为您提供帮助")
                                || text.contains("💡 深入解答所需信息"))) {
                    return true;
                }
                break; // 只检查最近一轮
            }
        }
        return false;
    }

    private ClarificationMode resolveActiveMode(ClarificationMode requestMode, boolean isAgentMode) {
        if (requestMode != null) {
            return requestMode;
        }
        if (isAgentMode) {
            return properties.getAgentMode() != null ? properties.getAgentMode() : ClarificationMode.STRICT;
        }
        return properties.getDefaultMode() != null ? properties.getDefaultMode() : ClarificationMode.SOFT;
    }

    private ClarificationScenario matchScenario(String text) {
        if (CODE_AMBIGUOUS_PATTERN.matcher(text).find()) {
            return new ClarificationScenario(
                    "CODE_GENERATION",
                    List.of(
                            "目标编程语言与框架版本（例如：Python 3.11 / Java 21 / Node.js 20 等）",
                            "期望实现的核心业务逻辑与具体功能需求",
                            "数据输入与输出格式约束（例如：JSON / REST API / 控制台交互）"));
        }
        if (ERROR_AMBIGUOUS_PATTERN.matcher(text).find()) {
            return new ClarificationScenario(
                    "DEBUGGING_ERROR",
                    List.of("完整的报错信息、异常堆栈（Stack Trace）或具体错误码", "引发报错的关键代码片段或操作流程", "项目运行环境与核心依赖版本（操作系统、语言版本、框架版本等）"));
        }
        if (OPTIMIZATION_AMBIGUOUS_PATTERN.matcher(text).find()) {
            return new ClarificationScenario(
                    "PERFORMANCE_OPTIMIZATION",
                    List.of("需要优化的原始代码片段或当前系统架构实现", "主要期望优化的维度（例如：执行耗时/延时、内存占用、CPU负载或代码可读性）", "目前的性能瓶颈或期望达到的性能量化指标"));
        }
        if (DEPLOY_AMBIGUOUS_PATTERN.matcher(text).find()) {
            return new ClarificationScenario(
                    "DEPLOYMENT_OPS",
                    List.of(
                            "项目的具体技术栈与打包方式（例如：Spring Boot jar / Next.js / 前后端分离）",
                            "目标部署环境（例如：Docker 容器 / Kubernetes / Linux 裸机 / 公有云服务）",
                            "是否需要配置域名解析、HTTPS 证书或 CI/CD 自动化流水线"));
        }
        if (ARCH_AMBIGUOUS_PATTERN.matcher(text).find()) {
            return new ClarificationScenario(
                    "ARCHITECTURE_PROPOSAL",
                    List.of("具体的业务应用场景与核心功能目标", "预估的并发量与数据规模（如 QPS、用户量、存储容量要求）", "团队现有技术栈储备与开发/运维成本预算限制"));
        }
        if (GENERAL_AMBIGUOUS_PATTERN.matcher(text).find()) {
            return new ClarificationScenario(
                    "GENERAL_UNDERSPECIFIED", List.of("您希望深入探讨或对比的具体主体（如技术 A 与技术 B）", "当前遇到的具体现象、前提条件或上下文背景"));
        }
        return null;
    }

    private String buildClarificationMessage(String category, List<String> missingAspects) {
        StringBuilder sb = new StringBuilder();
        sb.append("为了更准确地为您提供高质量解答，我需要向您先确认以下关键信息：\n\n");
        for (int i = 0; i < missingAspects.size(); i++) {
            sb.append(i + 1).append(". **").append(missingAspects.get(i)).append("**\n");
        }
        sb.append("\n您可以直接回复上述信息（或补充更多细节），我将立即为您提供精准方案！\n");
        sb.append(ClarificationAssessment.CLARIFICATION_MARKER).append("\n");
        return sb.toString();
    }

    private record ClarificationScenario(String category, List<String> missingAspects) {}
}
