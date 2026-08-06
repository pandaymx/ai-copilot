package xyz.ppmblszdp.ai.memory;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SafeVectorStoreTest {

	@Test
	void similaritySearch_shouldReturnEmptyList_whenDelegateThrowsException() {
		VectorStore mockVs = mock(VectorStore.class);
		when(mockVs.similaritySearch(any(SearchRequest.class)))
				.thenThrow(new RuntimeException("404: Unknown (Simulated OpenAI 404 Error)"));

		SafeVectorStore safeVs = new SafeVectorStore(mockVs);
		List<Document> results = safeVs.similaritySearch(SearchRequest.builder().query("hello").build());

		assertThat(results).isNotNull().isEmpty();
	}

	@Test
	void add_shouldNotThrow_whenDelegateThrowsException() {
		VectorStore mockVs = mock(VectorStore.class);
		doThrow(new RuntimeException("DB error")).when(mockVs).add(any());

		SafeVectorStore safeVs = new SafeVectorStore(mockVs);
		safeVs.add(List.of(new Document("test")));
	}
}
