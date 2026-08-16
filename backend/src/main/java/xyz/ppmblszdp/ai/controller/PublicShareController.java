package xyz.ppmblszdp.ai.controller;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.ppmblszdp.ai.dto.ShareDto;
import xyz.ppmblszdp.ai.service.ShareService;

/**
 * 免登录公开只读分享访问控制器（PublicShareController）。
 */
@RestController
@RequestMapping("/api/public/shares")
public class PublicShareController {

    private final ShareService shareService;

    public PublicShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    @GetMapping("/{token}/check")
    public ResponseEntity<?> checkShare(@PathVariable String token) {
        try {
            boolean needPwd = shareService.requiresPassword(token);
            return ResponseEntity.ok(Map.of("token", token, "requiresPassword", needPwd));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "分享不存在"));
        }
    }

    @PostMapping("/{token}")
    public ResponseEntity<?> resolveShare(
            @PathVariable String token, @RequestBody(required = false) ShareDto.ShareResolveRequest req) {
        String password = req != null ? req.password() : null;
        try {
            ShareDto.ShareSnapshotView view = shareService.resolveSnapshot(token, password);
            return ResponseEntity.ok(view);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage(), "requiresPassword", true));
        }
    }
}
