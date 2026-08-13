package xyz.ppmblszdp.ai.controller;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import xyz.ppmblszdp.ai.dto.MemoryDto;
import xyz.ppmblszdp.ai.dto.MemoryDto.ListResponse;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;
import xyz.ppmblszdp.ai.service.MemoryService;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MemoryController 切片测试：直接绑定 Controller 实例 + Mock Service。
 */
class MemoryControllerTest {

	private MemoryService memoryService;
	private MemoryController controller;
	private WebTestClient webClient;

	private static final String USER = "user-abc";

	@BeforeEach
	void setUp() {
		memoryService = mock(MemoryService.class);
		AuthProperties authProperties = new AuthProperties("strict", "X-User-Id", Set.of("admin"));
		controller = new MemoryController(memoryService, authProperties);
		webClient = WebTestClient.bindToController(controller)
				.webFilter(new UserIdentityFilter(authProperties))
				.build();
	}

	@Test
	void listMemories_shouldReturnItemsForCurrentUser() {
		ListResponse resp = new ListResponse(
				List.of(new MemoryDto("m1", "用户偏好：Java 25", "技术栈偏好", 0.9, "2026-01-01T00:00:00Z")), 1L);
		when(memoryService.listMemories(eq(USER), isNull(), eq("active"), anyInt(), anyInt())).thenReturn(resp);

		webClient.get().uri("/api/memory").header("X-User-Id", USER)
				.exchange()
				.expectStatus().isOk()
				.expectBody(ListResponse.class)
				.value(r -> {
					Assertions.assertThat(r.getTotal()).isEqualTo(1L);
					Assertions.assertThat(r.getItems()).hasSize(1);
					Assertions.assertThat(r.getItems().get(0).getId()).isEqualTo("m1");
				});
	}

	@Test
	void updateMemory_shouldReturnUpdated_whenOwner() {
		MemoryDto updated = new MemoryDto("m1", "用户偏好：Kotlin", "技术栈偏好", 0.9, "2026-01-02T00:00:00Z");
		when(memoryService.updateMemory(eq("m1"), eq(USER), eq("用户偏好：Kotlin"), eq("技术栈偏好"), isNull(), isNull()))
				.thenReturn(Optional.of(updated));

		webClient.put().uri("/api/memory/m1").header("X-User-Id", USER)
				.header("Content-Type", "application/json")
				.bodyValue("{\"content\":\"用户偏好：Kotlin\",\"category\":\"技术栈偏好\"}")
				.exchange()
				.expectStatus().isOk()
				.expectBody(MemoryDto.class)
				.value(d -> Assertions.assertThat(d.getContent()).isEqualTo("用户偏好：Kotlin"));
	}

	@Test
	void updateMemory_shouldReturn404_whenNotOwnerOrMissing() {
		when(memoryService.updateMemory(eq("m1"), eq(USER), any(), any(), any(), any())).thenReturn(Optional.empty());

		webClient.put().uri("/api/memory/m1").header("X-User-Id", USER)
				.header("Content-Type", "application/json")
				.bodyValue("{\"content\":\"x\"}")
				.exchange()
				.expectStatus().isNotFound();
	}

	@Test
	void deleteMemory_shouldReturnOk_whenOwner() {
		when(memoryService.deleteMemory(eq("m1"), eq(USER))).thenReturn(true);

		webClient.delete().uri("/api/memory/m1").header("X-User-Id", USER)
				.exchange()
				.expectStatus().isOk();
	}

	@Test
	void decayMemories_shouldReturnStats() {
		when(memoryService.decayMemories(eq(USER))).thenReturn(Map.of("archived", 2, "deleted", 1));

		webClient.post().uri("/api/memory/decay").header("X-User-Id", USER)
				.exchange()
				.expectStatus().isOk()
				.expectBody(Map.class)
				.value(m -> {
					Assertions.assertThat(m.get("archived")).isEqualTo(2);
					Assertions.assertThat(m.get("deleted")).isEqualTo(1);
				});
	}

	@Test
	void compressMemories_shouldReturnCount() {
		when(memoryService.compressMemories(eq(USER))).thenReturn(3);

		webClient.post().uri("/api/memory/compress").header("X-User-Id", USER)
				.exchange()
				.expectStatus().isOk()
				.expectBody(Map.class)
				.value(m -> Assertions.assertThat(m.get("compressedCategories")).isEqualTo(3));
	}

	@Test
	void resolveConflicts_shouldReturnResolvedCount() {
		when(memoryService.resolveConflicts(eq(USER))).thenReturn(2);

		webClient.post().uri("/api/memory/resolve-conflicts").header("X-User-Id", USER)
				.exchange()
				.expectStatus().isOk()
				.expectBody(Map.class)
				.value(m -> Assertions.assertThat(m.get("resolvedConflicts")).isEqualTo(2));
	}
}
