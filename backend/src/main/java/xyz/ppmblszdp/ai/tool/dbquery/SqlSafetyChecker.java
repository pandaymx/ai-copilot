package xyz.ppmblszdp.ai.tool.dbquery;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * SQL 安全校验器（SqlSafetyChecker）：对 LLM 生成或用户输入的 SQL 进行严格前置安全校验。
 *
 * <p>防护策略：
 * <ul>
 *   <li>仅允许 SELECT / WITH ... SELECT / EXPLAIN 查询；</li>
 *   <li>严厉禁止 INSERT / UPDATE / DELETE / DROP / ALTER / TRUNCATE / GRANT / REVOKE / SET 等写或 DDL 语句；</li>
 *   <li>禁止多语句分号拼接（防止堆叠注入）；</li>
 *   <li>禁止访问 PostgreSQL 敏感核心系统表（如 pg_authid / pg_shadow 等）；</li>
 *   <li>支持白名单表过滤。</li>
 * </ul>
 */
@Component
public class SqlSafetyChecker {

    private static final Pattern FORBIDDEN_COMMANDS = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE|GRANT|REVOKE|EXEC|EXECUTE|CALL|VACUUM|MERGE|UPSERT|LOCK|COMMENT|RENAME|DO|LISTEN|NOTIFY)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SENSITIVE_SYSTEM_TABLES = Pattern.compile(
            "\\b(pg_shadow|pg_authid|pg_user|pg_roles|pg_stat_activity|pg_config|pg_file_settings|pg_hba_file_rules)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern VALID_START =
            Pattern.compile("^\\s*(SELECT|WITH|EXPLAIN)\\b", Pattern.CASE_INSENSITIVE);

    /**
     * 校验 SQL 是否符合只读与安全规范。
     *
     * @param sql           待校验 SQL 语句
     * @param allowedTables 允许访问的表名白名单（为空表示不限白名单，但仍受只读与系统表限制）
     * @throws SecurityException 若检测到违规风险
     */
    public void assertSafe(String sql, List<String> allowedTables) {
        if (sql == null || sql.isBlank()) {
            throw new SecurityException("SQL 语句不能为空");
        }

        String trimmed = sql.trim();

        // 1. 必须以 SELECT / WITH / EXPLAIN 开头
        if (!VALID_START.matcher(trimmed).find()) {
            throw new SecurityException("只允许执行 SELECT / WITH / EXPLAIN 查询语句");
        }

        // 2. 检查多语句拼接（去除末尾分号后，中间不允许出现分号）
        String strippedEndingSemicolon = trimmed.replaceAll(";+\\s*$", "");
        if (strippedEndingSemicolon.contains(";")) {
            throw new SecurityException("禁止执行包含多个语句的复合 SQL");
        }

        // 3. 禁止写与 DDL 命令
        if (FORBIDDEN_COMMANDS.matcher(strippedEndingSemicolon).find()) {
            throw new SecurityException("SQL 包含被禁止的非只读/修改关键字 (如 INSERT/UPDATE/DELETE/DROP/ALTER 等)");
        }

        // 4. 禁止查询敏感核心系统表
        if (SENSITIVE_SYSTEM_TABLES.matcher(strippedEndingSemicolon).find()) {
            throw new SecurityException("禁止访问数据库核心安全/权限系统表");
        }

        // 5. 白名单表校验（若配置了白名单）
        if (allowedTables != null && !allowedTables.isEmpty()) {
            Set<String> allowedSet = allowedTables.stream()
                    .map(s -> s.trim().toLowerCase(Locale.ROOT))
                    .collect(java.util.stream.Collectors.toSet());

            // 简单解析 SQL 中的 FROM / JOIN 目标表
            Pattern tablePattern = Pattern.compile("\\b(?:FROM|JOIN)\\s+([a-zA-Z0-9_\\.]+)", Pattern.CASE_INSENSITIVE);
            var matcher = tablePattern.matcher(strippedEndingSemicolon);
            while (matcher.find()) {
                String rawTable = matcher.group(1).toLowerCase(Locale.ROOT);
                String tableName =
                        rawTable.contains(".") ? rawTable.substring(rawTable.lastIndexOf('.') + 1) : rawTable;
                if (!allowedSet.contains(tableName) && !allowedSet.contains(rawTable)) {
                    throw new SecurityException("表 [" + rawTable + "] 不在允许查询的白名单配置中: " + allowedTables);
                }
            }
        }
    }
}
