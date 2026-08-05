package xyz.ppmblszdp.ai.config;

import java.util.regex.Pattern;

/**
 * 密钥有效性校验：判断供应商是否「已配置」从而决定是否注册。
 *
 * <p>策略：空白、或匹配 {@code your_xxx_here} 占位风格、或长度过短，均视为未配置。
 * 本地模型（如自建 Ollama 网关）可通过 {@code requiresApiKey=false} 跳过密钥校验。
 */
public final class ApiKeyValidator {

	private static final Pattern PLACEHOLDER = Pattern.compile("^your_.*_here$", Pattern.CASE_INSENSITIVE);

	private ApiKeyValidator() {
	}

	/**
	 * @return true 表示密钥有效（未填写或仍为占位值时为 false）
	 */
	public static boolean isValid(String apiKey) {
		if (apiKey == null || apiKey.isBlank()) {
			return false;
		}
		if (PLACEHOLDER.matcher(apiKey.trim()).matches()) {
			return false;
		}
		return apiKey.trim().length() >= 8;
	}
}
