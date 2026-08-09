package xyz.ppmblszdp.ai.repository;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import xyz.ppmblszdp.ai.dto.UsageModelSummary;
import xyz.ppmblszdp.ai.dto.UsageMonthlySummary;

import java.math.BigDecimal;
import java.util.List;

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

	private static final RowMapper<UsageMonthlySummary> MONTHLY_ROW_MAPPER = (rs, rowNum) -> new UsageMonthlySummary(
			rs.getLong("total_tokens"),
			rs.getBigDecimal("total_cost")
	);

	private static final RowMapper<UsageModelSummary> MODEL_ROW_MAPPER = (rs, rowNum) -> new UsageModelSummary(
			rs.getString("model_id"),
			rs.getLong("tokens"),
			rs.getBigDecimal("cost")
	);

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
			jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_usage_user_month ON usage_record(user_id, created_month);");
			log.info("PostgreSQL 用量计量表 'usage_record' 初始化/校验成功");
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
					createdMonth
			);
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
		return jdbcTemplate.query(sql, MONTHLY_ROW_MAPPER, userId, monthKey)
				.stream().findFirst()
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
}
