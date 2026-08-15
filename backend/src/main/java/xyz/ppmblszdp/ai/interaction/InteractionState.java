package xyz.ppmblszdp.ai.interaction;

/**
 * 宏观用户交互认知与任务状态枚举。
 */
public enum InteractionState {
    /** 常规中性交互/标准事实查询 */
    NEUTRAL("中性日常", "常规交流与标准查询"),

    /** 指令主导/强约束要求（如要求精简、直奔主题、代码优先） */
    DIRECTIVE("明确指令", "用户提出了强格式约束或直奔主题"),

    /** 疑惑不解/概念未理解/需要降低认知门槛 */
    CONFUSED("疑惑待解", "用户表达不理解或需要通俗拆解"),

    /** 用户对上轮结果表达不满意/冗长质疑 */
    DISSATISFIED("诉求不符", "用户对既往输出的冗余或偏离表示不满"),

    /** 明确指出错误并要求重构/纠偏/修复 */
    CORRECTION_REQUIRED("纠偏重构", "用户指出上一轮答案有误并要求修正"),

    /** 探索性发散/头脑风暴/方案对比 */
    EXPLORATORY("发散探索", "用户进行头脑风暴或多方案选型探讨"),

    /** 深度技术剖析/底层原理深挖 */
    DEEP_DIVE("深度探究", "用户探究底层机制、架构全貌或高并发深水区"),

    /** 强任务执行/可落地实操导向 */
    TASK_FOCUSED("实操落地", "聚焦于特定工程任务的可执行落地交付"),

    /** 积极肯定/正面反馈/任务完成确认 */
    POSITIVE("肯定确认", "用户确认问题已解决或表示赞赏");

    private final String label;
    private final String description;

    InteractionState(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }
}
