package xyz.ppmblszdp.ai.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import xyz.ppmblszdp.ai.config.AiProviderProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LongTermMemoryProcessorTest {

	private VectorStore mockVectorStore;
	private AiProviderProperties properties;
	private LongTermMemoryProcessor processor;

	@BeforeEach
	void setUp() {
		mockVectorStore = mock(VectorStore.class);
		properties = new AiProviderProperties(
				null, null, null, null,
				new AiProviderProperties.MemoryConfig(true, 20, 5, true, 0.85d, 15, true, 5, 14, null),
				null, null
		);
		processor = new LongTermMemoryProcessor(mockVectorStore, null, properties);
	}

	@Test
	void isTrivialOrNoise_shouldFilterShortAndGreetingMessages() {
		assertThat(processor.isTrivialOrNoise("你好")).isTrue();
		assertThat(processor.isTrivialOrNoise("OK")).isTrue();
		assertThat(processor.isTrivialOrNoise("收到！")).isTrue();
		assertThat(processor.isTrivialOrNoise("12345678901234")).isTrue(); // len < 15

		assertThat(processor.isTrivialOrNoise("我习惯在 Spring Boot 3 中使用 Java 21 进行开发。")).isFalse();
	}

	@Test
	void dedupAndUpsert_shouldInsertNewDoc_whenNoSimilarDocExists() {
		when(mockVectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

		processor.dedupAndUpsert("user-123", "用户偏好：使用 PostgreSQL 数据库");

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
		verify(mockVectorStore).add(captor.capture());

		List<Document> added = captor.getValue();
		assertThat(added).hasSize(1);
		assertThat(added.get(0).getText()).isEqualTo("用户偏好：使用 PostgreSQL 数据库");
		assertThat(added.get(0).getMetadata()).containsKey("updated_at");
		assertThat(added.get(0).getMetadata().get("userId")).isEqualTo("user-123");
	}

	@Test
	void dedupAndUpsert_shouldDeleteOldDocAndUpsert_whenSimilarDocExists() {
		Document existingDoc = new Document("old-id", "用户偏好：使用 PostgreSQL 数据库", java.util.Map.of("userId", "user-123"));
		when(mockVectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(existingDoc));

		processor.dedupAndUpsert("user-123", "用户偏好：使用 PostgreSQL 数据库");

		verify(mockVectorStore).delete(List.of("old-id"));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
		verify(mockVectorStore).add(captor.capture());

		List<Document> added = captor.getValue();
		assertThat(added).hasSize(1);
		assertThat(added.get(0).getText()).isEqualTo("用户偏好：使用 PostgreSQL 数据库");
		assertThat(added.get(0).getMetadata()).containsKey("updated_at");
	}
}
