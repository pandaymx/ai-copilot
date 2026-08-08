package xyz.ppmblszdp.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigurationTest {

	@Test
	void corsWebFilter_WildcardOriginAndNoCredentials_ReturnsExactWildcardOriginHeader() {
		CorsProperties corsProperties = new CorsProperties("*", false);
		AiBeansConfiguration beansConfig = new AiBeansConfiguration();
		CorsWebFilter filter = beansConfig.corsWebFilter(corsProperties);

		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.options("http://localhost:8084/api/chat")
						.header("Origin", "http://example.com")
						.header("Access-Control-Request-Method", "POST")
						.build()
		);

		WebFilterChain filterChain = ex -> Mono.empty();

		StepVerifier.create(filter.filter(exchange, filterChain))
				.verifyComplete();

		assertThat(exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Origin"))
				.isEqualTo("*");
		assertThat(exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Credentials"))
				.isNull();
	}

	@Test
	void corsWebFilter_WildcardOriginAndAllowCredentials_ReflectsOrigin() {
		CorsProperties corsProperties = new CorsProperties("*", true);
		AiBeansConfiguration beansConfig = new AiBeansConfiguration();
		CorsWebFilter filter = beansConfig.corsWebFilter(corsProperties);

		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.options("http://localhost:8084/api/chat")
						.header("Origin", "http://example.com")
						.header("Access-Control-Request-Method", "POST")
						.build()
		);

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
		CorsProperties corsProperties = new CorsProperties("*", false);
		AiBeansConfiguration beansConfig = new AiBeansConfiguration();
		CorsWebFilter filter = beansConfig.corsWebFilter(corsProperties);

		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.get("http://localhost:8084/api/chat")
						.header("Origin", "http://example.com")
						.build()
		);

		WebFilterChain filterChain = ex -> {
			ex.getResponse().setStatusCode(org.springframework.http.HttpStatus.OK);
			return Mono.empty();
		};

		StepVerifier.create(filter.filter(exchange, filterChain))
				.verifyComplete();

		assertThat(exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Origin"))
				.isEqualTo("*");
	}
}
