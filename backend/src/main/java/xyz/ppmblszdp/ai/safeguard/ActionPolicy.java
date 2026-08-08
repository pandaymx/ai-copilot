package xyz.ppmblszdp.ai.safeguard;

/**
 * 敏感防护分级处置策略。
 */
public enum ActionPolicy {
	/**
	 * 直接阻断：终止本次对话并返回安全合规拦截提示或抛出阻断异常。
	 */
	BLOCK,

	/**
	 * 脱敏打码：将敏感词或隐私信息替换为掩码（如 ***）后继续传输。
	 */
	MASK,

	/**
	 * 仅审计日志：不影响正常对话与输出，仅记录告警审计日志。
	 */
	LOG_ONLY
}
