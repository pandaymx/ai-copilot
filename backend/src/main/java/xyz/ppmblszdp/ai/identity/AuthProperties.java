package xyz.ppmblszdp.ai.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 受信任 Header 身份认证配置。
 *
 * <p>后端定位为「专属 AI 微服务」，认证由前置网关（如 Caddy basic_auth）处理，
 * 网关在转发请求时注入 {@code X-User-Id} 头。后端不引入 Spring Security，
 * 仅从受信任 Header 读取已认证身份。
 *
 * <p>默认 {@code mode=strict}（fail-closed）：不配置环境变量即安全，
 * 缺少受信任 Header 时拒绝请求（401）。开发环境显式设 {@code AUTH_MODE=dev}
 * 才 fallback 到请求体中的 userId 或匿名身份。
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
		@DefaultValue("strict") String mode,
		@DefaultValue("X-User-Id") String headerName
) {
	/** 严格模式：必须有受信任 Header，否则拒绝请求。 */
	public boolean isStrict() {
		return "strict".equalsIgnoreCase(mode);
	}
}
