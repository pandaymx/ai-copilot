package xyz.ppmblszdp.ai.service;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;

import java.time.Instant;

import static org.mockito.Mockito.mock;

class MemoryForgetServiceTest {

	private MemoryForgetService forgetService;

	@BeforeEach
	void setUp() {
		ProviderRegistry registry = mock(ProviderRegistry.class);
		forgetService = new MemoryForgetService(registry);
	}

	@Test
	void calculatePriorityScore_recentAccess_shouldKeepHighScore() {
		String nowIso = Instant.now().toString();
		double score = forgetService.calculatePriorityScore(1.0, 5, nowIso, nowIso);
		Assertions.assertThat(score).isEqualTo(1.5);
	}

	@Test
	void calculatePriorityScore_oldAccess_shouldDecayScore() {
		Instant past = Instant.now().minusSeconds(86400 * 30); // 30 天前
		String pastIso = past.toString();
		double score = forgetService.calculatePriorityScore(1.0, 0, pastIso, pastIso);
		// 1.0 * e^(-0.05 * 30) = 1.0 * e^(-1.5) ≈ 0.22
		Assertions.assertThat(score).isLessThan(0.3);
	}
}
