package xyz.ppmblszdp.ai.config;

import jakarta.annotation.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS 跨域安全配置项 (app.cors)。
 *
 * @param allowedOrigins   允许的域名列表（逗号分隔），生产环境收敛为具体 Origin
 * @param allowCredentials 是否允许携带 Header 凭证
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
		@Nullable String allowedOrigins,
		@Nullable Boolean allowCredentials
) {
	public String resolveAllowedOrigins() {
		return (allowedOrigins != null && !allowedOrigins.isBlank()) ? allowedOrigins.trim() : "*";
	}

	public boolean isAllowCredentials() {
		return allowCredentials != null && allowCredentials;
	}
}
