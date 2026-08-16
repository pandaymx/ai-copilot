package xyz.ppmblszdp.ai.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.tool.dbquery.ReadOnlyExecutor;
import xyz.ppmblszdp.ai.tool.dbquery.ReadOnlyExecutor.DbQueryResult;
import xyz.ppmblszdp.ai.tool.dbquery.SqlGenerator;
import xyz.ppmblszdp.ai.tool.dbquery.SqlSafetyChecker;

/**
 * 数据库只读查询工具（DatabaseQueryTool）：供 Agent 使用自然语言或直接 SQL 查询数据库。
 *
 * <p>特性：
 * <ul>
 *   <li>NL→SQL 自动转译与 Schema 感知；</li>
 *   <li>多层次只读与 SQL 注入拦截防护（{@link SqlSafetyChecker}）；</li>
 *   <li>只读连接与最大行数截断（{@link ReadOnlyExecutor}）；</li>
 *   <li>统一由 {@link ToolEventEmitter#executeWithEvent} 发送 SSE 进度与结果帧。</li>
 * </ul>
 */
@Component
public class DatabaseQueryTool {

    private static final Logger log = LoggerFactory.getLogger(DatabaseQueryTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_MAX_ROWS = 200;
    private static final int MAX_ALLOW_ROWS = 500;

    private final SqlGenerator sqlGenerator;
    private final SqlSafetyChecker sqlSafetyChecker;
    private final ReadOnlyExecutor readOnlyExecutor;
    private final AiProviderProperties properties;

    public DatabaseQueryTool(
            SqlGenerator sqlGenerator,
            SqlSafetyChecker sqlSafetyChecker,
            ReadOnlyExecutor readOnlyExecutor,
            AiProviderProperties properties) {
        this.sqlGenerator = sqlGenerator;
        this.sqlSafetyChecker = sqlSafetyChecker;
        this.readOnlyExecutor = readOnlyExecutor;
        this.properties = properties;
    }

    @Tool(
            name = "db_query",
            description = "数据库只读查询工具：使用自然语言或直接 SQL 查询数据库中的业务数据。"
                    + "支持结构化返回列定义(columns)、数据行(rows)、行数(rowCount)与执行耗时(executionTimeMs)。"
                    + "仅允许执行安全的只读 SELECT 查询，严禁执行修改或 DDL 语句。")
    public String query(
            @ToolParam(description = "用户的自然语言查询意图或直接 SQL 语句，必填") String question,
            @ToolParam(description = "可选，若已知精确 SQL 可直接传入，否则由系统自动将自然语言转为 SQL") String sql,
            @ToolParam(description = "返回行数上限，默认 200，最大 500") Integer maxRows,
            ToolContext toolContext) {

        int resolvedMaxRows = (maxRows != null && maxRows > 0) ? Math.min(maxRows, MAX_ALLOW_ROWS) : DEFAULT_MAX_ROWS;
        String argsJson = toJson(Map.of(
                "question", question == null ? "" : question,
                "sql", sql == null ? "" : sql,
                "maxRows", resolvedMaxRows));

        return ToolEventEmitter.from(toolContext).executeWithEvent("db_query", argsJson, toolContext, () -> {
            if ((question == null || question.isBlank()) && (sql == null || sql.isBlank())) {
                return toJson(DbQueryResult.error("", "查询参数不能为空，请输入自然语言问题或 SQL", 0));
            }

            String userId = extractUserId(toolContext);
            List<String> allowedTables =
                    properties.resolveAgent().resolveDbQuery().resolveAllowedTables();

            String targetSql = sql;
            if (targetSql == null || targetSql.isBlank()) {
                // 1. 抽取 Schema 并转译 NL 为 SQL
                String schemaInfo = readOnlyExecutor.extractSchemaInfo(allowedTables);
                targetSql = sqlGenerator.generateSql(question, schemaInfo, userId);
            }

            // 2. 前置安全性检查
            sqlSafetyChecker.assertSafe(targetSql, allowedTables);

            // 3. 只读执行
            DbQueryResult result = readOnlyExecutor.execute(targetSql, resolvedMaxRows);
            return toJson(result);
        });
    }

    private String extractUserId(ToolContext toolContext) {
        if (toolContext != null && toolContext.getContext() != null) {
            Object u = toolContext.getContext().get("userId");
            if (u == null) {
                u = toolContext.getContext().get(ToolEventEmitter.CTX_USER_ID);
            }
            if (u instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }

    private String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("DbQueryResult 序列化异常", e);
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }
}
