package xyz.ppmblszdp.ai.rag.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import xyz.ppmblszdp.ai.memory.SafeEmbeddingModel;
import xyz.ppmblszdp.ai.rag.RagProperties;
import xyz.ppmblszdp.ai.rag.embedding.dto.DocumentSimilarityClusterDto;
import xyz.ppmblszdp.ai.rag.embedding.dto.EmbeddingHealthDto;
import xyz.ppmblszdp.ai.rag.embedding.dto.EmbeddingReindexTaskDto;
import xyz.ppmblszdp.ai.rag.embedding.dto.StaleVectorDto;
import xyz.ppmblszdp.ai.rag.embedding.service.EmbeddingManagementService;

class EmbeddingManagementServiceTest {

    private EmbeddingManagementService service;
    private RagProperties ragProperties;

    @BeforeEach
    void setUp() {
        ragProperties = new RagProperties(
                true,
                4,
                900,
                180,
                "CL100K_BASE",
                "ai_rag_documents",
                true,
                false,
                true,
                true,
                60,
                3,
                new RagProperties.SsrfConfig(5, 10_485_760L));

        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> mockJdbc = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<SafeEmbeddingModel> mockSafeEmbedding = mock(ObjectProvider.class);

        service = new EmbeddingManagementService(mockJdbc, ragProperties, null, mockSafeEmbedding, null);
    }

    @Test
    void detectHealth_shouldCalculateHealthAndIdentifyIssues() {
        // 注册一份正常文档、一份空向量文档、一份模型失配文档
        service.registerMemoryDoc(new EmbeddingManagementService.MockDocumentRecord(
                "doc-1",
                "正常内容文档",
                Map.of("fileName", "guide.md", "embeddingModel", "text-embedding-3-small"),
                new float[] {0.1f, 0.2f, 0.3f},
                System.currentTimeMillis(),
                5,
                System.currentTimeMillis(),
                false));

        service.registerMemoryDoc(new EmbeddingManagementService.MockDocumentRecord(
                "doc-2",
                "空向量文档",
                Map.of("fileName", "empty.md", "embeddingModel", "text-embedding-3-small"),
                new float[] {0.0f, 0.0f, 0.0f},
                System.currentTimeMillis(),
                0,
                null,
                false));

        service.registerMemoryDoc(new EmbeddingManagementService.MockDocumentRecord(
                "doc-3",
                "旧模型文档",
                Map.of("fileName", "legacy.md", "embeddingModel", "text-embedding-ada-002"),
                new float[] {0.5f, 0.6f, 0.7f},
                System.currentTimeMillis(),
                1,
                System.currentTimeMillis(),
                false));

        EmbeddingHealthDto health = service.detectHealth("test-user");

        assertThat(health.totalVectors()).isEqualTo(3);
        assertThat(health.emptyOrZeroVectors()).isEqualTo(1);
        assertThat(health.modelMismatchCount()).isEqualTo(1);
        assertThat(health.healthScore()).isLessThan(100);
    }

    @Test
    void findSimilarityClusters_shouldGroupHighSimilarityDocuments() {
        // 两个高度相似的文档向量
        service.registerMemoryDoc(new EmbeddingManagementService.MockDocumentRecord(
                "doc-a",
                "Spring Boot 自动装配原理与条件注解",
                Map.of("fileName", "spring_v1.md"),
                new float[] {1.0f, 0.0f, 0.0f},
                System.currentTimeMillis(),
                2,
                System.currentTimeMillis(),
                false));

        service.registerMemoryDoc(new EmbeddingManagementService.MockDocumentRecord(
                "doc-b",
                "Spring Boot 自动装配原理与条件注解详解",
                Map.of("fileName", "spring_v2.md"),
                new float[] {0.99f, 0.01f, 0.0f},
                System.currentTimeMillis(),
                0,
                null,
                false));

        List<DocumentSimilarityClusterDto> clusters = service.findSimilarityClusters("test-user", 0.88, 10);

        assertThat(clusters).hasSize(1);
        assertThat(clusters.get(0).similarityScore()).isGreaterThan(0.95);
        assertThat(clusters.get(0).conflictType()).isEqualTo("CROSS_DOC_DUPLICATE");
        assertThat(clusters.get(0).suggestedAction()).isEqualTo("DELETE_DOC_B");
    }

    @Test
    void findStaleVectors_andArchive_shouldWorkCorrectly() {
        long oldTs = Instant.now().minusSeconds(40L * 24 * 3600).toEpochMilli();

        service.registerMemoryDoc(new EmbeddingManagementService.MockDocumentRecord(
                "doc-stale",
                "40天前入库但零检索命中冷数据",
                Map.of("fileName", "archive.txt", "userId", "test-user"),
                new float[] {0.1f, 0.1f, 0.1f},
                oldTs,
                0,
                null,
                false));

        List<StaleVectorDto> stales = service.findStaleVectors("test-user", 30, 10);
        assertThat(stales).hasSize(1);
        assertThat(stales.get(0).id()).isEqualTo("doc-stale");

        boolean archiveRes = service.archiveStaleVectors(List.of("doc-stale"), "test-user");
        assertThat(archiveRes).isTrue();

        List<StaleVectorDto> archivedStales = service.findStaleVectors("test-user", 30, 10);
        assertThat(archivedStales.get(0).isArchived()).isTrue();

        boolean purgeRes = service.purgeStaleVectors(List.of("doc-stale"), "test-user");
        assertThat(purgeRes).isTrue();
        assertThat(service.findStaleVectors("test-user", 30, 10)).isEmpty();
    }

    @Test
    void reembedding_taskFlow_shouldHandleStatusAndPause() {
        EmbeddingReindexTaskDto task = service.startReembedding("user-1", false);
        assertThat(task).isNotNull();

        service.pauseReembedding();
        EmbeddingReindexTaskDto paused = service.getReindexTaskStatus();
        assertThat(paused.isPaused()).isTrue();

        service.resumeReembedding();
        EmbeddingReindexTaskDto resumed = service.getReindexTaskStatus();
        assertThat(resumed.isPaused()).isFalse();
    }
}
