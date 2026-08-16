package xyz.ppmblszdp.ai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.tool.dbquery.ReadOnlyExecutor;
import xyz.ppmblszdp.ai.tool.dbquery.ReadOnlyExecutor.DbQueryResult;
import xyz.ppmblszdp.ai.tool.dbquery.SqlGenerator;
import xyz.ppmblszdp.ai.tool.dbquery.SqlSafetyChecker;

class DatabaseQueryToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SqlSafetyChecker safetyChecker;
    private ReadOnlyExecutor readOnlyExecutor;
    private SqlGenerator sqlGenerator;
    private AiProviderProperties properties;
    private DatabaseQueryTool databaseQueryTool;

    private ToolEventEmitter emitter;
    private reactor.core.publisher.Sinks.Many<xyz.ppmblszdp.ai.dto.ChatChunkDto> sink;

    @BeforeEach
    void setUp() {
        safetyChecker = new SqlSafetyChecker();
        readOnlyExecutor = mock(ReadOnlyExecutor.class);
        sqlGenerator = mock(SqlGenerator.class);
        properties = mock(AiProviderProperties.class);

        var agentConfig = mock(AiProviderProperties.AgentConfig.class);
        var dbConfig = mock(AiProviderProperties.DbQueryConfig.class);
        when(properties.resolveAgent()).thenReturn(agentConfig);
        when(agentConfig.resolveMaxToolCalls()).thenReturn(5);
        when(agentConfig.resolveTimeoutSeconds()).thenReturn(30);
        when(agentConfig.resolveDbQuery()).thenReturn(dbConfig);
        when(dbConfig.resolveAllowedTables()).thenReturn(List.of());

        emitter = new ToolEventEmitter(properties);
        sink = emitter.newSink();

        databaseQueryTool = new DatabaseQueryTool(sqlGenerator, safetyChecker, readOnlyExecutor, properties);
    }

    @Nested
    @DisplayName("SqlSafetyChecker 安全校验测试")
    class SqlSafetyCheckerTest {

        @Test
        @DisplayName("允许合法的只读 SELECT 查询")
        void allowSafeSelectQueries() {
            safetyChecker.assertSafe("SELECT id, name FROM users WHERE status = 'active' LIMIT 10", null);
            safetyChecker.assertSafe("WITH active_users AS (SELECT * FROM users) SELECT * FROM active_users", null);
            safetyChecker.assertSafe("EXPLAIN ANALYZE SELECT count(*) FROM orders", null);
        }

        @Test
        @DisplayName("禁止写操作 (INSERT/UPDATE/DELETE/DROP/ALTER/TRUNCATE)")
        void forbidWriteAndDdl() {
            assertThatThrownBy(() -> safetyChecker.assertSafe("DROP TABLE users", null))
                    .isInstanceOf(SecurityException.class);

            assertThatThrownBy(() -> safetyChecker.assertSafe("DELETE FROM users WHERE id = 1", null))
                    .isInstanceOf(SecurityException.class);

            assertThatThrownBy(() -> safetyChecker.assertSafe("UPDATE users SET name = 'admin'", null))
                    .isInstanceOf(SecurityException.class);

            assertThatThrownBy(() -> safetyChecker.assertSafe("INSERT INTO logs VALUES ('test')", null))
                    .isInstanceOf(SecurityException.class);

            assertThatThrownBy(() -> safetyChecker.assertSafe("ALTER TABLE users ADD COLUMN hack text", null))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("禁止分号拼接多语句")
        void forbidMultiStatements() {
            assertThatThrownBy(() -> safetyChecker.assertSafe("SELECT 1; DROP TABLE users;", null))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("复合 SQL");
        }

        @Test
        @DisplayName("禁止访问核心系统表")
        void forbidSystemTables() {
            assertThatThrownBy(() -> safetyChecker.assertSafe("SELECT * FROM pg_shadow", null))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("白名单模式下拦截未授权表")
        void enforceTableWhitelist() {
            List<String> allowed = List.of("orders", "products");

            // 允许白名单内的表
            safetyChecker.assertSafe("SELECT * FROM orders JOIN products ON orders.pid = products.id", allowed);

            // 拦截白名单外的表
            assertThatThrownBy(() -> safetyChecker.assertSafe("SELECT * FROM users", allowed))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("白名单");
        }
    }

    @Nested
    @DisplayName("DatabaseQueryTool 工具调用测试")
    class ToolExecutionTest {

        @Test
        @DisplayName("直接执行合法 SQL 并返回结构化结果")
        void executeDirectSql() throws Exception {
            String sql = "SELECT id, title FROM sessions WHERE user_id = 'user-1'";
            DbQueryResult mockResult = DbQueryResult.success(
                    sql,
                    List.of("id", "title"),
                    List.of(Map.of("id", "s-1", "title", "会话1"), Map.of("id", "s-2", "title", "会话2")),
                    2,
                    false,
                    12);

            when(readOnlyExecutor.execute(anyString(), anyInt())).thenReturn(mockResult);

            ToolContext toolContext = new ToolContext(Map.of(
                    ToolEventEmitter.CTX_USER_ID, "user-1", ToolEventEmitter.CTX_EMITTER, emitter, "eventSink", sink));
            String outputJson = databaseQueryTool.query("查询我的会话", sql, 50, toolContext);

            JsonNode root = MAPPER.readTree(outputJson);
            assertThat(root.get("success").asBoolean()).isTrue();
            assertThat(root.get("rowCount").asInt()).isEqualTo(2);
            assertThat(root.get("columns").get(0).asText()).isEqualTo("id");
        }

        @Test
        @DisplayName("输入自然语言问题时调用 SqlGenerator 生成 SQL 并执行")
        void executeNaturalLanguageQuestion() throws Exception {
            String generatedSql = "SELECT count(*) AS total FROM api_keys";
            when(readOnlyExecutor.extractSchemaInfo(any())).thenReturn("Table api_keys: id, provider");
            when(sqlGenerator.generateSql(anyString(), anyString(), any())).thenReturn(generatedSql);

            DbQueryResult mockResult =
                    DbQueryResult.success(generatedSql, List.of("total"), List.of(Map.of("total", 5)), 1, false, 8);
            when(readOnlyExecutor.execute(anyString(), anyInt())).thenReturn(mockResult);

            ToolContext toolContext = new ToolContext(
                    Map.of("userId", "u-123", ToolEventEmitter.CTX_EMITTER, emitter, "eventSink", sink));
            String outputJson = databaseQueryTool.query("统计一共有多少个 API Key", null, 100, toolContext);

            JsonNode root = MAPPER.readTree(outputJson);
            assertThat(root.get("success").asBoolean()).isTrue();
            assertThat(root.get("rowCount").asInt()).isEqualTo(1);
            assertThat(root.get("sql").asText()).isEqualTo(generatedSql);
        }
    }
}
