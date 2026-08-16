package xyz.ppmblszdp.ai.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class BookmarkRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private BookmarkRepository repository;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        repository = new BookmarkRepository(jdbcTemplate);
    }

    @Test
    void toggleBookmark_NewMessage_InsertsAndReturnsBookmarked() {
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq("user-1"), eq("msg-1")))
                .thenReturn(List.of());

        var res = repository.toggleBookmark("user-1", "sess-1", "msg-1", "assistant", "你好", List.of("重要"));

        assertThat(res.bookmarked()).isTrue();
        assertThat(res.pinned()).isFalse();
        verify(jdbcTemplate)
                .update(
                        any(String.class),
                        any(),
                        eq("user-1"),
                        eq("sess-1"),
                        eq("msg-1"),
                        eq("assistant"),
                        eq("你好"),
                        any(),
                        any());
    }

    @Test
    void togglePin_NewMessage_InsertsAndReturnsPinned() {
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq("user-1"), eq("msg-2")))
                .thenReturn(List.of());

        var res = repository.togglePin("user-1", "sess-1", "msg-2", "user", "请帮我总结");

        assertThat(res.pinned()).isTrue();
        assertThat(res.bookmarked()).isFalse();
    }
}
