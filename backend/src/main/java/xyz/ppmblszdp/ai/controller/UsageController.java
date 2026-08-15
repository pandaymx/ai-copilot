package xyz.ppmblszdp.ai.controller;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.dto.QuotaConfigDto;
import xyz.ppmblszdp.ai.dto.RateLimitStatusDto;
import xyz.ppmblszdp.ai.dto.RealtimeUsageDto;
import xyz.ppmblszdp.ai.dto.UsageDailySummary;
import xyz.ppmblszdp.ai.dto.UsageDashboardDto;
import xyz.ppmblszdp.ai.dto.UsageModelDetailSummary;
import xyz.ppmblszdp.ai.dto.UsageModelSummary;
import xyz.ppmblszdp.ai.dto.UsageSummaryDto;
import xyz.ppmblszdp.ai.dto.UsageUserSummary;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;
import xyz.ppmblszdp.ai.memory.ChatRateLimiter;
import xyz.ppmblszdp.ai.memory.ChatRateLimiter.RateLimiter;
import xyz.ppmblszdp.ai.memory.UsageQuotaChecker;
import xyz.ppmblszdp.ai.memory.UsageQuotaChecker.UsageQuota;
import xyz.ppmblszdp.ai.repository.UsageRepository;

/**
 * 用量计量与成本看板 Controller（/api/usage）。
 *
 * <p>用户身份严格来自上游 {@link UserIdentityFilter} 写入 {@code X-User-Id} 的受信任属性。
 */
@RestController
@RequestMapping("/api/usage")
public class UsageController {

    private final UsageRepository usageRepository;
    private final AiProviderProperties properties;
    private final AuthProperties authProperties;
    private final ObjectProvider<UsageQuota> usageQuotaProvider;
    private final ObjectProvider<RateLimiter> rateLimiterProvider;

    @org.springframework.beans.factory.annotation.Autowired
    public UsageController(
            UsageRepository usageRepository,
            AiProviderProperties properties,
            AuthProperties authProperties,
            ObjectProvider<UsageQuota> usageQuotaProvider,
            ObjectProvider<RateLimiter> rateLimiterProvider) {
        this.usageRepository = usageRepository;
        this.properties = properties;
        this.authProperties = authProperties;
        this.usageQuotaProvider = usageQuotaProvider;
        this.rateLimiterProvider = rateLimiterProvider;
    }

    /**
     * 兼容旧版 4 参数构造器。
     */
    public UsageController(
            UsageRepository usageRepository,
            AiProviderProperties properties,
            AuthProperties authProperties,
            ObjectProvider<UsageQuota> usageQuotaProvider) {
        this(usageRepository, properties, authProperties, usageQuotaProvider, null);
    }

    /**
     * 返回当前用户的短时窗口限流状态与月度配额概览（用于聊天界面实时预警与倒计时）。
     */
    @GetMapping("/rate-limit-status")
    public ResponseEntity<RateLimitStatusDto> getRateLimitStatus(ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);

        RateLimiter limiter = rateLimiterProvider != null ? rateLimiterProvider.getIfAvailable() : null;
        ChatRateLimiter.WindowQuotaDto windowStatus;
        if (limiter != null) {
            windowStatus = limiter.getQuotaStatus(userId);
        } else {
            int cap = 20;
            int win = 60;
            if (properties != null
                    && properties.resolveMemory() != null
                    && properties.resolveMemory().resolveRateLimit() != null) {
                cap = properties.resolveMemory().resolveRateLimit().resolveCapacity();
                win = properties.resolveMemory().resolveRateLimit().resolveRefillSeconds();
            }
            windowStatus = new ChatRateLimiter.WindowQuotaDto(cap, cap, win, 0, System.currentTimeMillis());
        }

        UsageQuota quota = usageQuotaProvider != null ? usageQuotaProvider.getIfAvailable() : null;
        long monthlyRemaining;
        long monthlyQuota;
        double monthlyUsedPercent;

        if (quota != null) {
            RealtimeUsageDto rt = quota.getRealtimeUsage(userId, 80.0);
            monthlyRemaining = rt.remainingTokens();
            monthlyQuota = rt.quotaTokens();
            monthlyUsedPercent = rt.usedPercent();
        } else {
            monthlyQuota = properties.resolveMemory().resolveUsageQuota().resolveMonthlyTokenQuota();
            monthlyRemaining = monthlyQuota;
            monthlyUsedPercent = 0.0;
        }

        RateLimitStatusDto dto = RateLimitStatusDto.of(
                windowStatus.remaining(),
                windowStatus.capacity(),
                windowStatus.windowSeconds(),
                windowStatus.resetAfterSeconds(),
                windowStatus.resetAtMs(),
                monthlyRemaining,
                monthlyQuota,
                monthlyUsedPercent);

