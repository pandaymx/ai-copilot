package xyz.ppmblszdp.ai.clarification;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.Ordered;
import reactor.core.publisher.Flux;

/**
 * Spring AI 2.0.0 标准 ClarificationAdvisor 主动提问澄清 Advisor。
 *
 * <p>责任链执行顺序 (Order = HIGHEST_PRECEDENCE + 100)：
 * <pre>
 * SafeGuardAdvisor (安全过滤) → ClarificationAdvisor (清晰度评估与短路) → Memory/RagAdvisor → ChatModel
 * </pre>
 */
public class ClarificationAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger log = LoggerFactory.getLogger(ClarificationAdvisor.class);

    public static final String CTX_CLARIFICATION_MODE = "clarificationMode";
    public static final String CTX_IS_AGENT = "isAgent";

    private final ClarificationEngine engine;
    private final ClarificationProperties properties;
    private final int order;

    public ClarificationAdvisor(ClarificationEngine engine, ClarificationProperties properties) {
        this(engine, properties, Ordered.HIGHEST_PRECEDENCE + 100);
    }

    public ClarificationAdvisor(ClarificationEngine engine, ClarificationProperties properties, int order) {
        this.engine = engine;
        this.properties = properties;
        this.order = order;
    }

    @Override
    public String getName() {
        return "ClarificationAdvisor";
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        if (!properties.isEnabled()) {
            return chain.nextCall(request);
        }

        String userText = extractUserText(request);
        List<Message> history = extractHistory(request);
        ClarificationMode reqMode = extractRequestMode(request);
        boolean isAgent = extractIsAgent(request);

        ClarificationAssessment assessment = engine.evaluate(userText, history, reqMode, isAgent);
        if (assessment.isAmbiguous()) {
            if (assessment.mode() == ClarificationMode.STRICT) {
                log.info("❓ [ClarificationAdvisor] STRICT 模式前置短路拦截，直接下发澄清提问");
                return buildClarificationResponse(request, assessment.clarificationMessage());
            }
            if (assessment.mode() == ClarificationMode.SOFT) {
                request = augmentSystemPromptWithSoftClarification(request, assessment);
            }
        }

        return chain.nextCall(request);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        if (!properties.isEnabled()) {
            return chain.nextStream(request);
        }

        String userText = extractUserText(request);
        List<Message> history = extractHistory(request);
        ClarificationMode reqMode = extractRequestMode(request);
        boolean isAgent = extractIsAgent(request);

        ClarificationAssessment assessment = engine.evaluate(userText, history, reqMode, isAgent);
        if (assessment.isAmbiguous()) {
            if (assessment.mode() == ClarificationMode.STRICT) {
                log.info("❓ [ClarificationAdvisor-Stream] STRICT 模式流式短路拦截，直接下发澄清提问");
                return Flux.just(buildClarificationResponse(request, assessment.clarificationMessage()));
            }
            if (assessment.mode() == ClarificationMode.SOFT) {
                request = augmentSystemPromptWithSoftClarification(request, assessment);
            }
        }

        return chain.nextStream(request);
    }

    // ─────────────────────────────────────────────
    // 辅助方法
    // ─────────────────────────────────────────────

    private String extractUserText(ChatClientRequest request) {
        if (request == null || request.prompt() == null) {
            return "";
        }
        List<Message> messages = request.prompt().getInstructions();
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        // 取最后一条 UserMessage
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            if (m instanceof UserMessage um) {
                return um.getText();
            }
        }
        return "";
    }

    private List<Message> extractHistory(ChatClientRequest request) {
        if (request == null || request.prompt() == null) {
            return List.of();
        }
        List<Message> messages = request.prompt().getInstructions();
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        // 返回前驱历史消息（除最后一条 UserMessage 之外）
        if (messages.size() > 1) {
            return messages.subList(0, messages.size() - 1);
        }
        return List.of();
    }

    private ClarificationMode extractRequestMode(ChatClientRequest request) {
        if (request == null || request.context() == null) {
            return null;
        }
        Object modeObj = request.context().get(CTX_CLARIFICATION_MODE);
        if (modeObj instanceof ClarificationMode cm) {
            return cm;
        }
        if (modeObj instanceof String str) {
            try {
                return ClarificationMode.valueOf(str.toUpperCase());
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private boolean extractIsAgent(ChatClientRequest request) {
        if (request == null || request.context() == null) {
            return false;
        }
        Object agentObj = request.context().get(CTX_IS_AGENT);
        if (agentObj instanceof Boolean b) {
            return b;
        }
        return false;
    }

    private ChatClientRequest augmentSystemPromptWithSoftClarification(
            ChatClientRequest request, ClarificationAssessment assessment) {
        if (request == null || request.prompt() == null) {
            return request;
        }

        StringBuilder instruction = new StringBuilder("\n\n【主动澄清与追问指引】\n");
        instruction.append("用户当前提问较为简略或缺少部分关键上下文。请在回答时：\n");
        instruction.append("1. 先基于常规合理假设给出初步解答与常见最佳实践方案；\n");
        instruction.append("2. 在回答末尾必须追加「💡 深入解答所需信息」小节，列出以下 2~3 个精准追问点引导深入：\n");
        for (String aspect : assessment.missingAspects()) {
            instruction.append("   - ").append(aspect).append("\n");
        }

        List<Message> original = request.prompt().getInstructions();
        List<Message> updated = new ArrayList<>();
        boolean systemAugmented = false;

        for (Message m : original) {
            if (m instanceof SystemMessage sm && !systemAugmented) {
                updated.add(new SystemMessage(sm.getText() + instruction));
                systemAugmented = true;
            } else {
                updated.add(m);
            }
        }

        if (!systemAugmented) {
            updated.add(0, new SystemMessage(instruction.toString().trim()));
        }

        Prompt newPrompt = new Prompt(updated, request.prompt().getOptions());
        return request.mutate().prompt(newPrompt).build();
    }

    private ChatClientResponse buildClarificationResponse(ChatClientRequest request, String clarificationText) {
        AssistantMessage assistantMessage = new AssistantMessage(clarificationText);
        Generation generation = new Generation(assistantMessage);
        ChatResponse clarificationChatResponse = new ChatResponse(List.of(generation));
        return new ChatClientResponse(
                clarificationChatResponse, request != null ? request.context() : java.util.Map.of());
    }
}
