package xyz.ppmblszdp.ai.repository;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import xyz.ppmblszdp.ai.dto.AuthDto;
import xyz.ppmblszdp.ai.security.PasswordHasher;

/**
 * RBAC 用户与权限体系持久化层（UserRepository）。
 */
@Repository
public class UserRepository {

    private static final Logger log = LoggerFactory.getLogger(UserRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final PasswordHasher passwordHasher;

    public record UserEntity(
            String id,
            String username,
            String passwordHash,
            String role,
            String status,
            long createdAt,
            long updatedAt) {}

    private final RowMapper<UserEntity> userRowMapper = (rs, rowNum) -> new UserEntity(
            rs.getString("id"),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("role"),
            rs.getString("status"),
            rs.getLong("created_at"),
            rs.getLong("updated_at"));

    public UserRepository(JdbcTemplate jdbcTemplate, PasswordHasher passwordHasher) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordHasher = passwordHasher;
    }

    @PostConstruct
    public void initSchema() {
        try {
            // 1. 用户表
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS app_users (
                    id VARCHAR(64) PRIMARY KEY,
                    username VARCHAR(64) UNIQUE NOT NULL,
                    password_hash VARCHAR(256) NOT NULL,
                    role VARCHAR(32) NOT NULL DEFAULT 'USER',
                    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                    created_at BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_app_users_username ON app_users(username);
            """);

            // 2. 角色表
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS app_roles (
                    id VARCHAR(32) PRIMARY KEY,
                    name VARCHAR(64) NOT NULL,
                    description TEXT
                );
            """);

            // 3. 角色权限关联表
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS app_role_permissions (
                    role_id VARCHAR(32) NOT NULL,
                    permission_code VARCHAR(64) NOT NULL,
                    PRIMARY KEY (role_id, permission_code)
                );
            """);

            // 4. 初始化默认角色
            jdbcTemplate.update(
                    "INSERT INTO app_roles (id, name, description) VALUES ('ADMIN', '超级管理员', '系统最高权限') ON CONFLICT (id) DO NOTHING");
            jdbcTemplate.update(
                    "INSERT INTO app_roles (id, name, description) VALUES ('USER', '普通用户', '标准 AI 对话与工具使用权限') ON CONFLICT (id) DO NOTHING");
            jdbcTemplate.update(
                    "INSERT INTO app_roles (id, name, description) VALUES ('GUEST', '只读访客', '基础对话浏览权限') ON CONFLICT (id) DO NOTHING");

            // 5. 初始化角色权限映射
            initRolePermissions();

            // 6. 初始化种子账号（若不存在）
            if (findByUsername("admin").isEmpty()) {
                String adminHash = passwordHasher.hash("admin123");
                long now = System.currentTimeMillis();
                jdbcTemplate.update(
                        "INSERT INTO app_users (id, username, password_hash, role, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        "user-admin-01",
                        "admin",
                        adminHash,
                        "ADMIN",
                        "ACTIVE",
                        now,
                        now);
                log.info("已自动初始化默认超级管理员账号: admin / admin123");
            }

            if (findByUsername("user").isEmpty()) {
                String userHash = passwordHasher.hash("user123");
                long now = System.currentTimeMillis();
                jdbcTemplate.update(
                        "INSERT INTO app_users (id, username, password_hash, role, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        "user-demo-01",
                        "user",
                        userHash,
                        "USER",
                        "ACTIVE",
                        now,
                        now);
                log.info("已自动初始化默认普通用户账号: user / user123");
            }
        } catch (Exception e) {
            log.warn("初始化 RBAC 用户表结构或默认种子数据失败: {}", e.getMessage());
        }
    }

    private void initRolePermissions() {
        String[] adminPerms = {
            "chat:create",
            "chat:delete",
            "knowledge:read",
            "knowledge:write",
            "tool:use",
            "admin:manage_users",
            "admin:api_keys"
        };
        for (String perm : adminPerms) {
            jdbcTemplate.update(
                    "INSERT INTO app_role_permissions (role_id, permission_code) VALUES ('ADMIN', ?) ON CONFLICT DO NOTHING",
                    perm);
        }

        String[] userPerms = {"chat:create", "chat:delete", "knowledge:read", "knowledge:write", "tool:use"};
        for (String perm : userPerms) {
            jdbcTemplate.update(
                    "INSERT INTO app_role_permissions (role_id, permission_code) VALUES ('USER', ?) ON CONFLICT DO NOTHING",
                    perm);
        }

        String[] guestPerms = {"knowledge:read", "chat:read"};
        for (String perm : guestPerms) {
            jdbcTemplate.update(
                    "INSERT INTO app_role_permissions (role_id, permission_code) VALUES ('GUEST', ?) ON CONFLICT DO NOTHING",
                    perm);
        }
    }

    public Optional<UserEntity> findByUsername(String username) {
        List<UserEntity> list = jdbcTemplate.query(
                "SELECT id, username, password_hash, role, status, created_at, updated_at FROM app_users WHERE username = ?",
                userRowMapper,
                username);
        return list.stream().findFirst();
    }

    public Optional<UserEntity> findById(String id) {
        List<UserEntity> list = jdbcTemplate.query(
                "SELECT id, username, password_hash, role, status, created_at, updated_at FROM app_users WHERE id = ?",
                userRowMapper,
                id);
        return list.stream().findFirst();
    }

    public void insertUser(UserEntity user) {
        jdbcTemplate.update(
                "INSERT INTO app_users (id, username, password_hash, role, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                user.id(),
                user.username(),
                user.passwordHash(),
                user.role(),
                user.status(),
                user.createdAt(),
                user.updatedAt());
    }

    public void updateRole(String userId, String role) {
        jdbcTemplate.update(
                "UPDATE app_users SET role = ?, updated_at = ? WHERE id = ?", role, System.currentTimeMillis(), userId);
    }

    public void updateStatus(String userId, String status) {
        jdbcTemplate.update(
                "UPDATE app_users SET status = ?, updated_at = ? WHERE id = ?",
                status,
                System.currentTimeMillis(),
                userId);
    }

    public List<AuthDto.UserAdminDto> listAllUsers() {
        return jdbcTemplate.query(
                "SELECT id, username, role, status, created_at, updated_at FROM app_users ORDER BY created_at ASC",
                (rs, rowNum) -> new AuthDto.UserAdminDto(
                        rs.getString("id"),
                        rs.getString("username"),
                        rs.getString("role"),
                        rs.getString("status"),
                        rs.getLong("created_at"),
                        rs.getLong("updated_at")));
    }

    public List<String> findPermissionsByRole(String role) {
        return jdbcTemplate.query(
                "SELECT permission_code FROM app_role_permissions WHERE role_id = ?",
                (rs, rowNum) -> rs.getString("permission_code"),
                role);
    }
}
