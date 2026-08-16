package xyz.ppmblszdp.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.ppmblszdp.ai.dto.AuthDto;
import xyz.ppmblszdp.ai.repository.UserRepository;
import xyz.ppmblszdp.ai.security.JwtProvider;
import xyz.ppmblszdp.ai.security.PasswordHasher;

class AuthServiceTest {

    private UserRepository userRepository;
    private PasswordHasher passwordHasher;
    private JwtProvider jwtProvider;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordHasher = new PasswordHasher();
        jwtProvider = new JwtProvider("test-secret-key-must-be-at-least-32-characters-long", 900, 604800);
        authService = new AuthService(userRepository, passwordHasher, jwtProvider);
    }

    @Test
    void registerAndLogin_Success() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userRepository.findPermissionsByRole("USER")).thenReturn(List.of("chat:create", "tool:use"));

        AuthDto.TokenPair registered =
                authService.register(new AuthDto.RegisterRequest("alice", "password123", "USER"));

        assertThat(registered).isNotNull();
        assertThat(registered.accessToken()).isNotBlank();
        assertThat(registered.refreshToken()).isNotBlank();
        assertThat(registered.user().username()).isEqualTo("alice");
        assertThat(registered.user().role()).isEqualTo("USER");
        verify(userRepository).insertUser(any());

        // 测试登录
        String hashedPwd = passwordHasher.hash("password123");
        UserRepository.UserEntity mockUser = new UserRepository.UserEntity(
                registered.user().id(), "alice", hashedPwd, "USER", "ACTIVE", 1000L, 1000L);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(mockUser));

        AuthDto.TokenPair loggedIn = authService.login(new AuthDto.LoginRequest("alice", "password123"));
        assertThat(loggedIn.accessToken()).isNotBlank();
        assertThat(loggedIn.user().id()).isEqualTo(registered.user().id());
    }

    @Test
    void login_InvalidPassword_ThrowsException() {
        String hashedPwd = passwordHasher.hash("correct-pwd");
        UserRepository.UserEntity mockUser =
                new UserRepository.UserEntity("u1", "bob", hashedPwd, "USER", "ACTIVE", 1000L, 1000L);
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(mockUser));

        assertThatThrownBy(() -> authService.login(new AuthDto.LoginRequest("bob", "wrong-pwd")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    @Test
    void login_DisabledUser_ThrowsException() {
        String hashedPwd = passwordHasher.hash("mypassword");
        UserRepository.UserEntity mockUser =
                new UserRepository.UserEntity("u2", "carol", hashedPwd, "USER", "DISABLED", 1000L, 1000L);
        when(userRepository.findByUsername("carol")).thenReturn(Optional.of(mockUser));

        assertThatThrownBy(() -> authService.login(new AuthDto.LoginRequest("carol", "mypassword")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已被禁用");
    }

    @Test
    void updateUserRole_Success() {
        authService.updateUserRole("u1", "ADMIN");
        verify(userRepository).updateRole(eq("u1"), eq("ADMIN"));

        assertThatThrownBy(() -> authService.updateUserRole("u1", "INVALID_ROLE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的角色类型");
    }
}
