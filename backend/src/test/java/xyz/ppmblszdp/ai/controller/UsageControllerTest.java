package xyz.ppmblszdp.ai.controller;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.config.AiProviderProperties.MemoryConfig;
import xyz.ppmblszdp.ai.config.AiProviderProperties.UsageQuotaConfig;
import xyz.ppmblszdp.ai.dto.QuotaConfigDto;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;
import xyz.ppmblszdp.ai.repository.UsageRepository;

import java.math.BigDecimal;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UsageController 切片测试：验证 GET/PUT 端点的受信任身份认证与管理员权限控制。
 */
class UsageControllerTest {

	private UsageRepository usageRepository;
	private AiProviderProperties properties;
	private AuthProperties authProperties;
	private UsageController controller;
	private WebTestClient webClient;

	private static final String ADMIN_USER = "admin";
	private static final String REGULAR_USER = "alice";

	@BeforeEach
	void setUp() {
		usageRepository = mock(UsageRepository.class);
		properties = mock(AiProviderProperties.class);
		MemoryConfig memoryConfig = mock(MemoryConfig.class);
		UsageQuotaConfig quotaConfig = mock(UsageQuotaConfig.class);

		when(properties.resolveMemory()).thenReturn(memoryConfig);
		when(memoryConfig.resolveUsageQuota()).thenReturn(quotaConfig);
		when(quotaConfig.resolveMonthlyTokenQuota()).thenReturn(1000000L);


		authProperties = new AuthProperties("strict", "X-User-Id", Set.of("admin", "root"));
		controller = new UsageController(usageRepository, properties, authProperties);

		webClient = WebTestClient.bindToController(controller)
				.webFilter(new UserIdentityFilter(authProperties))
				.build();
	}

	@Test
	void getQuotaConfig_shouldReturn401_whenMissingHeaderInStrictMode() {
		webClient.get().uri("/api/usage/quota-config")
				.exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	void getQuotaConfig_shouldReturn200_whenAuthenticated() {
		QuotaConfigDto config = new QuotaConfigDto(1000000L, 80.0, new BigDecimal("200.00"));
		when(usageRepository.getQuotaConfig(anyLong())).thenReturn(config);

		webClient.get().uri("/api/usage/quota-config")
				.header("X-User-Id", REGULAR_USER)
				.exchange()
				.expectStatus().isOk()
				.expectBody(QuotaConfigDto.class)
				.value(dto -> Assertions.assertThat(dto.monthlyTokenQuota()).isEqualTo(1000000L));
	}

	@Test
	void updateQuotaConfig_shouldReturn401_whenUnauthenticated() {
		QuotaConfigDto updateDto = new QuotaConfigDto(2000000L, 90.0, new BigDecimal("300.00"));

		webClient.put().uri("/api/usage/quota-config")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(updateDto)
				.exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	void updateQuotaConfig_shouldReturn403_whenAuthenticatedUserIsNotAdmin() {
		QuotaConfigDto updateDto = new QuotaConfigDto(2000000L, 90.0, new BigDecimal("300.00"));

		webClient.put().uri("/api/usage/quota-config")
				.header("X-User-Id", REGULAR_USER)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(updateDto)
				.exchange()
				.expectStatus().isForbidden();
	}

	@Test
	void updateQuotaConfig_shouldReturn403_whenHeaderSpoofingAttempted() {
		QuotaConfigDto updateDto = new QuotaConfigDto(2000000L, 90.0, new BigDecimal("300.00"));

		// 附带外部注入的 X-Admin-Id: 1，但 X-User-Id 为普通用户，必须严格拒绝
		webClient.put().uri("/api/usage/quota-config")
				.header("X-User-Id", REGULAR_USER)
				.header("X-Admin-Id", "1")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(updateDto)
				.exchange()
				.expectStatus().isForbidden();
	}

	@Test
	void updateQuotaConfig_shouldReturn200_whenAuthenticatedUserIsAdmin() {
		QuotaConfigDto updateDto = new QuotaConfigDto(2000000L, 90.0, new BigDecimal("300.00"));
		when(usageRepository.getQuotaConfig(anyLong())).thenReturn(updateDto);

		webClient.put().uri("/api/usage/quota-config")
				.header("X-User-Id", ADMIN_USER)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(updateDto)
				.exchange()
				.expectStatus().isOk()
				.expectBody(QuotaConfigDto.class)
				.value(dto -> Assertions.assertThat(dto.monthlyTokenQuota()).isEqualTo(2000000L));

		verify(usageRepository).saveQuotaConfig(updateDto);
	}
}