        return ResponseEntity.ok(dto);
    }

    /**
     * 返回当前用户本月实时配额与消耗状态（基于 Redis 实时数据，不查 DB）。
     */
    @GetMapping("/realtime")
    public ResponseEntity<RealtimeUsageDto> getRealtimeUsage(ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        UsageQuota quota = usageQuotaProvider != null ? usageQuotaProvider.getIfAvailable() : null;
        if (quota != null) {
            RealtimeUsageDto dto = quota.getRealtimeUsage(userId, 80.0);
            return ResponseEntity.ok(dto);
        }
        long defaultQuota = properties.resolveMemory().resolveUsageQuota().resolveMonthlyTokenQuota();
        RealtimeUsageDto fallback =
                new RealtimeUsageDto(UsageQuotaChecker.currentMonthKey(), 0L, defaultQuota, defaultQuota, 0.0, 80.0);
        return ResponseEntity.ok(fallback);
    }

    /**
     * 返回当前用户本月用量统计与配额剩余。
     */
    @GetMapping
    public ResponseEntity<UsageSummaryDto> getUsage(ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        String monthKey = UsageQuotaChecker.currentMonthKey();

        long defaultQuota = properties.resolveMemory().resolveUsageQuota().resolveMonthlyTokenQuota();
        QuotaConfigDto quotaConfig = usageRepository.getQuotaConfig(defaultQuota);
        long quotaTokens = quotaConfig.monthlyTokenQuota();

        var monthly = usageRepository.sumUsageByUserAndMonth(userId, monthKey);
        List<UsageModelSummary> byModel = usageRepository.sumByModelForUserAndMonth(userId, monthKey);

        long totalTokens = monthly.totalTokens();
        double usedPercent = (quotaTokens > 0) ? Math.min(100.0, (totalTokens * 100.0) / quotaTokens) : 0.0;
        long remainingTokens = (quotaTokens > 0) ? Math.max(0, quotaTokens - totalTokens) : totalTokens;

        UsageSummaryDto dto = new UsageSummaryDto(
                monthKey,
                totalTokens,
                monthly.totalCost() != null ? monthly.totalCost() : BigDecimal.ZERO,
                quotaTokens,
                remainingTokens,
                usedPercent,
                byModel);
        return ResponseEntity.ok(dto);
    }

    /**
     * 看板大盘聚合数据 API（GET /api/usage/dashboard?month=yyyy-MM）。
     * 返回按用户、模型、日期的全局 Token 与费用分布，以及告警触发状态。
     */
    @GetMapping("/dashboard")
    public ResponseEntity<UsageDashboardDto> getDashboard(
            ServerWebExchange exchange, @RequestParam(name = "month", required = false) String monthParam) {
        UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        String monthKey =
                (monthParam != null && !monthParam.isBlank()) ? monthParam.trim() : UsageQuotaChecker.currentMonthKey();

        long defaultQuota = properties.resolveMemory().resolveUsageQuota().resolveMonthlyTokenQuota();
        QuotaConfigDto quotaConfig = usageRepository.getQuotaConfig(defaultQuota);

        List<UsageUserSummary> byUser = usageRepository.sumByUsersForMonth(monthKey);
        List<UsageModelDetailSummary> byModel = usageRepository.sumByModelsForMonth(monthKey);
        List<UsageDailySummary> dailyTrend = usageRepository.sumDailyTrendForMonth(monthKey);

        long totalTokens = byUser.stream().mapToLong(u -> u.totalTokens()).sum();
        BigDecimal totalCost = byUser.stream()
                .map(u -> u.totalCost() != null ? u.totalCost() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));
        long totalRequests = byUser.stream().mapToLong(u -> u.requestCount()).sum();
        long activeUsers = byUser.size();
        long activeModels = byModel.size();

        boolean alertTriggered = false;
        if (quotaConfig.monthlyTokenQuota() > 0) {
            double thresholdTokens = quotaConfig.monthlyTokenQuota() * (quotaConfig.alertThresholdPercent() / 100.0);
            if (totalTokens >= thresholdTokens) {
                alertTriggered = true;
            }
        }
        if (quotaConfig.monthlyCostQuotaRmb() != null
                && quotaConfig.monthlyCostQuotaRmb().compareTo(BigDecimal.ZERO) > 0) {
            if (totalCost.compareTo(quotaConfig.monthlyCostQuotaRmb()) >= 0) {
                alertTriggered = true;
            }
        }

        UsageDashboardDto dto = new UsageDashboardDto(
                monthKey,
                totalTokens,
                totalCost,
                totalRequests,
                activeUsers,
                activeModels,
                byUser,
                byModel,
                dailyTrend,
                quotaConfig,
                alertTriggered);
        return ResponseEntity.ok(dto);
    }

    /**
     * 获取当前配额与告警阈值配置（GET /api/usage/quota-config）。
     */
    @GetMapping("/quota-config")
    public ResponseEntity<QuotaConfigDto> getQuotaConfig(ServerWebExchange exchange) {
        UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        long defaultQuota = properties.resolveMemory().resolveUsageQuota().resolveMonthlyTokenQuota();
        QuotaConfigDto config = usageRepository.getQuotaConfig(defaultQuota);
        return ResponseEntity.ok(config);
    }

    /**
     * 管理员更新配额与告警阈值配置（PUT /api/usage/quota-config）。
     */
    @PutMapping("/quota-config")
    public ResponseEntity<QuotaConfigDto> updateQuotaConfig(
            ServerWebExchange exchange, @RequestBody QuotaConfigDto config) {
        UserIdentityFilter.requireAdmin(exchange, authProperties);
        usageRepository.saveQuotaConfig(config);
        long defaultQuota = properties.resolveMemory().resolveUsageQuota().resolveMonthlyTokenQuota();
        QuotaConfigDto updated = usageRepository.getQuotaConfig(defaultQuota);
        return ResponseEntity.ok(updated);
    }
}
