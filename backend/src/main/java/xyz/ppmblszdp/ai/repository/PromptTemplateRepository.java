package xyz.ppmblszdp.ai.repository;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Prompt 模板持久化仓库（PostgreSQL + JdbcTemplate）。
 *
 * <p>支持用户自定义模板与系统预设模板（{@code user_id = '__system__'}）。
 */
@Repository
public class PromptTemplateRepository {

    private static final Logger log = LoggerFactory.getLogger(PromptTemplateRepository.class);
    public static final String SYSTEM_USER_ID = "__system__";

    private final JdbcTemplate jdbcTemplate;

    public PromptTemplateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record PromptTemplateEntity(
            String id,
            String userId,
            String title,
            String description,
            String category,
            String body,
            int rating,
            boolean favorite,
            boolean isSystem,
            long createdAt,
            long updatedAt) {}

    private final RowMapper<PromptTemplateEntity> rowMapper = (rs, rowNum) -> new PromptTemplateEntity(
            rs.getString("id"),
            rs.getString("user_id"),
            rs.getString("title"),
            rs.getString("description"),
            rs.getString("category"),
            rs.getString("body"),
            rs.getInt("rating"),
            rs.getBoolean("favorite"),
            rs.getBoolean("is_system"),
            rs.getLong("created_at"),
            rs.getLong("updated_at"));

    @PostConstruct
    public void initSchema() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS prompt_templates (
                        id VARCHAR(64) PRIMARY KEY,
                        user_id VARCHAR(128) NOT NULL,
                        title VARCHAR(255) NOT NULL,
                        description TEXT,
                        category VARCHAR(64) NOT NULL DEFAULT 'general',
                        body TEXT NOT NULL,
                        rating INT NOT NULL DEFAULT 5,
                        favorite BOOLEAN NOT NULL DEFAULT false,
                        is_system BOOLEAN NOT NULL DEFAULT false,
                        created_at BIGINT NOT NULL,
                        updated_at BIGINT NOT NULL
                    );
                    """);
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_tpl_user_cat ON prompt_templates(user_id, category);");
            log.info("PostgreSQL Prompt 模板表 'prompt_templates' 初始化/校验成功");

            seedSystemTemplatesIfEmpty();
        } catch (Exception e) {
            log.error("PostgreSQL Prompt 模板表初始化异常", e);
        }
    }

    private void seedSystemTemplatesIfEmpty() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM prompt_templates WHERE user_id = ?", Integer.class, SYSTEM_USER_ID);
        if (count != null && count > 0) {
            return;
        }

        log.info("初始化系统预设高品质 Prompt 模板...");
        long now = System.currentTimeMillis();

        insert(new PromptTemplateEntity(
                "tpl_sys_code_refactor",
                SYSTEM_USER_ID,
                "Clean Code 代码重构大师",
                "对现有代码进行整洁架构与规范重构，提升可读性与性能",
                "coding",
                """
                你是一名资深代码架构师。请帮我重构以下 {{language}} 代码：

                【重构目标】
                {{goal}}

                【待重构代码】
                ```{{language}}
                {{code}}
                ```

                请给出：
                1. 现状问题与异味分析
                2. 重构后的完整代码（附核心设计说明）
                3. 单元测试建议与性能权衡
                """.stripIndent(),
                5,
                true,
                true,
                now,
                now));

        insert(new PromptTemplateEntity(
                "tpl_sys_sql_optimize",
                SYSTEM_USER_ID,
                "SQL 性能深度调优与索引规划",
                "分析慢 SQL 查询瓶颈，给出改写建议与复合索引创建方案",
                "coding",
                """
                请帮我优化以下 {{database_type}} 慢查询 SQL：

                【当前 SQL】
                ```sql
                {{sql}}
                ```

                【表数据量与业务场景】
                {{context}}

                请提供：
                1. 潜在的全表扫描或索引失效瓶颈分析
                2. 优化改写后的 SQL 语句
                3. 推荐的建表索引（CREATE INDEX DDL）
                """.stripIndent(),
                5,
                false,
                true,
                now,
                now));

        insert(new PromptTemplateEntity(
                "tpl_sys_translate_pro",
                SYSTEM_USER_ID,
                "专业学术与技术翻译家",
                "保留专有名词与技术语境的高精度地道翻译",
                "translation",
                """
                请将以下专业内容从 {{source_lang}} 精准翻译为 {{target_lang}}。

                【翻译要求】
                - 保持专业术语一致性，语气为 {{tone}}
                - 译文流畅自然、符合母语表达习惯

