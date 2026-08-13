package xyz.ppmblszdp.ai.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 二等公民供应商所使用的接入协议。
 *
 * <p>协议决定了使用哪一个 {@code ChatModelFactory} 来构建底层 {@code ChatModel}：
 * <ul>
 *   <li>{@link #OPENAI} —— OpenAI 兼容协议，覆盖通义千问 DashScope 兼容模式、
 *       百度千帆 v2、智谱、月之暗面、DeepSeek 等绝大多数厂商；</li>
 *   <li>{@link #ANTHROPIC} —— Anthropic / Claude 协议（含兼容网关）；</li>
 *   <li>{@link #CUSTOM} —— 自定义扩展点，交由用户实现的
 *       {@code CustomChatModelSupplier} 处理无法兼容的厂商。</li>
 * </ul>
 */
public enum ProviderProtocol {

    /** OpenAI 兼容协议。 */
    OPENAI,

    /** Anthropic / Claude 协议。 */
    ANTHROPIC,

    /** 自定义协议，走 SPI 扩展点。 */
    CUSTOM;

    /**
     * 宽松解析协议名称，大小写无关，并允许常见别名。
     *
     * @param raw 配置文件中书写的原始值，允许为 {@code null}
     * @return 解析出的协议；{@code raw} 为空时回落到 {@link #OPENAI}
     * @throws IllegalArgumentException 当取值无法识别时，异常信息中会列出全部候选值
     */
    public static ProviderProtocol fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return OPENAI;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            // 常见别名：这些厂商本质上都是 OpenAI 兼容端点
            case "openai", "open_ai", "oai", "openai_compatible", "compatible" -> OPENAI;
            case "anthropic", "claude" -> ANTHROPIC;
            case "custom", "spi" -> CUSTOM;
            default -> throw new IllegalArgumentException("未知的 protocol 取值 '%s'，可选值为: %s".formatted(raw, candidates()));
        };
    }

    private static String candidates() {
        return Arrays.stream(values())
                .map(p -> p.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(", "));
    }
}
