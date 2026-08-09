package xyz.ppmblszdp.ai.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import xyz.ppmblszdp.ai.dto.UsageModelSummary;
import xyz.ppmblszdp.ai.dto.UsageMonthlySummary;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsageRepositoryTest {

	private JdbcTemplate jdbcTemplate;
	private UsageRepository usageRepository;

	@BeforeEach
	void setUp() {
		jdbcTemplate = mock(JdbcTemplate.class);
		usageRepository = new UsageRepository(jdbcTemplate);
	}

	@Test
	void saveUsageStoresNonNullCost() {
		usageRepository.saveUsage("user-1", "deepseek", "deepseek-chat", "conv-1",
				10, 20, 30, new BigDecimal("0.0123"), "2026-08");

		ArgumentCaptor<BigDecimal> costCaptor = ArgumentCaptor.forClass(BigDecimal.class);
		verify(jdbcTemplate).update(any(String.class),
				eq("user-1"), eq("deepseek"), eq("deepseek-chat"), eq("conv-1"),
				eq(10), eq(20), eq(30), costCaptor.capture(), eq("2026-08"));
		assertEquals(0, new BigDecimal("0.0123").compareTo(costCaptor.getValue()));
	}

	@Test
	void saveUsageFallsBackToZeroWhenCostIsNull() {
		usageRepository.saveUsage("user-1", "unknown", "unknown-model", null,
				5, 5, 10, null, "2026-08");

		ArgumentCaptor<BigDecimal> costCaptor = ArgumentCaptor.forClass(BigDecimal.class);
		verify(jdbcTemplate).update(any(String.class),
				eq("user-1"), any(), any(), any(),
				eq(5), eq(5), eq(10), costCaptor.capture(), eq("2026-08"));
		assertEquals(0, BigDecimal.ZERO.compareTo(costCaptor.getValue()));
	}

	@Test
	void saveUsageSkipsWhenUserIdBlank() {
		// 缺少用户身份：不应调用 update
		usageRepository.saveUsage("", "deepseek", "deepseek-chat", "conv-1",
				1, 1, 2, BigDecimal.ONE, "2026-08");
		verify(jdbcTemplate, org.mockito.Mockito.never()).update(any(String.class),
				any(), any(), any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	@SuppressWarnings("unchecked")
	void sumUsageByUserAndMonthAggregates() {
		when(jdbcTemplate.query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), any(), any()))
				.thenReturn(List.of(new UsageMonthlySummary(123L, new BigDecimal("0.45"))));

		UsageMonthlySummary result = usageRepository.sumUsageByUserAndMonth("user-1", "2026-08");
		assertEquals(123L, result.totalTokens());
		assertEquals(0, new BigDecimal("0.45").compareTo(result.totalCost()));
	}

	@Test
	@SuppressWarnings("unchecked")
	void sumByModelReturnsGroupedRows() {
		when(jdbcTemplate.query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), any(), any()))
				.thenReturn(List.of(new UsageModelSummary("deepseek-chat", 100L, new BigDecimal("0.30"))));

		List<UsageModelSummary> rows = usageRepository.sumByModelForUserAndMonth("user-1", "2026-08");
		assertEquals(1, rows.size());
		assertEquals("deepseek-chat", rows.get(0).modelId());
		assertEquals(100L, rows.get(0).tokens());
	}
}
