package xyz.ppmblszdp.ai.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.dto.UsageModelSummary;
import xyz.ppmblszdp.ai.dto.UsageSummaryDto;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;
import xyz.ppmblszdp.ai.memory.UsageQuotaChecker;
import xyz.ppmblszdp.ai.repository.UsageRepository;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用量计量查询 Controller（GET /api/usage）。
 *
 * <p>用户身份严格来自上游 {@link UserIdentityFilter} 写入 {@code X-User-Id} 的受信任属性，
 * 绝不接受前端 query/body 传入的任意 userId，确保多租户数据隔离、不越权。
 */
@RestController
@RequestMapping("/api/usage")
public class UsageController {

	private final UsageRepository usageRepository;
	private final AiProviderProperties properties;
	private final AuthProperties authProperties;

	public UsageController(UsageRepository usageRepository, AiProviderProperties properties, AuthProperties authProperties) {
		this.usageRepository = usageRepository;
		this.properties = properties;
		this.authProperties = authProperties;
	}

	/**
	 * 返回当前用户本月用量统计与配额剩余。
	 *
	 * <p>月度 key 由服务端按当前日期计算（{@code yyyy-MM}），查询聚合走
	 * {@code (user_id, created_month)} 索引。
	 */
	@GetMapping
	public ResponseEntity<UsageSummaryDto> getUsage(ServerWebExchange exchange) {
		String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
		String monthKey = UsageQuotaChecker.currentMonthKey();

		long quotaTokens = properties.resolveMemory().resolveUsageQuota().resolveMonthlyTokenQuota();
		var monthly = usageRepository.sumUsageByUserAndMonth(userId, monthKey);
		List<UsageModelSummary> byModel = usageRepository.sumByModelForUserAndMonth(userId, monthKey);

		long totalTokens = monthly.totalTokens();
		double usedPercent = (quotaTokens > 0)
				? Math.min(100.0, (totalTokens * 100.0) / quotaTokens)
				: 0.0;
		long remainingTokens = (quotaTokens > 0)
				? Math.max(0, quotaTokens - totalTokens)
				: totalTokens;

		UsageSummaryDto dto = new UsageSummaryDto(
				monthKey,
				totalTokens,
				monthly.totalCost() != null ? monthly.totalCost() : BigDecimal.ZERO,
				quotaTokens,
				remainingTokens,
				usedPercent,
				byModel
		);
		return ResponseEntity.ok(dto);
	}
}
