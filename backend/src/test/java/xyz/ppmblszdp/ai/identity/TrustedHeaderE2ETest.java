package xyz.ppmblszdp.ai.identity;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 受信任身份头端到端实证测试。
 *
 * <p>
 * 模拟生产链路：Caddy basic_auth 注入 {@code X-User-Id: alice}（来自
 * {@code {http.auth.user.id}}）→ 前端代理白名单透传并覆盖写入 {@code X-User-Id}
 * → 后端 {@link UserIdentityFilter} 读取该头并经 Controller 调用
 * {@link UserIdentityFilter#resolveIdentity} 解析身份。
 *
 * <p>
 * 本测试聚焦链路末端的身份判定（即 Controller 实际调用点），验证：
 * <ul>
 * <li>strict 模式收到网关注入的 X-User-Id 头 → 正确采信该用户，不采信伪造值；</li>
 * <li>strict 模式缺 X-User-Id 头（修复前 Caddy 误用 {@code header} 响应头指令
 * 导致上游未收到头）→ fail-closed 返回 401；</li>
 * </ul>
 */
class TrustedHeaderE2ETest {

	private final AuthProperties strictAuth = new AuthProperties("strict", "X-User-Id", Set.of("admin"));

	/** 把网关注入的 X-User-Id 写入 exchange attribute（等价于 UserIdentityFilter 的搬运）。 */
	private MockServerWebExchange exchangeWithGatewayHeader(String userId) {
		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.get("/api/sessions").header("X-User-Id", userId).build());
		// UserIdentityFilter.filter 会把头值写入 attribute 供下游解析；此处直接模拟该结果。
		exchange.getAttributes().putIfAbsent(UserIdentityFilter.ATTR_HEADER_VALUE, userId);
		exchange.getAttributes().putIfAbsent(UserIdentityFilter.ATTR_HEADER_PRESENT, Boolean.TRUE);
		return exchange;
	}

	@Test
	void headerInjectedByGateway_shouldBeAccepted_andIgnoreClientSpoofing() {
		MockServerWebExchange exchange = exchangeWithGatewayHeader("real-user-123");

		// Controller 调用 resolveIdentity 解析身份
		String resolved = UserIdentityFilter.resolveIdentity(exchange, "spoofed-by-client", strictAuth);

		// 必须采用 gateway 注入的身份，绝不信任 body 中的 spoofed-by-client
		assertThat(resolved).isEqualTo("real-user-123");
	}

	@Test
	void missingGatewayHeader_inStrictMode_shouldThrow401() {
		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.get("/api/sessions").build());
		exchange.getAttributes().putIfAbsent(UserIdentityFilter.ATTR_HEADER_PRESENT, Boolean.FALSE);

		assertThatThrownBy(() -> UserIdentityFilter.resolveIdentity(exchange, "spoofed", strictAuth))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("401");
	}

	@Test
	void requireAdmin_shouldThrow403_whenUserIsNotAdmin() {
		MockServerWebExchange exchange = exchangeWithGatewayHeader("regular-user");

		assertThatThrownBy(() -> UserIdentityFilter.requireAdmin(exchange, strictAuth))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("403");
	}

	@Test
	void requireAdmin_shouldSucceed_whenUserIsAdmin() {
		MockServerWebExchange exchange = exchangeWithGatewayHeader("admin");

		String resolvedAdmin = UserIdentityFilter.requireAdmin(exchange, strictAuth);
		assertThat(resolvedAdmin).isEqualTo("admin");
	}
}
