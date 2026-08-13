package xyz.ppmblszdp.ai.repository;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import xyz.ppmblszdp.ai.dto.QuotaConfigDto;
import xyz.ppmblszdp.ai.dto.UsageDailySummary;
import xyz.ppmblszdp.ai.dto.UsageModelDetailSummary;
import xyz.ppmblszdp.ai.dto.UsageModelSummary;
import xyz.ppmblszdp.ai.dto.UsageMonthlySummary;
import xyz.ppmblszdp.ai.dto.UsageUserSummary;

/**
 * 对话 Token 用量与 Cost 计量 Repository（基于 JdbcTemplate 与 PostgreSQL 落盘存储）。
 *
 * <p>每一次对话成功返回后都会落库一条用量记录，按 {@code user_id} + {@code created_month}
 * 做多租户隔离与月度聚合。{@code created_month} 为冗余列（如 {@code 2026-08}），使月度聚合
 * SQL 无需对时间戳做函数转换，聚合查询直接走 {@code (user_id, created_month)} 复合索引。
 *
 * <p>成本字段 {@code cost_rmb} 在落库时兜底为 {@link BigDecimal#ZERO}，避免部分未定义单价的
 * 模型产生 NPE 或 DB 写入 NULL。
 */
@Repository
public class UsageRepository {

