package xyz.ppmblszdp.ai.controller;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import xyz.ppmblszdp.ai.dto.AuthDto;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;
import xyz.ppmblszdp.ai.service.AuthService;

/**
 * 认证授权对外 REST 控制器（AuthController）。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthProperties authProperties;

    public AuthController(AuthService authService, AuthProperties authProperties) {
        this.authService = authService;
        this.authProperties = authProperties;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthDto.LoginRequest req) {
        try {
            AuthDto.TokenPair tokenPair = authService.login(req);
            return ResponseEntity.ok(tokenPair);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthDto.RegisterRequest req) {
        try {
            AuthDto.TokenPair tokenPair = authService.register(req);
            return ResponseEntity.status(HttpStatus.CREATED).body(tokenPair);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody AuthDto.RefreshRequest req) {
        try {
            AuthDto.TokenPair tokenPair = authService.refreshToken(req);
            return ResponseEntity.ok(tokenPair);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        try {
            AuthDto.UserProfile profile = authService.getUserProfile(userId);
            return ResponseEntity.ok(profile);
        } catch (Exception e) {
            return ResponseEntity.ok(new AuthDto.UserProfile(
                    userId, userId, "USER", java.util.List.of("chat:create"), System.currentTimeMillis()));
        }
    }
}
