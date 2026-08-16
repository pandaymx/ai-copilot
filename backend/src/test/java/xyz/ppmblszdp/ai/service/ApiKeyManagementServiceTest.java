package xyz.ppmblszdp.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import xyz.ppmblszdp.ai.customtool.security.CredentialCipher;
import xyz.ppmblszdp.ai.dto.ApiKeyDto;
import xyz.ppmblszdp.ai.repository.ApiKeyRepository;
import xyz.ppmblszdp.ai.repository.ApiKeyRepository.ApiKeyEntity;

class ApiKeyManagementServiceTest {

    private ApiKeyRepository repository;
    private ApiKeyManagementService service;

    @BeforeEach
    void setUp() {
        repository = mock(ApiKeyRepository.class);
        service = new ApiKeyManagementService(repository);
    }

    @Test
    @DisplayName("保存合法 API Key 时应加密存储并返回生成的 ID")
    void shouldSaveAndEncryptKey() {
        when(repository.findByUserAndProvider("user-1", "deepseek")).thenReturn(Optional.empty());
        when(repository.save(eq("user-1"), eq("deepseek"), anyString())).thenReturn("key_123456");

        String id = service.save("user-1", "deepseek", "sk-1234567890abcdef123456");
        assertThat(id).isEqualTo("key_123456");
        verify(repository).save(eq("user-1"), eq("deepseek"), anyString());
    }

    @Test
    @DisplayName("保存占位符或无效 Key 时应抛出参数异常")
    void shouldRejectInvalidKey() {
        assertThatThrownBy(() -> service.save("user-1", "openai", "your_openai_api_key_here"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无效的 API Key");
    }

    @Test
    @DisplayName("查询用户 Key 列表时应返回脱敏掩码而非明文")
    void shouldReturnMaskedKeysInList() {
        String encrypted = CredentialCipher.encrypt("sk-proj-supersecretkey123456");
        ApiKeyEntity entity =
                new ApiKeyEntity("key_1", "user-1", "openai", encrypted, "ACTIVE", "$10.00", null, 1000L, 2000L);
        when(repository.findAllByUserId("user-1")).thenReturn(List.of(entity));

        List<ApiKeyDto> list = service.list("user-1");
        assertThat(list).hasSize(1);
        assertThat(list.get(0).maskedKey()).startsWith("sk-");
        assertThat(list.get(0).maskedKey()).contains("****");
        assertThat(list.get(0).maskedKey()).doesNotContain("supersecretkey");
    }

    @Test
    @DisplayName("删除 Key 时应调用仓库删除方法")
    void shouldDeleteKey() {
        when(repository.delete("key_1", "user-1")).thenReturn(true);
        boolean deleted = service.delete("key_1", "user-1");
        assertThat(deleted).isTrue();
        verify(repository).delete("key_1", "user-1");
    }
}
