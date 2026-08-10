package xyz.ppmblszdp.ai.identity;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 受信任身份头端到端实证测试。
 *
 * <p>模拟生产链路：Caddy basic_auth 注入 {@code X-User-Id: alice}（来自
 * {@code {http.auth.user.id}}）→ 前端代理白名单透传并覆盖写入 {@code X-User-Id}
 * → 后端 {@link UserIdentityFilter} 读取该头并经 Controller 调用
 * {@link UserIdentityFilter#resolveIdentity} 解析身份。
 *
 * <p>本测试聚焦链路末端的身份判定（即 Controller 实际调用点），验证：
 * <ul>
 *   <li>strict 模式收到网关注入的 X-User-Id 头 → 正确采信该用户，不采信伪造值；</li>
 *   <li>strict 模式缺 X-User-Id 头（修复前 Caddy 误用 {@code header} 响应头指令
 *       导致上游未收到头）→ fail-closed 返回 401；</li>
 * </ul>
 */
class TrustedHeaderE2ETest {

	private final AuthProperties strictAuth = new AuthProperties("strict", "X-User-Id");

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
	void strictModeWithGatewayInjectedHeaderResolvesIdentity() {
		MockServerWebExchange exchange = exchangeWithGatewayHeader("alice");
		// 即便客户端伪造了 body 中的 userId，后端也必须采信网关注入的 X-User-Id。
		StepVerifier.create(Mono.fromCallable(() ->
						UserIdentityFilter.resolveIdentity(exchange, "spoofed-by-client", strictAuth)))
				.assertNext(identity -> assertEquals("alice", identity))
				.verifyComplete();
	}

	@Test
	void strictModeMissingHeaderReturns401() {
		// 模拟修复前故障：Caddy 误用 `header` 响应头指令，上游未收到 X-User-Id。
		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.get("/api/sessions").build());
		// filter 记录 header 缺失（attribute 不写入值），Controller 调用 resolveIdentity。
		exchange.getAttributes().putIfAbsent(UserIdentityFilter.ATTR_HEADER_PRESENT, Boolean.FALSE);
		StepVerifier.create(Mono.fromCallable(() ->
						UserIdentityFilter.resolveIdentity(exchange, "spoofed", strictAuth)))
				.expectErrorMatches(t -> t instanceof ResponseStatusException ex
						&& ex.getStatusCode() == HttpStatus.UNAUTHORIZED)
				.verify();
	}
}
