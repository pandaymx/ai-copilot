package xyz.ppmblszdp.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import xyz.ppmblszdp.ai.identity.AuthProperties;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigurationTest {

	@Test
	void corsWebFilter_WildcardOriginAndNoCredentials_ReturnsExactWildcardOriginHeader() {
		CorsProperties corsProperties = new CorsProperties("*", false, null);
		AiBeansConfiguration beansConfig = new AiBeansConfiguration();
		CorsWebFilter filter = beansConfig.corsWebFilter(corsProperties);

		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.options("http://localhost:8084/api/chat")
						.header("Origin", "http://example.com")
						.header("Access-Control-Request-Method", "POST")
						.build());

		WebFilterChain filterChain = ex -> Mono.empty();

		StepVerifier.create(filter.filter(exchange, filterChain))
				.verifyComplete();

		assertThat(exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Origin"))
				.isEqualTo("*");
		assertThat(exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Credentials"))
				.isNull();
	}

	@Test
	void corsWebFilter_AllowsCustomHeadersSuchAsXUserId() {
		CorsProperties corsProperties = new CorsProperties("*", false, "*");
		AiBeansConfiguration beansConfig = new AiBeansConfiguration();
		CorsWebFilter filter = beansConfig.corsWebFilter(corsProperties);

		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.options("http://localhost:8084/api/chat")
						.header("Origin", "http://example.com")
						.header("Access-Control-Request-Method", "POST")
						.header("Access-Control-Request-Headers", "X-User-Id, Content-Type")
						.build());

		WebFilterChain filterChain = ex -> Mono.empty();

		StepVerifier.create(filter.filter(exchange, filterChain))
				.verifyComplete();

		assertThat(exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Headers"))
				.contains("X-User-Id");
	}

	@Test
	void corsWebFilter_WildcardOriginAndAllowCredentials_ReflectsOrigin() {
		CorsProperties corsProperties = new CorsProperties("*", true, null);
		AiBeansConfiguration beansConfig = new AiBeansConfiguration();
		CorsWebFilter filter = beansConfig.corsWebFilter(corsProperties);

		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.options("http://localhost:8084/api/chat")
						.header("Origin", "http://example.com")
						.header("Access-Control-Request-Method", "POST")
						.build());

		WebFilterChain filterChain = ex -> Mono.empty();

		StepVerifier.create(filter.filter(exchange, filterChain))
				.verifyComplete();

		assertThat(exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Origin"))
				.isEqualTo("http://example.com");
		assertThat(exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Credentials"))
				.isEqualTo("true");
	}

	@Test
	void corsWebFilter_ActualGetRequest_ReturnsCorsHeader() {
		CorsProperties corsProperties = new CorsProperties("*", false, null);
		AiBeansConfiguration beansConfig = new AiBeansConfiguration();
		CorsWebFilter filter = beansConfig.corsWebFilter(corsProperties);

		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.get("http://localhost:8084/api/chat")
						.header("Origin", "http://example.com")
						.build());

		WebFilterChain filterChain = ex -> {
			ex.getResponse().setStatusCode(org.springframework.http.HttpStatus.OK);
			return Mono.empty();
		};

		StepVerifier.create(filter.filter(exchange, filterChain))
				.verifyComplete();

		assertThat(exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Origin"))
				.isEqualTo("*");
	}

	@Test
	void corsProperties_WildcardCheck_DetectsWildcardsCorrectly() {
		CorsProperties wildcardProps = new CorsProperties("*", false, "*");
		assertThat(wildcardProps.hasWildcardOrigin()).isTrue();
		assertThat(wildcardProps.hasWildcardHeader()).isTrue();

		CorsProperties explicitProps = new CorsProperties("http://example.com", true, "X-User-Id");
		assertThat(explicitProps.hasWildcardOrigin()).isFalse();
		assertThat(explicitProps.hasWildcardHeader()).isFalse();
	}

	@Test
	void corsWebFilter_WithStrictModeAndWildcard_ConstructsSuccessfullyWithWarning() {
		CorsProperties corsProperties = new CorsProperties("*", false, "*");
		AuthProperties strictAuth = new AuthProperties("strict", "X-User-Id", java.util.Set
				.of("admin"));
		AiBeansConfiguration beansConfig = new AiBeansConfiguration();

		CorsWebFilter filter = beansConfig.corsWebFilter(corsProperties, strictAuth);
		assertThat(filter).isNotNull();
	}

	@Test
	void hostValidationWebFilter_ValidHost_AllowsChainExecution() {
		CorsProperties corsProperties = new CorsProperties("http://localhost:3000,http://127.0.0.1:3000", false, null);
		AiBeansConfiguration beansConfig = new AiBeansConfiguration();
		org.springframework.web.server.WebFilter hostFilter = beansConfig.hostValidationWebFilter(corsProperties);

		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.get("http://localhost:3000/api/chat")
						.header("Host", "localhost:3000")
						.build());

		WebFilterChain filterChain = ex -> {
			ex.getResponse().setStatusCode(org.springframework.http.HttpStatus.OK);
			return Mono.empty();
		};

		StepVerifier.create(hostFilter.filter(exchange, filterChain))
				.verifyComplete();

		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
	}

	@Test
	void hostValidationWebFilter_InvalidRebindingHost_ReturnsForbidden() {
		CorsProperties corsProperties = new CorsProperties("http://localhost:3000,http://127.0.0.1:3000", false, null);
		AiBeansConfiguration beansConfig = new AiBeansConfiguration();
		org.springframework.web.server.WebFilter hostFilter = beansConfig.hostValidationWebFilter(corsProperties);

		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.get("http://attacker.com/api/chat")
						.header("Host", "attacker.com")
						.build());

		WebFilterChain filterChain = ex -> Mono.empty();

		StepVerifier.create(hostFilter.filter(exchange, filterChain))
				.verifyComplete();

		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
	}
}
