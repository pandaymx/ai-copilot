package xyz.ppmblszdp.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.ppmblszdp.ai.rag.dto.RagExtractRequest;
import xyz.ppmblszdp.ai.rag.dto.StructuredKnowledge;
import xyz.ppmblszdp.ai.rag.service.RagExtractionService;
import xyz.ppmblszdp.ai.rag.service.RagQueryService;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;

class RagExtractionServiceTest {

    private RagQueryService mockQueryService;
    private RagExtractionService extractionService;

    @BeforeEach
    void setUp() {
        mockQueryService = mock(RagQueryService.class);
        extractionService = new RagExtractionService(mockQueryService, (ProviderRegistry) null);
    }

    @Test
    void extract_shouldReturnFallback_whenProviderRegistryNull() {
        RagExtractRequest request =
                new RagExtractRequest("核心架构", "这是待抽取的文本，包含实体 AI Copilot 和技术栈 Spring Boot。", "user-01", "TEXT", 4);

        StructuredKnowledge knowledge = extractionService.extract(request);

        assertThat(knowledge).isNotNull();
        assertThat(knowledge.title()).isEqualTo("未就绪");
    }

    @Test
    void extract_shouldReturnEmptyKnowledge_whenNoRagDocsFound() {
        when(mockQueryService.search(anyString(), anyString(), any(), anyInt())).thenReturn(List.of());

        RagExtractRequest request = new RagExtractRequest("检索不存在的文档", null, "user-01", "TEXT", 4);

        StructuredKnowledge knowledge = extractionService.extract(request);

        assertThat(knowledge).isNotNull();
        assertThat(knowledge.summary()).contains("未找到相关文档");
    }
}