    private static final Logger log = LoggerFactory.getLogger(UsageRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public UsageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<UsageMonthlySummary> MONTHLY_ROW_MAPPER =
            (rs, rowNum) -> new UsageMonthlySummary(rs.getLong("total_tokens"), rs.getBigDecimal("total_cost"));

    private static final RowMapper<UsageModelSummary> MODEL_ROW_MAPPER = (rs, rowNum) ->
            new UsageModelSummary(rs.getString("model_id"), rs.getLong("tokens"), rs.getBigDecimal("cost"));

    private static final RowMapper<UsageUserSummary> USER_SUMMARY_ROW_MAPPER = (rs, rowNum) -> new UsageUserSummary(
            rs.getString("user_id"),
            rs.getLong("prompt_tokens"),
            rs.getLong("completion_tokens"),
            rs.getLong("total_tokens"),
            rs.getBigDecimal("total_cost"),
            rs.getLong("request_count"));

    private static final RowMapper<UsageModelDetailSummary> MODEL_DETAIL_ROW_MAPPER =
            (rs, rowNum) -> new UsageModelDetailSummary(
                    rs.getString("model_id"),
                    rs.getString("provider_id"),
                    rs.getLong("prompt_tokens"),
                    rs.getLong("completion_tokens"),
                    rs.getLong("total_tokens"),
                    rs.getBigDecimal("total_cost"),
                    rs.getLong("request_count"));

    private static final RowMapper<UsageDailySummary> DAILY_SUMMARY_ROW_MAPPER = (rs, rowNum) -> new UsageDailySummary(
            rs.getString("day"),
            rs.getLong("total_tokens"),
            rs.getBigDecimal("total_cost"),
            rs.getLong("request_count"));

    @PostConstruct
    public void initSchema() {
        try {
            jdbcTemplate.execute("""
					CREATE TABLE IF NOT EXISTS usage_record (
						id BIGSERIAL PRIMARY KEY,
						user_id VARCHAR(128) NOT NULL,
						provider_id VARCHAR(128),
						model_id VARCHAR(128),
						conversation_id VARCHAR(128),
						prompt_tokens INT NOT NULL DEFAULT 0,
						completion_tokens INT NOT NULL DEFAULT 0,
						total_tokens INT NOT NULL DEFAULT 0,
						cost_rmb NUMERIC(12,4) NOT NULL DEFAULT 0,
						created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
						created_month VARCHAR(7) NOT NULL
					);
					""");
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_usage_user_month ON usage_record(user_id, created_month);");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_usage_month ON usage_record(created_month);");

            jdbcTemplate.execute("""
					CREATE TABLE IF NOT EXISTS usage_quota_config (
						config_key VARCHAR(64) PRIMARY KEY,
						config_value TEXT NOT NULL,
						updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
					);
					""");
            log.info("PostgreSQL 用量计量表 'usage_record' 与 'usage_quota_config' 初始化/校验成功");
        } catch (Exception ex) {
            log.error("初始化 PostgreSQL 用量计量表失败: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 落库一次对话的用量记录。costRmb 为 NULL 时兜底为 ZERO。
     *
     * @param userId          服务端受信任用户身份（多租户隔离键）
     * @param providerId      供应商 id（可能为空）
     * @param modelId         模型 id（可能为空）
     * @param conversationId  会话 id（单轮/非记忆路径可能为空）
     * @param promptTokens    prompt token 数
     * @param completionTokens completion token 数
     * @param totalTokens     总 token 数
     * @param costRmb         本次费用（元），可能为空
     * @param createdMonth    冗余月份列，格式 {@code yyyy-MM}（由服务端按当前时区计算）
     */
    public void saveUsage(
            String userId,
            String providerId,
            String modelId,
            String conversationId,
            int promptTokens,
            int completionTokens,
            int totalTokens,
            BigDecimal costRmb,
            String createdMonth) {
        if (userId == null || userId.isBlank()) {
            log.warn("跳过缺少用户身份的用量落库请求");
            return;
        }
        if (createdMonth == null || createdMonth.isBlank()) {
            log.warn("跳过缺少月份信息的用量落库请求 [user={}]", userId);
            return;
        }
        BigDecimal safeCost = (costRmb == null) ? BigDecimal.ZERO : costRmb;
        String sql = """
				INSERT INTO usage_record
					(user_id, provider_id, model_id, conversation_id, prompt_tokens, completion_tokens, total_tokens, cost_rmb, created_month)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
				""";
        try {
            jdbcTemplate.update(
                    sql,
                    userId,
                    providerId,
                    modelId,
                    conversationId,
                    promptTokens,
                    completionTokens,
                    totalTokens,
                    safeCost,
                    createdMonth);
        } catch (Exception ex) {
            // 用量落库失败不影响对话主链路，仅告警
            log.warn("用量记录落库失败 [user={}, model={}]: {}", userId, modelId, ex.getMessage());
        }
    }

    /**
     * 查询用户指定月份的累计用量与费用总和（不按模型拆分）。
     */
    public UsageMonthlySummary sumUsageByUserAndMonth(String userId, String monthKey) {
        String sql = """
				SELECT COALESCE(SUM(total_tokens), 0) AS total_tokens,
				       COALESCE(SUM(cost_rmb), 0) AS total_cost
				FROM usage_record
				WHERE user_id = ? AND created_month = ?;
				""";
        return jdbcTemplate.query(sql, MONTHLY_ROW_MAPPER, userId, monthKey).stream()
                .findFirst()
                .orElse(new UsageMonthlySummary(0L, BigDecimal.ZERO));
    }

    /**
     * 查询用户指定月份按模型分组的用量与费用明细。
     */
    public List<UsageModelSummary> sumByModelForUserAndMonth(String userId, String monthKey) {
        String sql = """
				SELECT COALESCE(model_id, 'unknown') AS model_id,
				       COALESCE(SUM(total_tokens), 0) AS tokens,
				       COALESCE(SUM(cost_rmb), 0) AS cost
				FROM usage_record
				WHERE user_id = ? AND created_month = ?
				GROUP BY model_id
				ORDER BY tokens DESC;
				""";
        return jdbcTemplate.query(sql, MODEL_ROW_MAPPER, userId, monthKey);
    }

    /**
     * 看板聚合：按用户汇总指定月份的 Token 与费用排行榜。
     */
    public List<UsageUserSummary> sumByUsersForMonth(String monthKey) {
        String sql = """
				SELECT user_id,
				       COALESCE(SUM(prompt_tokens), 0) AS prompt_tokens,
				       COALESCE(SUM(completion_tokens), 0) AS completion_tokens,
				       COALESCE(SUM(total_tokens), 0) AS total_tokens,
				       COALESCE(SUM(cost_rmb), 0) AS total_cost,
				       COUNT(*) AS request_count
				FROM usage_record
				WHERE created_month = ?
				GROUP BY user_id
				ORDER BY total_tokens DESC;
				""";
        return jdbcTemplate.query(sql, USER_SUMMARY_ROW_MAPPER, monthKey);
    }

    /**
     * 看板聚合：按模型与供应商汇总指定月份的 Token 与费用明细。
     */
    public List<UsageModelDetailSummary> sumByModelsForMonth(String monthKey) {
        String sql = """
				SELECT COALESCE(model_id, 'unknown') AS model_id,
				       COALESCE(provider_id, 'unknown') AS provider_id,
				       COALESCE(SUM(prompt_tokens), 0) AS prompt_tokens,
				       COALESCE(SUM(completion_tokens), 0) AS completion_tokens,
				       COALESCE(SUM(total_tokens), 0) AS total_tokens,
				       COALESCE(SUM(cost_rmb), 0) AS total_cost,
				       COUNT(*) AS request_count
				FROM usage_record
				WHERE created_month = ?
				GROUP BY model_id, provider_id
				ORDER BY total_tokens DESC;
				""";
        return jdbcTemplate.query(sql, MODEL_DETAIL_ROW_MAPPER, monthKey);
    }

    /**
     * 看板聚合：按日汇总指定月份的 Token 与费用趋势。
     */
    public List<UsageDailySummary> sumDailyTrendForMonth(String monthKey) {
        String sql = """
				SELECT TO_CHAR(created_at, 'YYYY-MM-DD') AS day,
				       COALESCE(SUM(total_tokens), 0) AS total_tokens,
				       COALESCE(SUM(cost_rmb), 0) AS total_cost,
				       COUNT(*) AS request_count
				FROM usage_record
				WHERE created_month = ?
				GROUP BY TO_CHAR(created_at, 'YYYY-MM-DD')
				ORDER BY day ASC;
				""";
        return jdbcTemplate.query(sql, DAILY_SUMMARY_ROW_MAPPER, monthKey);
    }

    /**
     * 获取配额与告警阈值配置（不存在时返回默认配额）。
     */
    public QuotaConfigDto getQuotaConfig(long defaultMonthlyQuota) {
        try {
            String sql = "SELECT config_key, config_value FROM usage_quota_config;";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            Map<String, String> map = rows.stream()
                    .collect(Collectors.toMap(r -> (String) r.get("config_key"), r -> (String) r.get("config_value")));

            long monthlyQuota = map.containsKey("monthlyTokenQuota")
                    ? Long.parseLong(map.get("monthlyTokenQuota"))
                    : defaultMonthlyQuota;
            double alertPercent = map.containsKey("alertThresholdPercent")
                    ? Double.parseDouble(map.get("alertThresholdPercent"))
                    : 80.0;
            BigDecimal costQuota = map.containsKey("monthlyCostQuotaRmb")
                    ? new BigDecimal(map.get("monthlyCostQuotaRmb"))
                    : BigDecimal.ZERO;

            return new QuotaConfigDto(monthlyQuota, alertPercent, costQuota);
        } catch (Exception ex) {
            log.warn("读取配额阈值配置失败，使用默认配置: {}", ex.getMessage());
            return new QuotaConfigDto(defaultMonthlyQuota, 80.0, BigDecimal.ZERO);
        }
    }

    /**
     * 保存/更新配额与告警阈值配置。
     */
    public void saveQuotaConfig(QuotaConfigDto config) {
        if (config == null) return;
        String sql = """
				INSERT INTO usage_quota_config (config_key, config_value, updated_at)
				VALUES (?, ?, now())
				ON CONFLICT (config_key) DO UPDATE
				SET config_value = EXCLUDED.config_value, updated_at = now();
				""";
        try {
            jdbcTemplate.update(sql, "monthlyTokenQuota", String.valueOf(config.monthlyTokenQuota()));
            jdbcTemplate.update(sql, "alertThresholdPercent", String.valueOf(config.alertThresholdPercent()));
            jdbcTemplate.update(
                    sql,
                    "monthlyCostQuotaRmb",
                    config.monthlyCostQuotaRmb() != null
                            ? config.monthlyCostQuotaRmb().toString()
                            : "0");
            log.info(
                    "更新配额阈值配置成功: monthlyQuota={}, alertPercent={}, costQuota={}",
                    config.monthlyTokenQuota(),
                    config.alertThresholdPercent(),
                    config.monthlyCostQuotaRmb());
        } catch (Exception ex) {
            log.error("保存配额阈值配置失败: {}", ex.getMessage(), ex);
        }
    }
}
