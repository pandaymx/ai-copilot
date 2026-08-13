package xyz.ppmblszdp.ai.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import xyz.ppmblszdp.ai.dto.MemoryDto;
import xyz.ppmblszdp.ai.dto.MemoryDto.ListResponse;
import xyz.ppmblszdp.ai.dto.MemoryDto.UpdateRequest;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;
import xyz.ppmblszdp.ai.service.MemoryService;

import java.util.Map;
import java.util.Optional;

/**
 * 长期记忆管理 Controller。
 *
 * <p>
 * 端点包括：列表查询（含状态过滤）、单条更新/优先级调整/归档、删除、衰减清理、摘要压缩、冲突检测与合并。
 * 所有端点从受信任 {@code X-User-Id} Header 读取用户身份。
 */
@RestController
@RequestMapping("/api/memory")
public class MemoryController {

	private final MemoryService memoryService;
	private final AuthProperties authProperties;

	public MemoryController(MemoryService memoryService, AuthProperties authProperties) {
		this.memoryService = memoryService;
		this.authProperties = authProperties;
	}

	private String resolveIdentity(ServerWebExchange exchange) {
		return UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
	}

	/** 列出当前用户的长期记忆（支持 status: active / archived / all，分页 + 关键字过滤） */
	@GetMapping
	public ResponseEntity<ListResponse> listMemories(
			ServerWebExchange exchange,
			@RequestParam(value = "keyword", required = false) String keyword,
			@RequestParam(value = "status", required = false, defaultValue = "active") String status,
			@RequestParam(value = "limit", defaultValue = "50") int limit,
			@RequestParam(value = "offset", defaultValue = "0") int offset) {
		String userId = resolveIdentity(exchange);
		int safeLimit = (limit <= 0 || limit > 200) ? 50 : limit;
		int safeOffset = (offset < 0) ? 0 : offset;
		ListResponse resp = memoryService.listMemories(userId, keyword, status, safeLimit, safeOffset);
		return ResponseEntity.ok(resp);
	}

	/** 编辑单条记忆（更新内容、分类、优先级权重、归档状态） */
	@PutMapping("/{id}")
	public ResponseEntity<MemoryDto> updateMemory(
			@PathVariable("id") String id,
			@RequestBody UpdateRequest request,
			ServerWebExchange exchange) {
		if (request == null) {
			return ResponseEntity.badRequest().build();
		}
		String userId = resolveIdentity(exchange);
		Optional<MemoryDto> updated = memoryService.updateMemory(
				id,
				userId,
				request.getContent(),
				request.getCategory(),
				request.getPriority(),
				request.getArchived());
		return updated.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}

	/** 删除单条记忆 */
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteMemory(
			@PathVariable("id") String id,
			ServerWebExchange exchange) {
		String userId = resolveIdentity(exchange);
		boolean deleted = memoryService.deleteMemory(id, userId);
		if (!deleted) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok().build();
	}

	/** 手动触发记忆优先级时间衰减与自动归档/清理 */
	@PostMapping("/decay")
	public ResponseEntity<Map<String, Integer>> decayMemories(ServerWebExchange exchange) {
		String userId = resolveIdentity(exchange);
		Map<String, Integer> stats = memoryService.decayMemories(userId);
		return ResponseEntity.ok(stats);
	}

	/** 手动触发细粒度记忆摘要压缩 */
	@PostMapping("/compress")
	public ResponseEntity<Map<String, Integer>> compressMemories(ServerWebExchange exchange) {
		String userId = resolveIdentity(exchange);
		int count = memoryService.compressMemories(userId);
		return ResponseEntity.ok(Map.of("compressedCategories", count));
	}

	/** 手动触发记忆冲突检测与合并 */
	@PostMapping("/resolve-conflicts")
	public ResponseEntity<Map<String, Integer>> resolveConflicts(ServerWebExchange exchange) {
		String userId = resolveIdentity(exchange);
		int resolved = memoryService.resolveConflicts(userId);
		return ResponseEntity.ok(Map.of("resolvedConflicts", resolved));
	}
}
