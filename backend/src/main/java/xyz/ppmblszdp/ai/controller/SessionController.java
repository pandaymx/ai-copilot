package xyz.ppmblszdp.ai.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import xyz.ppmblszdp.ai.dto.SessionDto;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;
import xyz.ppmblszdp.ai.service.SessionService;

import java.util.List;

/**
 * 会话元数据与全量历史 Controller。
 *
 * <p>所有端点均需经身份解析：从受信任 {@code X-User-Id} Header 读取用户身份，
 * 跨用户访问返回 404（不可区分「不存在」与「不属于你」）。
 */
@RestController
@RequestMapping("/api/chat/sessions")
public class SessionController {

	private final SessionService sessionService;
	private final AuthProperties authProperties;

	public SessionController(SessionService sessionService, AuthProperties authProperties) {
		this.sessionService = sessionService;
		this.authProperties = authProperties;
	}

	/** 从请求交换解析当前用户身份（严格模式缺 Header 抛 401） */
	private String resolveIdentity(ServerWebExchange exchange) {
		return UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
	}

	/** 获取当前用户的历史会话列表（按用户隔离） */
	@GetMapping
	public ResponseEntity<List<SessionDto>> getSessions(ServerWebExchange exchange) {
		String userId = resolveIdentity(exchange);
		return ResponseEntity.ok(sessionService.getAllSessions(userId));
	}

	/** 获取单个会话的完整历史（元数据与消息），跨用户返回 404 */
	@GetMapping("/{id}")
	public ResponseEntity<SessionDto.SessionDetail> getSessionDetail(
			@PathVariable("id") String id,
			ServerWebExchange exchange) {
		String userId = resolveIdentity(exchange);
		return sessionService.getSessionDetail(id, userId)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	/** 重命名会话（仅会话所有者），跨用户返回 404 */
	@PutMapping("/{id}/title")
	public ResponseEntity<Void> renameSession(
			@PathVariable("id") String id,
			@RequestBody SessionDto.RenameRequest request,
			ServerWebExchange exchange) {
		if (request == null || request.title() == null || request.title().isBlank()) {
			return ResponseEntity.badRequest().build();
		}
		String userId = resolveIdentity(exchange);
		// 归属校验：不存在或不属于该用户 -> 404
		if (sessionService.findSession(id, userId).isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		sessionService.renameSession(id, userId, request.title().trim());
		return ResponseEntity.ok().build();
	}

	/** 删除会话（仅所有者），跨用户返回 404 */
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteSession(
			@PathVariable("id") String id,
			ServerWebExchange exchange) {
		String userId = resolveIdentity(exchange);
		boolean deleted = sessionService.deleteSession(id, userId);
		if (!deleted) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok().build();
	}
}
