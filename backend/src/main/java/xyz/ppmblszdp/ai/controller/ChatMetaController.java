package xyz.ppmblszdp.ai.controller;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import xyz.ppmblszdp.ai.dto.MessageMetaDto;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;
import xyz.ppmblszdp.ai.repository.BookmarkRepository;

/**
 * 消息固定（Pin）、收藏（Bookmark）与标签 REST 控制器（ChatMetaController）。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatMetaController {

    private final BookmarkRepository bookmarkRepository;
    private final AuthProperties authProperties;

    public ChatMetaController(BookmarkRepository bookmarkRepository, AuthProperties authProperties) {
        this.bookmarkRepository = bookmarkRepository;
        this.authProperties = authProperties;
    }

    @PostMapping("/messages/{messageId}/bookmark")
    public ResponseEntity<MessageMetaDto.MessageStatusResponse> toggleBookmark(
            @PathVariable String messageId,
            @RequestBody MessageMetaDto.ToggleBookmarkRequest req,
            ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        var res = bookmarkRepository.toggleBookmark(
                userId,
                req.sessionId() != null ? req.sessionId() : "default",
                messageId,
                req.role() != null ? req.role() : "assistant",
                req.content() != null ? req.content() : "",
                req.tags());
        return ResponseEntity.ok(res);
    }

    @PostMapping("/messages/{messageId}/pin")
    public ResponseEntity<MessageMetaDto.MessageStatusResponse> togglePin(
            @PathVariable String messageId,
            @RequestBody MessageMetaDto.TogglePinRequest req,
            ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        var res = bookmarkRepository.togglePin(
                userId,
                req.sessionId() != null ? req.sessionId() : "default",
                messageId,
                req.role() != null ? req.role() : "assistant",
                req.content() != null ? req.content() : "");
        return ResponseEntity.ok(res);
    }

    @PutMapping("/messages/{messageId}/tags")
    public ResponseEntity<?> updateTags(
            @PathVariable String messageId,
            @RequestBody MessageMetaDto.UpdateTagsRequest req,
            ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        bookmarkRepository.updateTags(userId, messageId, req.tags());
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/bookmarks")
    public ResponseEntity<List<MessageMetaDto.MessageBookmarkDto>> listBookmarks(ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        return ResponseEntity.ok(bookmarkRepository.listUserBookmarks(userId));
    }

    @GetMapping("/sessions/{sessionId}/pinned")
    public ResponseEntity<List<MessageMetaDto.MessageBookmarkDto>> listPinned(
            @PathVariable String sessionId, ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        return ResponseEntity.ok(bookmarkRepository.listSessionPinned(userId, sessionId));
    }

    @GetMapping("/messages/{messageId}/meta")
    public ResponseEntity<?> getMessageMeta(@PathVariable String messageId, ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        var meta = bookmarkRepository.findByMessageId(userId, messageId);
        return meta.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.ok().build());
    }
}
