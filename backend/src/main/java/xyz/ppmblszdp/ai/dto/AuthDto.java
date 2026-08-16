package xyz.ppmblszdp.ai.dto;

import java.util.List;

/**
 * 认证与 RBAC 权限数据传输对象（Auth DTOs）。
 */
public class AuthDto {

    public record LoginRequest(String username, String password) {}

    public record RegisterRequest(String username, String password, String role) {}

    public record RefreshRequest(String refreshToken) {}

    public record UpdateRoleRequest(String role) {}

    public record UpdateStatusRequest(String status) {}

    public record UserProfile(String id, String username, String role, List<String> permissions, long createdAt) {}

    public record TokenPair(String accessToken, String refreshToken, long expiresIn, UserProfile user) {}

    public record UserAdminDto(
            String id, String username, String role, String status, long createdAt, long updatedAt) {}
}
