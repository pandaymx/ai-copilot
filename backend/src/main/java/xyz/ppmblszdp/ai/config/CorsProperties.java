package xyz.ppmblszdp.ai.config;

import jakarta.annotation.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS 跨域安全配置项 (app.cors)。
 *
 * @param allowedOrigins   允许的域名列表（逗号分隔），生产环境收敛为具体 Origin
 * @param allowCredentials 是否允许携带 Header 凭证
 * @param allowedHeaders   允许的请求头列表（逗号分隔），用于放行 X-User-Id 等自定义头
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
		@Nullable String allowedOrigins,
		@Nullable Boolean allowCredentials,
		@Nullable String allowedHeaders
) {
	public String resolveAllowedOrigins() {
		return (allowedOrigins != null && !allowedOrigins.isBlank()) ? allowedOrigins.trim() : "*";
	}

	public boolean isAllowCredentials() {
		return allowCredentials != null && allowCredentials;
	}

	public String resolveAllowedHeaders() {
		return (allowedHeaders != null && !allowedHeaders.isBlank()) ? allowedHeaders.trim() : "*";
	}

	public boolean hasWildcardOrigin() {
		String origins = resolveAllowedOrigins();
		return "*".equals(origins) || origins.contains("*");
	}

	public boolean hasWildcardHeader() {
		String headers = resolveAllowedHeaders();
		return "*".equals(headers) || headers.contains("*");
	}
}
