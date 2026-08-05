package xyz.ppmblszdp.ai.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * 供应商不存在或未启用。
 *
 * <p>错误信息中会列出当前可用 supplier 列表，便于排障。当未指定 provider 且无法反查唯一归属，
 * 或 model 在多个 provider 下重名导致歧义时，也复用本异常（通过 {@code ambiguous} 标志区分文案）。
 */
public class ProviderNotFoundException extends AiException {

	/**
	 * @param providerId 缺失的供应商 id（歧义场景忽略此值）
	 * @param ambiguous  是否为「模型重名导致歧义」场景
	 * @param available  当前可用供应商列表
	 */
	public ProviderNotFoundException(String providerId, boolean ambiguous, List<String> available) {
		super(ambiguous ? "PROVIDER_AMBIGUOUS" : "PROVIDER_NOT_FOUND",
				HttpStatus.BAD_REQUEST,
				ambiguous
						? "请求的模型在多个供应商下都存在，请显式指定 provider。可选供应商: %s".formatted(available)
						: "供应商 '%s' 不存在或未启用。当前可用供应商: %s".formatted(providerId, available));
	}
}
