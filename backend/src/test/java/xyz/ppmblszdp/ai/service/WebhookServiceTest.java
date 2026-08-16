package xyz.ppmblszdp.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.ppmblszdp.ai.dto.WebhookDto;
import xyz.ppmblszdp.ai.repository.WebhookRepository;

class WebhookServiceTest {

    private WebhookRepository webhookRepository;
    private WebhookService webhookService;

    @BeforeEach
    void setUp() {
        webhookRepository = mock(WebhookRepository.class);
        webhookService = new WebhookService(webhookRepository);
    }

    @Test
    void createSubscription_Success() {
        var req = new WebhookDto.WebhookCreateRequest(
                "飞书群机器人", "https://open.feishu.cn/open-apis/bot/v2/hook/xxx", "chat.completed", null);

        var sub = webhookService.createSubscription("user-1", req);

        assertThat(sub).isNotNull();
        assertThat(sub.id()).startsWith("wh_");
        assertThat(sub.secret()).startsWith("whsec_");
        assertThat(sub.name()).isEqualTo("飞书群机器人");
        verify(webhookRepository).saveSubscription(any());
    }

    @Test
    void updateSubscription_Success() {
        var existing = new WebhookDto.WebhookSubscriptionDto(
                "wh_1", "user-1", "旧名称", "https://example.com/hook", "chat.completed", "sec", true, null, null, 1000L);
        when(webhookRepository.findByIdAndUserId("wh_1", "user-1")).thenReturn(Optional.of(existing));

        var req = new WebhookDto.WebhookUpdateRequest("新名称", "https://example.com/new-hook", "error.occurred", false);
        webhookService.updateSubscription("wh_1", "user-1", req);

        verify(webhookRepository)
                .updateSubscription("wh_1", "user-1", "新名称", "https://example.com/new-hook", "error.occurred", false);
    }

    @Test
    void listSubscriptions_Success() {
        var sub = new WebhookDto.WebhookSubscriptionDto(
                "wh_1", "user-1", "测试", "https://example.com", "*", "sec", true, "SUCCESS", null, 1000L);
        when(webhookRepository.findSubscriptionsByUserId("user-1")).thenReturn(List.of(sub));

        var list = webhookService.listSubscriptions("user-1");
        assertThat(list).hasSize(1);
        assertThat(list.get(0).name()).isEqualTo("测试");
    }
}
