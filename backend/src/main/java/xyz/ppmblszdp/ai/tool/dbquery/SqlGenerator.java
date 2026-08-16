package xyz.ppmblszdp.ai.tool.dbquery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;

/**
 * 自然语言转 SQL 生成器（SqlGenerator）：根据数据库表结构 Schema 及用户自然语言意图生成合法的 PostgreSQL 只读查询语句。
 */
@Component
public class SqlGenerator {

    private static final Logger log = LoggerFactory.getLogger(SqlGenerator.class);

    private final ProviderRegistry providerRegistry;

    public SqlGenerator(ProviderRegistry providerRegistry) {
        this.providerRegistry = providerRegistry;
    }

    /**
     * 将自然语言问题转化为 PostgreSQL SELECT 语句。
     *
     * @param naturalLanguageQuestion 自然语言查询请求
     * @param schemaInfo              当前可见数据库表结构 Schema 描述
     * @param userId                  当前登录用户 ID（用于多租户过滤）
     * @return 生成的纯 SQL 字符串
     */
    public String generateSql(String naturalLanguageQuestion, String schemaInfo, String userId) {
        // 优先解析 T3-T4 或通用对话模型
        ResolvedModel resolved = resolveModel();
        ChatClient chatClient = resolved.chatClient();

        String userFilterHint = (userId != null && !userId.isBlank())
                ? "若查询包含 user_id 列的业务表，请在 WHERE 条件中加入 user_id = '" + userId + "' 以进行数据隔离。"
                : "";

        String systemPrompt = """
                你是一名资深的 PostgreSQL 数据库专家。你的任务是根据提供的数据库表结构，将用户的自然语言查询转换为准确、高效的只读 PostgreSQL SQL 语句。

                【表结构信息】
                %s

                【强制规则】
                1. 只能生成单条 SELECT / WITH ... SELECT 语句，严禁生成 INSERT / UPDATE / DELETE / DROP / ALTER / TRUNCATE 等修改或 DDL 语句。
                2. 严禁生成分号分隔的多条复合语句。
                3. %s
                4. 请务必在末尾添加合理的 LIMIT 限制（默认不超过 200）。
                5. 仅返回纯 SQL 文本，严禁包含任何 Markdown 标记（如 ```sql）、严禁包含任何解释或额外文字！
                """.formatted(schemaInfo, userFilterHint);

        try {
            String response = chatClient
                    .prompt()
                    .system(systemPrompt)
                    .user(naturalLanguageQuestion)
                    .call()
                    .content();

            return cleanSql(response);
        } catch (Exception e) {
            log.warn("NL→SQL 转换异常: {}", e.getMessage());
            throw new IllegalStateException("自然语言转 SQL 失败: " + e.getMessage(), e);
        }
    }

    private ResolvedModel resolveModel() {
        try {
            return providerRegistry.resolve("deepseek", "deepseek-chat");
        } catch (Exception e1) {
            try {
                return providerRegistry.resolve("openai", "gpt-4o");
            } catch (Exception e2) {
                return providerRegistry.resolve(null, null);
            }
        }
    }

    /**
     * 清洗 LLM 输出，去除可能携带的 markdown 代码块标记与前后空白。
     */
    public static String cleanSql(String raw) {
        if (raw == null) {
            return "";
        }
        String sql = raw.trim();
        if (sql.startsWith("```")) {
            int firstNewline = sql.indexOf('\n');
            if (firstNewline != -1) {
                sql = sql.substring(firstNewline + 1);
            } else {
                sql = sql.substring(3);
            }
        }
        if (sql.endsWith("```")) {
            sql = sql.substring(0, sql.length() - 3);
        }
        return sql.trim();
    }
}
