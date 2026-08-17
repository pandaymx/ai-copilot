package xyz.ppmblszdp.ai.registry;

/**
 * 任务级模型选型键（5-#2）。
 *
 * <p>
 * 每个任务键映射到 {@code app.ai.routing.task-tiers} 中的一枚 {@code key}，由
 * {@link TaskModelResolver} 解析为可用的 {@link ResolvedModel}。枚举名即 tier 键名，
 * 因此 {@link #tierKey()} 直接返回 {@code name()}。
 *
 * <p>梯队语义（见方案 5-task-level-model-selection.md）：
 * <ul>
 *   <li>{@code MEMORY_EXTRACT} / {@code MEMORY_FORGET} → 低成本分类梯队（T1–T2）</li>
 *   <li>{@code GRAPH_EXTRACT} / {@code CONTENT_GEN} → 中等摘要梯队（T2–T3）</li>
 *   <li>{@code CODE_REVIEW} / {@code REFLECTION} / {@code TASK_PLAN} → 推理梯队（T3–T4）</li>
 * </ul>
 */
public enum TaskKey {
    MEMORY_EXTRACT,
    MEMORY_FORGET,
    CODE_REVIEW,
    GRAPH_EXTRACT,
    CONTENT_GEN,
    REFLECTION,
    TASK_PLAN;

    /** 与 {@code app.ai.routing.task-tiers[].key} 对齐的梯队键。 */
    public String tierKey() {
        return name();
    }
}
