package xyz.ppmblszdp.ai.interaction;

/**
 * 用户输入中提取的正交、多维度原子交互特征信号。
 */
public enum InteractionSignal {
    /** 要求极简输出、剔除寒暄与铺垫 */
    REQUESTS_CONCISION("要求精简"),

    /** 要求开门见山直接给出核心答案/结论 */
    REQUESTS_DIRECT_ANSWER("直接答案"),

    /** 要求可运行代码/脚本/配置 */
    REQUESTS_CODE("代码优先"),

    /** 要求详细解释与原理阐述 */
    REQUESTS_EXPLANATION("原理阐述"),

    /** 要求具象化示例、Demo 或应用案例 */
    REQUESTS_EXAMPLE("提供示例"),

    /** 要求按步骤清晰拆解指引 */
    REQUESTS_STEP_BY_STEP("分步拆解"),

    /** 表达不理解、疑惑、困惑或反问 */
    EXPRESSES_CONFUSION("困惑不解"),

    /** 质疑上一轮输出或指出逻辑矛盾 */
    CHALLENGES_PREVIOUS_ANSWER("质疑前答"),

    /** 汇报运行报错、编译失败或异常堆栈 */
    REPORTS_ERROR("报错反馈"),

    /** 表达对冗余、跑题或既往答复的不满 */
    EXPRESSES_DISSATISFACTION("不合预期"),

    /** 要求多维度对比、技术选型或优劣势分析 */
    REQUESTS_COMPARISON("对比选型"),

    /** 要求探究底层源码、内核机制或深度剖析 */
    REQUESTS_DEEP_DIVE("深度探究"),

    /** 正面肯定、感谢或解决确认 */
    POSITIVE_FEEDBACK("正面确认");

    private final String label;

    InteractionSignal(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
