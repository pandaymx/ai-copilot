package xyz.ppmblszdp.ai.safeguard;

import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 输出内容质量与毒性/偏见/幻觉门控（OutputQualityGate）。
 *
 * <p>在后置审查中对大模型输出进行快速质量审计与安全过滤。
 */
@Component
public class OutputQualityGate {

    private static final Logger log = LoggerFactory.getLogger(OutputQualityGate.class);

    public record QualityVerdict(boolean isSafe, String riskCategory, String detail) {

        public static QualityVerdict safe() {
            return new QualityVerdict(true, "NONE", "内容质量合规");
        }

        public static QualityVerdict flagged(String category, String detail) {
            return new QualityVerdict(false, category, detail);
        }
    }

    private static final List<Pattern> TOXICITY_PATTERNS = List.of(
            Pattern.compile("(?i)(kill\\s+yourself|commit\\s+suicide|self-harm|how\\s+to\\s+make\\s+a\\s+bomb)"),
            Pattern.compile("(?i)(hate\\s+speech|racial\\s+slur|ethnic\\s+cleansing)"),
            Pattern.compile("(?i)(制造爆炸物|制作毒药|自残教程|攻击指令)"));

    private static final List<Pattern> PROMPT_LEAK_PATTERNS = List.of(
            Pattern.compile("(?i)my\\s+system\\s+prompt\\s+is\\s*[:：]"),
            Pattern.compile("(?i)here\\s+are\\s+the\\s+instructions\\s+I\\s+was\\s+given\\s*[:：]"),
            Pattern.compile("(?i)我的系统内置指令如下\\s*[:：]"));

    /**
     * 审查大模型生成的回复内容
     */
    public QualityVerdict inspect(String response) {
        if (response == null || response.isBlank()) {
            return QualityVerdict.safe();
        }

        // 1. 极端毒性与危险行为
        for (Pattern p : TOXICITY_PATTERNS) {
            if (p.matcher(response).find()) {
                log.warn("🚨 [OutputQualityGate] 触发极端有害/毒性内容拦截: {}", p.pattern());
                return QualityVerdict.flagged("HIGH_TOXICITY", "模型输出包含极端有害、暴力或违规内容");
            }
        }

        // 2. 系统提示词泄露防护
        for (Pattern p : PROMPT_LEAK_PATTERNS) {
            if (p.matcher(response).find()) {
                log.warn("🚨 [OutputQualityGate] 触发系统提示词反向泄露拦截: {}", p.pattern());
                return QualityVerdict.flagged("PROMPT_LEAKAGE", "模型输出尝试转储/泄露系统内部指令");
            }
        }

        return QualityVerdict.safe();
    }
}
