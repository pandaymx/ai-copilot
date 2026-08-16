package xyz.ppmblszdp.ai.service;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import xyz.ppmblszdp.ai.dto.AuthDto;
import xyz.ppmblszdp.ai.repository.UserRepository;
import xyz.ppmblszdp.ai.security.JwtProvider;
import xyz.ppmblszdp.ai.security.PasswordHasher;

/**
 * 认证与 RBAC 权限核心业务服务（AuthService）。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final JwtProvider jwtProvider;

    public AuthService(UserRepository userRepository, PasswordHasher passwordHasher, JwtProvider jwtProvider) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.jwtProvider = jwtProvider;
    }

    public AuthDto.TokenPair login(AuthDto.LoginRequest req) {
        if (req.username() == null || req.username().isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (req.password() == null || req.password().isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }

        var userOpt = userRepository.findByUsername(req.username().trim());
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        var user = userOpt.get();
        if (!"ACTIVE".equalsIgnoreCase(user.status())) {
            throw new IllegalStateException("用户账号已被禁用，请联系管理员");
        }

        if (!passwordHasher.verify(req.password(), user.passwordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        return generateTokenPair(user);
    }

    public AuthDto.TokenPair register(AuthDto.RegisterRequest req) {
        if (req.username() == null || req.username().trim().length() < 3) {
            throw new IllegalArgumentException("用户名长度至少为 3 个字符");
        }
        if (req.password() == null || req.password().length() < 6) {
            throw new IllegalArgumentException("密码长度至少为 6 个字符");
        }

        String username = req.username().trim();
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("该用户名已被注册");
        }

        String role = (req.role() != null && !req.role().isBlank()) ? req.role().toUpperCase() : "USER";
        if (!"ADMIN".equals(role) && !"USER".equals(role) && !"GUEST".equals(role)) {
            role = "USER";
        }

        String userId = "user-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String passwordHash = passwordHasher.hash(req.password());
        long now = System.currentTimeMillis();

        var entity = new UserRepository.UserEntity(userId, username, passwordHash, role, "ACTIVE", now, now);
        userRepository.insertUser(entity);
        log.info("新用户注册成功: username={}, role={}", username, role);

        return generateTokenPair(entity);
    }

    public AuthDto.TokenPair refreshToken(AuthDto.RefreshRequest req) {
        var claims = jwtProvider.validateAndParse(req.refreshToken());
        if (claims == null) {
            throw new IllegalArgumentException("无效或已过期的 Refresh Token");
        }

        var user =
                userRepository.findById(claims.userId()).orElseThrow(() -> new IllegalArgumentException("用户不存在或已被删除"));

        if (!"ACTIVE".equalsIgnoreCase(user.status())) {
            throw new IllegalStateException("用户账号已被禁用");
        }

        return generateTokenPair(user);
    }

    public AuthDto.UserProfile getUserProfile(String userId) {
        var user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        List<String> perms = userRepository.findPermissionsByRole(user.role());
        return new AuthDto.UserProfile(user.id(), user.username(), user.role(), perms, user.createdAt());
    }

    public List<AuthDto.UserAdminDto> listAllUsers() {
        return userRepository.listAllUsers();
    }

    public void updateUserRole(String userId, String newRole) {
        if (!"ADMIN".equals(newRole) && !"USER".equals(newRole) && !"GUEST".equals(newRole)) {
            throw new IllegalArgumentException("不支持的角色类型: " + newRole);
        }
        userRepository.updateRole(userId, newRole);
        log.info("已更新用户 {} 角色为: {}", userId, newRole);
    }

    public void updateUserStatus(String userId, String status) {
        if (!"ACTIVE".equalsIgnoreCase(status) && !"DISABLED".equalsIgnoreCase(status)) {
            throw new IllegalArgumentException("不支持的状态: " + status);
        }
        userRepository.updateStatus(userId, status.toUpperCase());
        log.info("已更新用户 {} 状态为: {}", userId, status);
    }

    private AuthDto.TokenPair generateTokenPair(UserRepository.UserEntity user) {
        String accessToken = jwtProvider.generateAccessToken(user.id(), user.username(), user.role());
        String refreshToken = jwtProvider.generateRefreshToken(user.id(), user.username(), user.role());
        List<String> perms = userRepository.findPermissionsByRole(user.role());
        var profile = new AuthDto.UserProfile(user.id(), user.username(), user.role(), perms, user.createdAt());
        return new AuthDto.TokenPair(accessToken, refreshToken, jwtProvider.getAccessValiditySeconds(), profile);
    }
}
