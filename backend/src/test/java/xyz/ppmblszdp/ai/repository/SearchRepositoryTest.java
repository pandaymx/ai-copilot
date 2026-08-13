package xyz.ppmblszdp.ai.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import xyz.ppmblszdp.ai.dto.SearchResponse.SearchResultItem;

/**
 * SearchRepository 单元测试（mock JdbcTemplate）。
 *
 * <p>注：{@code to_tsvector}/{@code ts_headline}/{@code pg_trgm} 均为 PostgreSQL 专有，
 * 运行测试使用 H2（PostgreSQL 兼容模式），无法实际执行检索 SQL，故此处仅用 mock 校验
 * SQL 文本被正确拼装、参数顺序与数量正确，以及 limit 的 clamp 行为。
 */
class SearchRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private SearchRepository searchRepository;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        searchRepository = new SearchRepository(jdbcTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchByUserBuildsJoinAndBoundsLimit() {
        SearchResultItem item = new SearchResultItem("conv-1", 1L, "USER", "snippet", 123L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(item));

        List<SearchResultItem> result = searchRepository.searchByUser("user-1", "hello", 9999);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate)
                .query(sqlCaptor.capture(), any(RowMapper.class), any(), any(), any(), any(), any(), anyInt());

        String sql = sqlCaptor.getValue();
        // 用户隔离：JOIN chat_session 并按 user_id 过滤
        assertTrue(
                sql.contains("JOIN chat_session s ON s.id = m.conversation_id AND s.user_id = ?"),
                "SQL 应 JOIN chat_session 做用户隔离");
        // tsvector + pg_trgm 组合检索
        assertTrue(sql.contains("m.content_tsv @@ plainto_tsquery("), "应使用 tsvector 匹配");
        assertTrue(sql.contains("m.content ILIKE"), "应使用 pg_trgm 子串兜底");
        // ts_headline 高亮
        assertTrue(sql.contains("ts_headline("), "应生成 ts_headline 高亮片段");
        // 表名小写（运行实例实际表名）
        assertTrue(sql.contains("FROM spring_ai_chat_memory m"), "应引用小写表名 spring_ai_chat_memory");
        // limit 被 clamp 到上限（9999 -> 200）
        assertEquals(1, result.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchByUserUsesDefaultLimitWhenZero() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        searchRepository.searchByUser("user-1", "hi", 0);

        ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(jdbcTemplate)
                .query(anyString(), any(RowMapper.class), any(), any(), any(), any(), any(), limitCaptor.capture());
        // 默认值 50
        assertEquals(50, limitCaptor.getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchByUserPassesQueryAndUserIdParams() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        searchRepository.searchByUser("user-42", "世界", 10);

        // 参数顺序：q, userId, q, q, q, limit
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate)
                .query(
                        anyString(),
                        any(RowMapper.class),
                        captor.capture(),
                        captor.capture(),
                        captor.capture(),
                        captor.capture(),
                        captor.capture(),
                        captor.capture());
        List<Object> params = captor.getAllValues();
        assertEquals("世界", params.get(0));
        assertEquals("user-42", params.get(1));
        assertEquals("世界", params.get(2));
        assertEquals("世界", params.get(3));
        assertEquals("世界", params.get(4));
        assertEquals(10, params.get(5));
    }
}
