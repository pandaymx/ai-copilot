package xyz.ppmblszdp.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.ppmblszdp.ai.dto.ShareDto;
import xyz.ppmblszdp.ai.repository.ShareRepository;
import xyz.ppmblszdp.ai.security.PasswordHasher;

class ShareServiceTest {

    private ShareRepository shareRepository;
    private PasswordHasher passwordHasher;
    private ShareService shareService;

    @BeforeEach
    void setUp() {
        shareRepository = mock(ShareRepository.class);
        passwordHasher = new PasswordHasher();
        shareService = new ShareService(shareRepository, passwordHasher);
    }

    @Test
    void createShare_Success() {
        var req = new ShareDto.ShareCreateRequest(
                "架构设计讨论", "[{\"role\":\"user\",\"content\":\"hello\"}]", null, "123456");

        ShareDto.ShareMetaDto meta = shareService.createShare("sess-1", "user-1", req);

        assertThat(meta).isNotNull();
        assertThat(meta.token()).startsWith("s_");
        assertThat(meta.title()).isEqualTo("架构设计讨论");
        assertThat(meta.hasPassword()).isTrue();
        verify(shareRepository).insert(any());
    }

    @Test
    void resolveSnapshot_Success() {
        String pwdHash = passwordHasher.hash("mypassword");
        var entity = new ShareRepository.ShareEntity(
                "s_token1",
                "sess-1",
                "user-1",
                "测试快照",
                "[{\"role\":\"user\",\"content\":\"hi\"}]",
                null,
                pwdHash,
                5L,
                1000L);

        when(shareRepository.findByToken("s_token1")).thenReturn(Optional.of(entity));

        ShareDto.ShareSnapshotView view = shareService.resolveSnapshot("s_token1", "mypassword");

        assertThat(view).isNotNull();
        assertThat(view.title()).isEqualTo("测试快照");
        assertThat(view.messagesJson()).contains("hi");
        verify(shareRepository).incrementViewCount("s_token1");
    }

    @Test
    void resolveSnapshot_WrongPassword_ThrowsSecurityException() {
        String pwdHash = passwordHasher.hash("mypassword");
        var entity =
                new ShareRepository.ShareEntity("s_token2", "sess-1", "user-1", "测试快照", "[]", null, pwdHash, 0L, 1000L);

        when(shareRepository.findByToken("s_token2")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> shareService.resolveSnapshot("s_token2", "wrong"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("密码错误");
    }

    @Test
    void resolveSnapshot_Expired_ThrowsIllegalStateException() {
        long pastTime = System.currentTimeMillis() - 10000;
        var entity = new ShareRepository.ShareEntity(
                "s_token3", "sess-1", "user-1", "过期快照", "[]", pastTime, null, 0L, 1000L);

        when(shareRepository.findByToken("s_token3")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> shareService.resolveSnapshot("s_token3", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已过期失效");
    }
}
