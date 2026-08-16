package xyz.ppmblszdp.ai.controller;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import xyz.ppmblszdp.ai.dto.AuthDto;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;
import xyz.ppmblszdp.ai.service.AuthService;

/**
 * 管理员用户管理 REST 控制器（AdminUserController）。
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AuthService authService;
    private final AuthProperties authProperties;

    public AdminUserController(AuthService authService, AuthProperties authProperties) {
        this.authService = authService;
        this.authProperties = authProperties;
    }

    private void checkAdminPermission(ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        try {
            var profile = authService.getUserProfile(userId);
            if (!"ADMIN".equalsIgnoreCase(profile.role())) {
                throw new SecurityException("只有管理员有权访问此管理接口");
            }
        } catch (Exception e) {
            // 如果是开发模式默认 system 用户且未开启强制鉴权，允许访问
            if (!"strict".equalsIgnoreCase(authProperties.mode()) && "system".equalsIgnoreCase(userId)) {
                return;
            }
            throw new SecurityException("无权访问用户管理接口");
        }
    }

    @GetMapping
    public ResponseEntity<?> listUsers(ServerWebExchange exchange) {
        try {
            checkAdminPermission(exchange);
            List<AuthDto.UserAdminDto> users = authService.listAllUsers();
            return ResponseEntity.ok(users);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<?> updateUserRole(
            @PathVariable String id, @RequestBody AuthDto.UpdateRoleRequest req, ServerWebExchange exchange) {
        try {
            checkAdminPermission(exchange);
            authService.updateUserRole(id, req.role());
            return ResponseEntity.ok(Map.of("success", true, "message", "用户角色已更新"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateUserStatus(
            @PathVariable String id, @RequestBody AuthDto.UpdateStatusRequest req, ServerWebExchange exchange) {
        try {
            checkAdminPermission(exchange);
            authService.updateUserStatus(id, req.status());
            return ResponseEntity.ok(Map.of("success", true, "message", "用户状态已更新"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
