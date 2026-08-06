package xyz.ppmblszdp.ai.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.ppmblszdp.ai.dto.SessionDto;
import xyz.ppmblszdp.ai.service.SessionService;

import java.util.List;

/**
 * 会话元数据与全量历史 Controller。
 */
@RestController
@RequestMapping("/api/chat/sessions")
public class SessionController {

	private final SessionService sessionService;

	public SessionController(SessionService sessionService) {
		this.sessionService = sessionService;
	}

	/** 获取全量历史会话列表 */
	@GetMapping
	public ResponseEntity<List<SessionDto>> getSessions() {
		return ResponseEntity.ok(sessionService.getAllSessions());
	}

	/** 获取单个会话的完整历史（元数据与消息） */
	@GetMapping("/{id}")
	public ResponseEntity<SessionDto.SessionDetail> getSessionDetail(@PathVariable("id") String id) {
		return sessionService.getSessionDetail(id)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	/** 重命名会话 */
	@PutMapping("/{id}/title")
	public ResponseEntity<Void> renameSession(
			@PathVariable("id") String id,
			@RequestBody SessionDto.RenameRequest request) {
		if (request == null || request.title() == null || request.title().isBlank()) {
			return ResponseEntity.badRequest().build();
		}
		sessionService.renameSession(id, request.title().trim());
		return ResponseEntity.ok().build();
	}

	/** 删除会话 */
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteSession(@PathVariable("id") String id) {
		sessionService.deleteSession(id);
		return ResponseEntity.ok().build();
	}
}
