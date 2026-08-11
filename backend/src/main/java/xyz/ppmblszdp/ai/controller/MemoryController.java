package xyz.ppmblszdp.ai.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

import java.util.Optional;

/**
 * 长期记忆管理 Controller。
 *
 * <p>
 * 所有端点均经身份解析：从受信任 {@code X-User-Id} Header 读取用户身份，
 * 跨用户访问返回 404（不可区分「不存在」与「不属于你」）。严格复用
 * {@link UserIdentityFilter#resolveIdentity} 模式，绝不信任请求体 userId。
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

	/** 从请求交换解析当前用户身份（严格模式缺 Header 抛 401） */
	private String resolveIdentity(ServerWebExchange exchange) {
		return UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
	}

	/** 列出当前用户的全部长期记忆（分页 + 关键字过滤） */
	@GetMapping
	public ResponseEntity<ListResponse> listMemories(
			ServerWebExchange exchange,
			@RequestParam(value = "keyword", required = false) String keyword,
			@RequestParam(value = "limit", defaultValue = "50") int limit,
			@RequestParam(value = "offset", defaultValue = "0") int offset) {
		String userId = resolveIdentity(exchange);
		int safeLimit = (limit <= 0 || limit > 200) ? 50 : limit;
		int safeOffset = (offset < 0) ? 0 : offset;
		ListResponse resp = memoryService.listMemories(userId, keyword, safeLimit, safeOffset);
		return ResponseEntity.ok(resp);
	}

	/** 编辑单条记忆（仅所有者，跨用户返回 404） */
	@PutMapping("/{id}")
	public ResponseEntity<MemoryDto> updateMemory(
			@PathVariable("id") String id,
			@RequestBody UpdateRequest request,
			ServerWebExchange exchange) {
		if (request == null || request.getContent() == null || request.getContent().isBlank()) {
			return ResponseEntity.badRequest().build();
		}
		String userId = resolveIdentity(exchange);
		Optional<MemoryDto> updated = memoryService.updateMemory(id, userId, request.getContent(),
				request.getCategory());
		return updated.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}

	/** 删除单条记忆（仅所有者，跨用户返回 404） */
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
}
