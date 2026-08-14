package xyz.ppmblszdp.ai.rag.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.rag.embedding.controller.EmbeddingManagementController;
import xyz.ppmblszdp.ai.rag.embedding.dto.DocumentSimilarityClusterDto;
import xyz.ppmblszdp.ai.rag.embedding.dto.EmbeddingHealthDto;
import xyz.ppmblszdp.ai.rag.embedding.dto.EmbeddingReindexTaskDto;
import xyz.ppmblszdp.ai.rag.embedding.dto.StaleVectorDto;
import xyz.ppmblszdp.ai.rag.embedding.service.EmbeddingManagementService;

class EmbeddingManagementControllerTest {

    private EmbeddingManagementService mockService;
    private EmbeddingManagementController controller;

    @BeforeEach
    void setUp() {
        mockService = mock(EmbeddingManagementService.class);
        AuthProperties authProperties = new AuthProperties("dev", "X-User-Id", Set.of("admin"));
        controller = new EmbeddingManagementController(mockService, authProperties);
    }

    @Test
    void getHealth_shouldReturnHealthDto() {
        EmbeddingHealthDto mockHealth = new EmbeddingHealthDto(
                100, 95, 2, 0, 3, 5, "text-embedding-3-small", 1536, 92, "HEALTHY", Map.of("1536", 98L), List.of());
        when(mockService.detectHealth(anyString())).thenReturn(mockHealth);

        ResponseEntity<EmbeddingHealthDto> res = controller.getHealth("user-1", null);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().healthScore()).isEqualTo(92);
    }

    @Test
    void reembedding_controls_shouldWork() {
        EmbeddingReindexTaskDto mockTask = new EmbeddingReindexTaskDto(
                "task-123",
                100,
                50,
                50,
                0,
                "doc-50",
                "text-embedding-3-small",
                1536,
                true,
                false,
                1000L,
                null,
                List.of());
        when(mockService.startReembedding(anyString(), anyBoolean())).thenReturn(mockTask);
        when(mockService.getReindexTaskStatus()).thenReturn(mockTask);

        ResponseEntity<EmbeddingReindexTaskDto> startRes = controller.startReembedding(false, "user-1", null);
        assertThat(startRes.getBody()).isNotNull();
        assertThat(startRes.getBody().taskId()).isEqualTo("task-123");

        ResponseEntity<EmbeddingReindexTaskDto> statusRes = controller.getReembedStatus();
        assertThat(statusRes.getBody()).isNotNull();
        assertThat(statusRes.getBody().processed()).isEqualTo(50);
    }

    @Test
    void similarityClusters_andStaleVectors_shouldReturnData() {
        DocumentSimilarityClusterDto cluster = new DocumentSimilarityClusterDto(
                "c-1", 0.95, "d1", "f1.md", "txt1", "d2", "f2.md", "txt2", "CROSS_DOC_DUPLICATE", "DELETE_DOC_B");
        when(mockService.findSimilarityClusters(anyString(), anyDouble(), anyInt()))
                .thenReturn(List.of(cluster));

        ResponseEntity<List<DocumentSimilarityClusterDto>> clustersRes =
                controller.getSimilarityClusters(0.88, 50, "user-1", null);
        assertThat(clustersRes.getBody()).hasSize(1);

        StaleVectorDto stale = new StaleVectorDto("stale-1", "f.md", "TEXT", "content", 100L, 0L, null, false);
        when(mockService.findStaleVectors(anyString(), anyInt(), anyInt())).thenReturn(List.of(stale));

        ResponseEntity<List<StaleVectorDto>> staleRes = controller.getStaleVectors(30, 100, "user-1", null);
        assertThat(staleRes.getBody()).hasSize(1);

        when(mockService.archiveStaleVectors(anyList(), anyString())).thenReturn(true);
        ResponseEntity<Map<String, Object>> archiveRes = controller.archiveStaleVectors(
                new EmbeddingManagementController.BatchDocIdsRequest(List.of("stale-1"), "user-1"), null);
        assertThat(archiveRes.getBody()).containsEntry("success", true);
    }
}
