package xyz.ppmblszdp.ai.identity;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 受信任身份 WebFilter。
 *
 * <p><b>绝对不读取请求体</b>：仅读取 {@code X-User-Id} 头，将其原始值写入
 * exchange attribute，供下游 Controller 解析身份。零 body 消费，避免破坏
 * SSE / JSON body 的后续解析。
 *
 * <p>本 Filter 不拒绝任何请求（不 fail-closed），是否要求 Header 由 Controller
 * 层依据 {@code app.auth.mode} 决定，从而让 dev 模式可平滑降级。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class UserIdentityFilter implements WebFilter {

	public static final String ATTR_HEADER_PRESENT = "userId.from.header";
	public static final String ATTR_HEADER_VALUE = "userId.header.value";

	/**
	 * 缺真实身份（无 X-User-Id 头且非 strict）时的默认身份。
	 * 集中定义以确保入库（RagController.ingest）与检索（RagAdvisorConfig /search）口径一致。
	 */
	public static final String DEFAULT_USER_ID = "system";

	private final AuthProperties authProperties;

	public UserIdentityFilter(AuthProperties authProperties) {
		this.authProperties = authProperties;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String val = exchange.getRequest().getHeaders().getFirst(authProperties.headerName());
		boolean present = val != null && !val.isBlank();
		exchange.getAttributes().put(ATTR_HEADER_PRESENT, present);
		if (present) {
			exchange.getAttributes().put(ATTR_HEADER_VALUE, val);
		} else {
			exchange.getAttributes().remove(ATTR_HEADER_VALUE);
		}
		return chain.filter(exchange);
	}


	/**
	 * 从 exchange attribute 解析身份字符串（不读 body）。
	 *
	 * @param exchange   当前请求交换
	 * @param dtoUserId  请求体中自报的 userId（仅 dev fallback 使用）
	 * @param authProperties 认证配置
	 * @return 解析后的 userId 字符串
	 * @throws ResponseStatusException strict 模式且缺 Header 时返回 401
	 */
	public static String resolveIdentity(ServerWebExchange exchange, String dtoUserId, AuthProperties authProperties) {
		String headerUserId = (String) exchange.getAttributes().get(ATTR_HEADER_VALUE);
		if (headerUserId != null && !headerUserId.isBlank()) {
			return headerUserId;
		}
		if (authProperties.isStrict()) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing " + authProperties.headerName() + " header");
		}
		return (dtoUserId != null && !dtoUserId.isBlank()) ? dtoUserId : DEFAULT_USER_ID;
	}

	/**
	 * 校验当前请求是否为具有管理员身份的用户。
	 *
	 * @param exchange 当前请求交换
	 * @param authProperties 认证配置
	 * @return 解析后的管理员 userId 字符串
	 * @throws ResponseStatusException 未认证抛 401 (UNAUTHORIZED)，非管理员抛 403 (FORBIDDEN)
	 */
	public static String requireAdmin(ServerWebExchange exchange, AuthProperties authProperties) {
		String userId = resolveIdentity(exchange, null, authProperties);
		if (!authProperties.isAdmin(userId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
		}
		return userId;
	}
}