                【原文】
                {{text}}
                """.stripIndent(),
                5,
                false,
                true,
                now,
                now));

        insert(new PromptTemplateEntity(
                "tpl_sys_paper_polishing",
                SYSTEM_USER_ID,
                "学术论文润色与精炼",
                "提升英文/中文学术表达逻辑性与学术词汇严谨度",
                "writing",
                """
                你是一名知名顶会审稿人。请对以下论文段落进行深度润色：

                【目标期刊/领域】
                {{field}}

                【待润色段落】
                {{content}}

                请输出：
                1. 润色后的版本（高学术水准）
                2. 主要修改点对比及改进原因说明
                """.stripIndent(),
                5,
                true,
                true,
                now,
                now));

        insert(new PromptTemplateEntity(
                "tpl_sys_unit_test",
                SYSTEM_USER_ID,
                "全覆盖单元测试生成器",
                "基于边界条件与分支覆盖生成全面的单元测试用例",
                "coding",
                """
                请为以下 {{framework}} 方法编写完整的单元测试用例（包含正常路径、边界值与异常分支）：

                ```{{language}}
                {{code}}
                ```

                【要求】
                - 使用 {{test_framework}} 框架（如 JUnit 5 / Bun test / Jest）
                - 包含 Mock 对象构造与清晰的断言
                """.stripIndent(),
                5,
                false,
                true,
                now,
                now));
    }

    public void insert(PromptTemplateEntity entity) {
        String id = entity.id() != null
                ? entity.id()
                : "tpl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        jdbcTemplate.update(
                """
                INSERT INTO prompt_templates (id, user_id, title, description, category, body, rating, favorite, is_system, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                entity.userId(),
                entity.title(),
                entity.description(),
                entity.category(),
                entity.body(),
                entity.rating(),
                entity.favorite(),
                entity.isSystem(),
                entity.createdAt(),
                entity.updatedAt());
    }

    public void update(PromptTemplateEntity entity) {
        jdbcTemplate.update(
                """
                UPDATE prompt_templates
                SET title = ?, description = ?, category = ?, body = ?, rating = ?, favorite = ?, updated_at = ?
                WHERE id = ? AND (user_id = ? OR is_system = false)
                """,
                entity.title(),
                entity.description(),
                entity.category(),
                entity.body(),
                entity.rating(),
                entity.favorite(),
                entity.updatedAt(),
                entity.id(),
                entity.userId());
    }

    public void delete(String id, String userId) {
        jdbcTemplate.update(
                "DELETE FROM prompt_templates WHERE id = ? AND user_id = ? AND is_system = false", id, userId);
    }

    public Optional<PromptTemplateEntity> findById(String id, String userId) {
        try {
            PromptTemplateEntity entity = jdbcTemplate.queryForObject(
                    "SELECT * FROM prompt_templates WHERE id = ? AND (user_id = ? OR user_id = ?)",
                    rowMapper,
                    id,
                    userId,
                    SYSTEM_USER_ID);
            return Optional.ofNullable(entity);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<PromptTemplateEntity> findAllByUser(String userId, String category, String keyword) {
        StringBuilder sql = new StringBuilder("SELECT * FROM prompt_templates WHERE (user_id = ? OR user_id = ?)");
        List<Object> params = new ArrayList<>();
        params.add(userId);
        params.add(SYSTEM_USER_ID);

        if (category != null && !category.isBlank() && !"all".equalsIgnoreCase(category)) {
            sql.append(" AND category = ?");
            params.add(category.trim().toLowerCase());
        }

        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (title ILIKE ? OR description ILIKE ? OR body ILIKE ?)");
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }

        sql.append(" ORDER BY favorite DESC, is_system DESC, updated_at DESC");
        return jdbcTemplate.query(sql.toString(), rowMapper, params.toArray());
    }

    public void toggleFavorite(String id, String userId) {
        jdbcTemplate.update(
                "UPDATE prompt_templates SET favorite = NOT favorite, updated_at = ? WHERE id = ? AND (user_id = ? OR user_id = ?)",
                System.currentTimeMillis(),
                id,
                userId,
                SYSTEM_USER_ID);
    }

    public void updateRating(String id, String userId, int rating) {
        int boundedRating = Math.max(1, Math.min(5, rating));
        jdbcTemplate.update(
                "UPDATE prompt_templates SET rating = ?, updated_at = ? WHERE id = ? AND (user_id = ? OR user_id = ?)",
                boundedRating,
                System.currentTimeMillis(),
                id,
                userId,
                SYSTEM_USER_ID);
    }
}
