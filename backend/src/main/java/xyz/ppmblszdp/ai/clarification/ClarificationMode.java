package xyz.ppmblszdp.ai.clarification;

/**
 * 主动澄清模式枚举。
 */
public enum ClarificationMode {
    /**
     * 严格（必问）模式：检测到问题模糊或缺失关键要素时，短路 LLM 执行，直接向用户抛出结构化澄清问题。
     */
    STRICT,

    /**
     * 柔性模式：基于常规合理假设先给出初步解答，并在回答末尾主动追问关键细节引导深入。
     */
    SOFT,

    /**
     * 禁用模式：不执行主动澄清检测。
     */
    DISABLED
}
