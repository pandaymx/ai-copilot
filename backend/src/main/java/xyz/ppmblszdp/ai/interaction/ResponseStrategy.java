package xyz.ppmblszdp.ai.interaction;

/**
 * 决议出的 AI 回复策略与行为指令组合枚举。
 */
public enum ResponseStrategy {
    /** 默认常规回答模式 */
    DEFAULT("标准模式", "采用清晰专业、结构均衡的默认工程输出"),

    /** 极简干练：去除寒暄、铺垫，输出纯核心干货 */
    CONCISE("极简输出", "去除所有客套、引言与冗余解释，直给核心信息"),

    /** 开门见山：首句即直接给出最终结论或核心答案 */
    DIRECT_ANSWER("直给答案", "首段直接给出最终结论或可执行命令"),

    /** 代码优先：先输出完整可编译代码/命令，后附极简关键说明 */
    CODE_FIRST("代码优先", "将经过生产验证的代码或脚本置于最前，避免先讲长篇理论"),

    /** 通俗阐释：降低认知门槛，用生活化或直观比喻解释复杂概念 */
    EXPLANATORY("通俗阐释", "降低门槛，用通俗语言与结构化拆解核心概念"),

    /** 逐步递进：按清晰的步骤清单展开 */
    STEP_BY_STEP("分步递进", "按照清晰的 1, 2, 3 序号逐步引导与拆解"),

    /** 示例优先：用具象代码或业务场景 Demo 驱动讲解 */
    EXAMPLE_FIRST("示例驱动", "以最小可复现示例驱动说明"),

    /** 纠错重答：坦诚接纳上一轮瑕疵，重新审视并直接输出纠偏方案 */
    CORRECT_PREVIOUS_ANSWER("纠错重构", "纠正上一轮中的错误或偏差，直接给出修复后的正确实现"),

    /** 深度全貌：剖析底层内核机制、内存模型与高并发深水区 */
    DEEP_ANALYSIS("深度剖析", "深入底层原理、源码架构与边界陷阱全方位解析"),

    /** 对比矩阵：结构化对比不同技术选型的优缺点与适用场景 */
    COMPARATIVE("对比选型", "通过结构化对比分析各技术方案的利弊与适用边界"),

    /** 任务落地：可直接用于生产部署/排障的指令式交付 */
    TASK_EXECUTION("实操交付", "聚焦于立即可执行的工程指令与配置文件交付");

    private final String label;
    private final String policyGuideline;

    ResponseStrategy(String label, String policyGuideline) {
        this.label = label;
        this.policyGuideline = policyGuideline;
    }

    public String getLabel() {
        return label;
    }

    public String getPolicyGuideline() {
        return policyGuideline;
    }
}
