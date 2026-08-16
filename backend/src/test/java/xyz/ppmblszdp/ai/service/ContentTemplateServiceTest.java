package xyz.ppmblszdp.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import xyz.ppmblszdp.ai.dto.ContentTemplateDto;
import xyz.ppmblszdp.ai.repository.ContentGenerationRepository;

class ContentTemplateServiceTest {

    private ContentGenerationRepository repository;
    private ContentTemplateService service;

    @BeforeEach
    void setUp() {
        repository = mock(ContentGenerationRepository.class);
        ObjectProvider mockProvider = mock(ObjectProvider.class);
        service = new ContentTemplateService(mockProvider, repository);
    }

    @Test
    void listTemplates_ReturnsBuiltInTemplates() {
        var templates = service.listTemplates();
        assertThat(templates).isNotEmpty();
        assertThat(templates).anyMatch(t -> t.id().equals("weekly-report"));
        assertThat(templates).anyMatch(t -> t.id().equals("tech-doc"));
    }

    @Test
    void generateContent_OfflineFallback_CreatesFormattedMarkdownAndSaves() {
        var req = new ContentTemplateDto.GenerateContentRequest(
                "weekly-report",
                "2026年第33周工作周报",
                Map.of(
                        "project", "AI Copilot",
                        "completed", "完成红队对抗演练与内容生成模板",
                        "inprogress", "优化知识库检索",
                        "nextWeek", "系统全链路压测"),
                "请突出研发亮点");

        var res = service.generateContent("user-1", req);

        assertThat(res.id()).startsWith("cgen_");
        assertThat(res.templateId()).isEqualTo("weekly-report");
        assertThat(res.title()).isEqualTo("2026年第33周工作周报");
        assertThat(res.markdownContent()).contains("2026年第33周工作周报");
        assertThat(res.markdownContent()).contains("完成红队对抗演练与内容生成模板");

        verify(repository)
                .save(any(), eq("user-1"), eq("weekly-report"), eq("2026年第33周工作周报"), any(), any(), any(Long.class));
    }
}
