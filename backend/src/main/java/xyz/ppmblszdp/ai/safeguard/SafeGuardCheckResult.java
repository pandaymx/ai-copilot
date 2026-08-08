package xyz.ppmblszdp.ai.safeguard;

/**
 * 安全审查检测结果。
 */
public class SafeGuardCheckResult {

	public enum TriggerType {
		NONE,
		PROMPT_INJECTION,
		PHONE,
		ID_CARD,
		EMAIL,
		SENSITIVE_WORD
	}

	private final boolean triggered;
	private final TriggerType triggerType;
	private final String matchedRule;
	private final String processedText;

	public SafeGuardCheckResult(boolean triggered, TriggerType triggerType, String matchedRule, String processedText) {
		this.triggered = triggered;
		this.triggerType = triggerType;
		this.matchedRule = matchedRule;
		this.processedText = processedText;
	}

	public static SafeGuardCheckResult clean(String text) {
		return new SafeGuardCheckResult(false, TriggerType.NONE, null, text);
	}

	public static SafeGuardCheckResult triggered(TriggerType type, String rule, String processedText) {
		return new SafeGuardCheckResult(true, type, rule, processedText);
	}

	public boolean isTriggered() {
		return triggered;
	}

	public TriggerType getTriggerType() {
		return triggerType;
	}

	public String getMatchedRule() {
		return matchedRule;
	}

	public String getProcessedText() {
		return processedText;
	}
}
