package xyz.ppmblszdp.ai.context;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;

/**
 * 基于 JTokkit (Tiktoken) 的精确 Token 估算器。
 *
 * <p>面向 OpenAI / Claude / Gemini 等采用 Tiktoken/BPE 编码的模型，
 * 默认使用 {@link EncodingType#O200K_BASE} (GPT-4o / GPT-4o-mini 等最新模型编码) 进行精确分词。
 * 相比字符启发式估算，在英文与代码段场景下能显著提升上下文预算利用率。
 * 当分词异常或解析失败时，自动安全降级至 {@link HeuristicTokenEstimator}。
 */
public class JTokkitTokenEstimator implements TokenEstimator {

    private static final Logger log = LoggerFactory.getLogger(JTokkitTokenEstimator.class);
    private static final int ROLE_OVERHEAD = 4;

    private final Encoding encoding;
    private final TokenEstimator fallback;

    public JTokkitTokenEstimator() {
        this(EncodingType.O200K_BASE, new HeuristicTokenEstimator(1.1d));
    }

    public JTokkitTokenEstimator(TokenEstimator fallback) {
        this(EncodingType.O200K_BASE, fallback);
    }

    public JTokkitTokenEstimator(EncodingType encodingType, TokenEstimator fallback) {
        this.fallback = (fallback != null) ? fallback : new HeuristicTokenEstimator(1.1d);
        Encoding enc = null;
        try {
            EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
            enc = registry.getEncoding(encodingType);
            log.info("JTokkitTokenEstimator 初始化成功，编码器={}", encodingType.getName());
        } catch (Exception e) {
            log.warn("JTokkitTokenEstimator 初始化失败，将使用 HeuristicTokenEstimator 降级", e);
        }
        this.encoding = enc;
    }

    @Override
    public int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        if (encoding != null) {
            try {
                return encoding.countTokens(text);
            } catch (Exception e) {
                log.debug("JTokkit 计词失败，降级至 HeuristicTokenEstimator: {}", e.getMessage());
            }
        }
        return fallback.estimate(text);
    }

    @Override
    public int estimate(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        if (encoding != null) {
            try {
                int total = 0;
                for (Message m : messages) {
                    String content = (m.getText() == null) ? "" : m.getText();
                    total += encoding.countTokens(content) + ROLE_OVERHEAD;
                }
                return total;
            } catch (Exception e) {
                log.debug("JTokkit 批量计词失败，降级至 HeuristicTokenEstimator: {}", e.getMessage());
            }
        }
        return fallback.estimate(messages);
    }
}
