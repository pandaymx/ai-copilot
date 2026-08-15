package xyz.ppmblszdp.ai.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import xyz.ppmblszdp.ai.config.AiProviderProperties;

class LongTermMemoryProcessorTest {

    private VectorStore mockVectorStore;
    private AiProviderProperties properties;
    private LongTermMemoryProcessor processor;

    @BeforeEach
    void setUp() {
        mockVectorStore = mock(VectorStore.class);
        properties = new AiProviderProperties(
                null,
                null,
                null,
                null,
                null,
                null,
                new AiProviderProperties.MemoryConfig(true, 20, 5, true, 0.85d, 15, true, 5, 14, null, null),
                null,
                null,
                null,
                null,
                null);
        processor = new LongTermMemoryProcessor(mockVectorStore, null, properties);
    }

    @Test
    void isTrivialOrNoise_shouldFilterShortAndGreetingMessages() {
        assertThat(processor.isTrivialOrNoise("你好")).isTrue();
        assertThat(processor.isTrivialOrNoise("OK")).isTrue();
        assertThat(processor.isTrivialOrNoise("收到！")).isTrue();
        assertThat(processor.isTrivialOrNoise("12345678901234")).isTrue(); // len < 15

        assertThat(processor.isTrivialOrNoise("我习惯在 Spring Boot 3 中使用 Java 21 进行开发。"))
                .isFalse();
    }

    @Test
    void dedupAndUpsert_shouldInsertNewDoc_whenNoSimilarDocExists() {
        when(mockVectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        processor.dedupAndUpsert("user-123", "用户偏好：使用 PostgreSQL 数据库", null, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(mockVectorStore).add(captor.capture());

        List<Document> added = captor.getValue();
        assertThat(added).hasSize(1);
        assertThat(added.get(0).getText()).isEqualTo("用户偏好：使用 PostgreSQL 数据库");
        assertThat(added.get(0).getMetadata()).containsKey("updated_at");
        assertThat(added.get(0).getMetadata().get("userId")).isEqualTo("user-123");
        assertThat(added.get(0).getMetadata().get("sourceType")).isEqualTo("long_term_memory");
    }

    @Test
    void dedupAndUpsert_shouldPersistStructuredMetadata_whenCategoryAndConfidenceProvided() {
        when(mockVectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        processor.dedupAndUpsert("user-123", "用户技术栈偏好：Java 25。", "技术栈偏好", 0.92d);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(mockVectorStore).add(captor.capture());

        Map<String, Object> meta = captor.getValue().get(0).getMetadata();
        assertThat(meta.get("category")).isEqualTo("技术栈偏好");
        assertThat(meta.get("confidence")).isEqualTo(0.92d);
    }

    @Test
    void dedupAndUpsert_shouldDeleteOldDocAndUpsert_whenSimilarDocExists() {
        Document existingDoc = new Document("old-id", "用户偏好：使用 PostgreSQL 数据库", java.util.Map.of("userId", "user-123"));
        when(mockVectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(existingDoc));

        processor.dedupAndUpsert("user-123", "用户偏好：使用 PostgreSQL 数据库", null, null);

        verify(mockVectorStore).delete(List.of("old-id"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(mockVectorStore).add(captor.capture());

        List<Document> added = captor.getValue();
        assertThat(added).hasSize(1);
        assertThat(added.get(0).getText()).isEqualTo("用户偏好：使用 PostgreSQL 数据库");
        assertThat(added.get(0).getMetadata()).containsKey("updated_at");
    }

    @Test
    void dedupAndUpsert_withConflictService_retainOld_shouldIgnoreNew() {
        xyz.ppmblszdp.ai.service.MemoryForgetService forgetService =
                mock(xyz.ppmblszdp.ai.service.MemoryForgetService.class);
        processor = new LongTermMemoryProcessor(mockVectorStore, null, forgetService, properties);

        Document existingDoc =
                new Document("old-id", "用户偏好：主要使用 Java 21 进行后端开发", java.util.Map.of("userId", "user-123"));
        when(mockVectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(existingDoc));
        when(forgetService.evaluateConflict("用户偏好：主要使用 Java 21 进行后端开发", "用户偏好：主要使用 Java 21 进行后端开发"))
                .thenReturn(
                        new xyz.ppmblszdp.ai.service.MemoryForgetService.ConflictDecision("RETAIN_OLD", null, "保留旧记录"));

        processor.dedupAndUpsert("user-123", "用户偏好：主要使用 Java 21 进行后端开发", "技术偏好", 0.9);

        // 不应删除旧记录，也不应新增
        verify(mockVectorStore, never()).delete(anyList());
        verify(mockVectorStore, never()).add(any());
    }

    @Test
    void dedupAndUpsert_withConflictService_merge_shouldInsertMerged() {
        xyz.ppmblszdp.ai.service.MemoryForgetService forgetService =
                mock(xyz.ppmblszdp.ai.service.MemoryForgetService.class);
        processor = new LongTermMemoryProcessor(mockVectorStore, null, forgetService, properties);

        Document existingDoc =
                new Document("old-id", "用户偏好：主要使用 Java 21 进行后端开发", java.util.Map.of("userId", "user-123"));
        when(mockVectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(existingDoc));
        when(forgetService.evaluateConflict("用户新偏好：全面转为 Java 25 全栈开发", "用户偏好：主要使用 Java 21 进行后端开发"))
                .thenReturn(new xyz.ppmblszdp.ai.service.MemoryForgetService.ConflictDecision(
                        "MERGE", "用户偏好升级为 Java 25 全栈开发体系", "合并偏好"));

        processor.dedupAndUpsert("user-123", "用户新偏好：全面转为 Java 25 全栈开发", "技术偏好", 0.95);

        // 应删除旧记录，并添加合并后的新记录
        verify(mockVectorStore).delete(List.of("old-id"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(mockVectorStore).add(captor.capture());
        assertThat(captor.getValue().get(0).getText()).isEqualTo("用户偏好升级为 Java 25 全栈开发体系");
    }
}
