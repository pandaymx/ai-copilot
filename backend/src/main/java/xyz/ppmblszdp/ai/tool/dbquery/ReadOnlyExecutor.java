package xyz.ppmblszdp.ai.tool.dbquery;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 数据库只读执行器（ReadOnlyExecutor）：在受限只读连接与行数限制下执行 SQL 查询并封装结构化结果。
 *
 * <p>连接策略：
 * <ul>
 *   <li>若配置了 {@code spring.datasource.readonly.url}，则建立独立只读副本连接池；</li>
 *   <li>若未配置，直接复用主库 {@link JdbcTemplate} 并在获取连接时强制设置 {@code Connection.setReadOnly(true)}。</li>
 * </ul>
 */
@Component
public class ReadOnlyExecutor {

    private static final Logger log = LoggerFactory.getLogger(ReadOnlyExecutor.class);

    private final JdbcTemplate primaryJdbcTemplate;

    @Value("${spring.datasource.readonly.url:#{null}}")
    private String readOnlyUrl;

    @Value("${spring.datasource.readonly.username:#{null}}")
    private String readOnlyUsername;

    @Value("${spring.datasource.readonly.password:#{null}}")
    private String readOnlyPassword;

    @Value("${spring.datasource.readonly.driver-class-name:org.postgresql.Driver}")
    private String driverClassName;

    private HikariDataSource customReadOnlyDataSource;
    private JdbcTemplate targetJdbcTemplate;

    public ReadOnlyExecutor(JdbcTemplate primaryJdbcTemplate) {
        this.primaryJdbcTemplate = primaryJdbcTemplate;
    }

    @PostConstruct
    public void init() {
        if (readOnlyUrl != null && !readOnlyUrl.isBlank()) {
            log.info("✅ ReadOnlyExecutor 初始化独立只读数据库连接池 → url={}", readOnlyUrl);
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(readOnlyUrl);
            if (readOnlyUsername != null) {
                config.setUsername(readOnlyUsername);
            }
            if (readOnlyPassword != null) {
                config.setPassword(readOnlyPassword);
            }
            config.setDriverClassName(driverClassName);
            config.setReadOnly(true);
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setPoolName("HikariPool-ReadOnly");
            this.customReadOnlyDataSource = new HikariDataSource(config);
            this.targetJdbcTemplate = new JdbcTemplate(this.customReadOnlyDataSource);
        } else {
            this.targetJdbcTemplate = this.primaryJdbcTemplate;
        }
        this.targetJdbcTemplate.setQueryTimeout(15);
        this.targetJdbcTemplate.setFetchSize(200);
    }

    @PreDestroy
    public void destroy() {
        if (customReadOnlyDataSource != null && !customReadOnlyDataSource.isClosed()) {
            customReadOnlyDataSource.close();
        }
    }

    /**
     * 执行只读 SQL 并返回结构化结果。
     *
     * @param sql     校验通过的只读 SQL
     * @param maxRows 最大允许返回行数
     * @return 结构化查询结果
     */
    public DbQueryResult execute(String sql, int maxRows) {
        long startTime = System.currentTimeMillis();
        int limit = (maxRows > 0) ? maxRows : 200;

        try {
            return targetJdbcTemplate.query(
                    con -> {
                        con.setReadOnly(true);
                        var stmt = con.prepareStatement(sql);
                        stmt.setMaxRows(limit + 1);
                        stmt.setQueryTimeout(15);
                        return stmt;
                    },
                    rs -> {
                        ResultSetMetaData meta = rs.getMetaData();
                        int colCount = meta.getColumnCount();
                        List<String> columns = new ArrayList<>(colCount);
                        for (int i = 1; i <= colCount; i++) {
                            columns.add(meta.getColumnLabel(i));
                        }

                        List<Map<String, Object>> rows = new ArrayList<>();
                        boolean truncated = false;

                        while (rs.next()) {
                            if (rows.size() >= limit) {
                                truncated = true;
                                break;
                            }
                            Map<String, Object> row = new LinkedHashMap<>();
                            for (int i = 1; i <= colCount; i++) {
                                String colName = columns.get(i - 1);
                                Object val = rs.getObject(i);
                                row.put(colName, formatValue(val));
                            }
                            rows.add(row);
                        }

                        long duration = System.currentTimeMillis() - startTime;
                        return DbQueryResult.success(sql, columns, rows, rows.size(), truncated, duration);
                    });
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("SQL 执行失败: sql=[{}], error={}", sql, e.getMessage());
            return DbQueryResult.error(sql, e.getMessage(), duration);
        }
    }

    /**
     * 获取数据库可见表和列结构的简化描述，供 NL→SQL 提示词使用。
     */
    public String extractSchemaInfo(List<String> allowedTables) {
        try {
            String tableFilter = (allowedTables != null && !allowedTables.isEmpty())
                    ? "AND table_name IN ('" + String.join("','", allowedTables) + "')"
                    : "AND table_schema = 'public'";

            String query = """
                    SELECT table_name, column_name, data_type
                    FROM information_schema.columns
                    WHERE table_schema NOT IN ('information_schema', 'pg_catalog')
                    """ + tableFilter + """
                     ORDER BY table_name, ordinal_position
                    LIMIT 200
                    """;

            List<Map<String, Object>> rows = targetJdbcTemplate.queryForList(query);
            if (rows.isEmpty()) {
                return "暂无可查询的公开表结构";
            }

            Map<String, List<String>> tableCols = new LinkedHashMap<>();
            for (Map<String, Object> r : rows) {
                String tbl = String.valueOf(r.get("table_name"));
                String col = String.valueOf(r.get("column_name")) + " (" + String.valueOf(r.get("data_type")) + ")";
                tableCols.computeIfAbsent(tbl, k -> new ArrayList<>()).add(col);
            }

            StringBuilder sb = new StringBuilder();
            tableCols.forEach((tbl, cols) -> {
                sb.append("Table ")
                        .append(tbl)
                        .append(":\n  - ")
                        .append(String.join("\n  - ", cols))
                        .append("\n\n");
            });
            return sb.toString().trim();
        } catch (Exception e) {
            log.debug("提取数据库 schema 失败: {}", e.getMessage());
            return "数据库架构信息自动获取不可用";
        }
    }

    private Object formatValue(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof java.sql.Timestamp
                || val instanceof java.sql.Date
                || val instanceof java.time.temporal.Temporal) {
            return val.toString();
        }
        if (val instanceof byte[]) {
            return "[Binary " + ((byte[]) val).length + " bytes]";
        }
        return val;
    }

    /**
     * 结构化数据库查询结果 DTO。
     */
    public record DbQueryResult(
            boolean success,
            String sql,
            List<String> columns,
            List<Map<String, Object>> rows,
            int rowCount,
            boolean truncated,
            long executionTimeMs,
            String error) {

        public static DbQueryResult success(
                String sql,
                List<String> columns,
                List<Map<String, Object>> rows,
                int rowCount,
                boolean truncated,
                long executionTimeMs) {
            return new DbQueryResult(true, sql, columns, rows, rowCount, truncated, executionTimeMs, null);
        }

        public static DbQueryResult error(String sql, String error, long executionTimeMs) {
            return new DbQueryResult(false, sql, List.of(), List.of(), 0, false, executionTimeMs, error);
        }
    }
}
