package xyz.ppmblszdp.ai.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import xyz.ppmblszdp.ai.dto.ShareDto;
import xyz.ppmblszdp.ai.repository.ShareRepository;
import xyz.ppmblszdp.ai.security.PasswordHasher;

/**
 * 会话在线分享与只读快照业务服务（ShareService）。
 */
@Service
public class ShareService {

    private static final Logger log = LoggerFactory.getLogger(ShareService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ShareRepository shareRepository;
    private final PasswordHasher passwordHasher;

    public ShareService(ShareRepository shareRepository, PasswordHasher passwordHasher) {
        this.shareRepository = shareRepository;
        this.passwordHasher = passwordHasher;
    }

    public ShareDto.ShareMetaDto createShare(String sessionId, String userId, ShareDto.ShareCreateRequest req) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        if (req.messagesJson() == null || req.messagesJson().isBlank()) {
            throw new IllegalArgumentException("快照消息内容不能为空");
        }

        byte[] tokenBytes = new byte[12];
        RANDOM.nextBytes(tokenBytes);
        String token = "s_" + Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        String passwordHash = (req.password() != null && !req.password().isBlank())
                ? passwordHasher.hash(req.password().trim())
                : null;

        long now = System.currentTimeMillis();
        String title =
                (req.title() != null && !req.title().isBlank()) ? req.title().trim() : "AI 对话分享";

        var entity = new ShareRepository.ShareEntity(
                token, sessionId, userId, title, req.messagesJson(), req.expireAt(), passwordHash, 0L, now);

        shareRepository.insert(entity);
        log.info("已生成对话分享快照: token={}, sessionId={}, userId={}", token, sessionId, userId);

        return new ShareDto.ShareMetaDto(
                token, sessionId, userId, title, req.expireAt(), passwordHash != null, 0L, now);
    }

    public ShareDto.ShareSnapshotView resolveSnapshot(String token, String password) {
        var entity = shareRepository.findByToken(token).orElseThrow(() -> new IllegalArgumentException("分享链接不存在或已被撤销"));

        if (entity.expireAt() != null && System.currentTimeMillis() > entity.expireAt()) {
            throw new IllegalStateException("该分享链接已过期失效");
        }

        if (entity.passwordHash() != null) {
            if (password == null || password.isBlank()) {
                throw new SecurityException("此分享受密码保护，请输入访问密码");
            }
            if (!passwordHasher.verify(password.trim(), entity.passwordHash())) {
                throw new SecurityException("访问密码错误，请重新输入");
            }
        }

        shareRepository.incrementViewCount(token);
        return new ShareDto.ShareSnapshotView(
                entity.token(), entity.title(), entity.snapshotJson(), entity.createdAt(), entity.viewCount() + 1);
    }

    public boolean requiresPassword(String token) {
        return shareRepository
                .findByToken(token)
                .map(e -> e.passwordHash() != null)
                .orElse(false);
    }

    public void revokeShare(String token, String userId) {
        int deleted = shareRepository.deleteByTokenAndUserId(token, userId);
        if (deleted == 0) {
            throw new IllegalArgumentException("未找到可撤销的分享链接，或无权操作");
        }
        log.info("用户 {} 已撤销分享: token={}", userId, token);
    }

    public List<ShareDto.ShareMetaDto> listSessionShares(String sessionId, String userId) {
        return shareRepository.listBySessionId(sessionId, userId).stream()
                .map(e -> new ShareDto.ShareMetaDto(
                        e.token(),
                        e.sessionId(),
                        e.userId(),
                        e.title(),
                        e.expireAt(),
                        e.passwordHash() != null,
                        e.viewCount(),
                        e.createdAt()))
                .toList();
    }
}
