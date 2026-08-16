package xyz.ppmblszdp.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import xyz.ppmblszdp.ai.dto.InsightSummaryDto;
import xyz.ppmblszdp.ai.repository.InsightRepository;

class ConversationInsightServiceTest {

    private InsightRepository repository;
    private JdbcTemplate jdbcTemplate;
    private ConversationInsightService insightService;

    @BeforeEach
    void setUp() {
        repository = mock(InsightRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        insightService = new ConversationInsightService(repository, jdbcTemplate);
    }

    @Test
    void compute_EmptyMessages_ReturnsDefaultBaseline() {
        when(jdbcTemplate.query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), eq("user-1")))
                .thenReturn(java.util.List.of());

        var summary = insightService.compute("user-1");

        assertThat(summary).isNotNull();
        assertThat(summary.userId()).isEqualTo("user-1");
        assertThat(summary.totalConversations()).isEqualTo(0);
        assertThat(summary.quality().overallScore()).isGreaterThan(0.0);
        verify(repository).saveInsight(eq("user-1"), any(InsightSummaryDto.class));
    }

    @Test
    void getLatest_ExistingCachedInsight_ReturnsCached() {
        var cached = new InsightSummaryDto(
                "user-1", 5, 20, java.util.List.of(), null, java.util.List.of(), java.util.List.of(), 1000L);
        when(repository.findByUserId("user-1")).thenReturn(Optional.of(cached));

        var res = insightService.getLatest("user-1");
        assertThat(res.totalConversations()).isEqualTo(5);
        assertThat(res.totalMessages()).isEqualTo(20);
    }
}
