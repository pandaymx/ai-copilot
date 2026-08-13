package xyz.ppmblszdp.ai.registry;

/**
 * 模型健康熔断三态枚举。
 */
public enum HealthStatus {
    /**
     * 健康在线
     */
    UP,

    /**
     * 不可用 / 熔断中
     */
    DOWN,

    /**
     * 冷却结束，半开试探
     */
    HALF_OPEN
}
