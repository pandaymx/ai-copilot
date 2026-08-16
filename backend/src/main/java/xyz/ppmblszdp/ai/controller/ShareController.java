package xyz.ppmblszdp.ai.controller;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import xyz.ppmblszdp.ai.dto.ShareDto;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;
import xyz.ppmblszdp.ai.service.ShareService;

/**
 * 用户侧分享管理 REST 控制器（ShareController）。
 */
@RestController
@RequestMapping("/api")
public class ShareController {

    private final ShareService shareService;
    private final AuthProperties authProperties;

    public ShareController(ShareService shareService, AuthProperties authProperties) {
        this.shareService = shareService;
        this.authProperties = authProperties;
    }

    @PostMapping("/sessions/{id}/share")
    public ResponseEntity<?> createShare(
            @PathVariable String id, @RequestBody ShareDto.ShareCreateRequest req, ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        try {
            ShareDto.ShareMetaDto meta = shareService.createShare(id, userId, req);
            return ResponseEntity.status(HttpStatus.CREATED).body(meta);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/sessions/{id}/shares")
    public ResponseEntity<?> listShares(@PathVariable String id, ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        List<ShareDto.ShareMetaDto> list = shareService.listSessionShares(id, userId);
        return ResponseEntity.ok(list);
    }

    @DeleteMapping("/shares/{token}")
    public ResponseEntity<?> revokeShare(@PathVariable String token, ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        try {
            shareService.revokeShare(token, userId);
            return ResponseEntity.ok(Map.of("success", true, "message", "已成功撤销该分享链接"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }
}
