package xyz.ppmblszdp.ai.reflection;

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
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.Ordered;
import reactor.core.publisher.Flux;

/**
 * Spring AI 2.0.0 标准 ReflectionAdvisor 自我反思与纠错 Advisor。
 *
 * <p>责任链执行顺序 (Order = Ordered.LOWEST_PRECEDENCE - 50)：
 * 在 ChatModel 输出完成后进行后置自检，必要时自动注入纠偏补充说明。
 */
public class ReflectionAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger log = LoggerFactory.getLogger(ReflectionAdvisor.class);

    private final ReflectionEngine engine;
    private final ReflectionProperties properties;
    private final int order;

    public ReflectionAdvisor(ReflectionEngine engine, ReflectionProperties properties) {
        this(engine, properties, Ordered.LOWEST_PRECEDENCE - 50);
    }

    public ReflectionAdvisor(ReflectionEngine engine, ReflectionProperties properties, int order) {
        this.engine = engine;
        this.properties = properties;
        this.order = order;
    }

    @Override
    public String getName() {
        return "ReflectionAdvisor";
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientResponse originalResponse = chain.nextCall(request);
        if (!properties.isEnabled()) {
            return originalResponse;
        }

        String userPrompt = extractUserPrompt(request);
        String assistantReply = extractAssistantReply(originalResponse);

        if (assistantReply == null || assistantReply.length() < properties.getMinContentLength()) {
            return originalResponse;
        }

        ReflectionAssessment assessment = engine.evaluate(userPrompt, assistantReply, null);
        if (!assessment.passed() && properties.isAutoCorrectionEnabled()) {
            String correctionBlock = formatCorrectionBlock(assessment);
            String augmentedReply = assistantReply + "\n\n" + correctionBlock;

            log.info("🔍 [ReflectionAdvisor] 非流式回复已追加自我反思纠偏内容");
            return rebuildResponseWithText(originalResponse, augmentedReply);
        }

        return originalResponse;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        Flux<ChatClientResponse> stream = chain.nextStream(request);
        if (!properties.isEnabled() || !properties.isAutoCorrectionEnabled()) {
            return stream;
        }

        StringBuilder fullText = new StringBuilder();
        String userPrompt = extractUserPrompt(request);

        return stream.doOnNext(chunk -> {
                    String piece = extractAssistantReply(chunk);
                    if (piece != null) {
                        fullText.append(piece);
                    }
                })
                .concatWith(Flux.defer(() -> {
                    String completedText = fullText.toString();
                    if (completedText.length() < properties.getMinContentLength()) {
                        return Flux.<ChatClientResponse>empty();
                    }

                    ReflectionAssessment assessment = engine.evaluate(userPrompt, completedText, null);
                    if (!assessment.passed()) {
                        String correctionBlock = "\n\n" + formatCorrectionBlock(assessment);
                        log.info("🔍 [ReflectionAdvisor] 流式回复结束，追加自我反思纠错帧");
                        Generation gen = new Generation(new AssistantMessage(correctionBlock));
                        ChatResponse chatResp = new ChatResponse(List.of(gen));
                        return Flux.just(new ChatClientResponse(
                                chatResp, request != null ? request.context() : java.util.Map.of()));
                    }
                    return Flux.<ChatClientResponse>empty();
                }));
    }

    public static String formatCorrectionBlock(ReflectionAssessment assessment) {
        StringBuilder sb = new StringBuilder();
        sb.append("> 🔍 **AI 自我纠错与补充**\n");
        if (assessment.issues() != null && !assessment.issues().isEmpty()) {
            sb.append("> **自检要点**：")
                    .append(String.join("；", assessment.issues()))
                    .append("\n");
        }
        if (assessment.correctionExplanation() != null
                && !assessment.correctionExplanation().isBlank()) {
            sb.append("> **修正说明**：").append(assessment.correctionExplanation()).append("\n");
        }
        sb.append(">\n");
        if (assessment.supplementalCorrection() != null
                && !assessment.supplementalCorrection().isBlank()) {
            sb.append("> ").append(assessment.supplementalCorrection().replace("\n", "\n> "));
        } else {
            sb.append("> （以上回答经自我复核已做进一步澄清）");
        }
        return sb.toString();
    }

    private String extractUserPrompt(ChatClientRequest request) {
        if (request != null && request.prompt() != null && request.prompt().getInstructions() != null) {
            for (Message msg : request.prompt().getInstructions()) {
                if (msg.getMessageType() == MessageType.USER) {
                    return msg.getText();
                }
            }
        }
        return "";
    }

    private String extractAssistantReply(ChatClientResponse response) {
        if (response != null
                && response.chatResponse() != null
                && response.chatResponse().getResult() != null) {
            Generation gen = response.chatResponse().getResult();
            if (gen.getOutput() != null) {
                return gen.getOutput().getText();
            }
        }
        return "";
    }

    private ChatClientResponse rebuildResponseWithText(ChatClientResponse orig, String newText) {
        Generation newGen = new Generation(new AssistantMessage(newText));
        ChatResponse newChatResponse = new ChatResponse(List.of(newGen));
        return new ChatClientResponse(newChatResponse, orig != null ? orig.context() : java.util.Map.of());
    }
}
