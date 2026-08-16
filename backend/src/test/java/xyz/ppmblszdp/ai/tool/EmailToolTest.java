package xyz.ppmblszdp.ai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.repository.EmailRepository;
import xyz.ppmblszdp.ai.tool.email.EmailDto;
import xyz.ppmblszdp.ai.tool.email.EmailSecurityGate;
import xyz.ppmblszdp.ai.tool.email.MailSenderClient;

class EmailToolTest {

    private EmailRepository repository;
    private EmailSecurityGate securityGate;
    private MailSenderClient mailSender;
    private EmailTool emailTool;
    private ToolContext toolContext;

    @BeforeEach
    void setUp() {
        repository = mock(EmailRepository.class);
        securityGate = new EmailSecurityGate();
        mailSender = mock(MailSenderClient.class);
        emailTool = new EmailTool(repository, securityGate, mailSender);

        AiProviderProperties props = mock(AiProviderProperties.class);
        AiProviderProperties.AgentConfig agentConfig = mock(AiProviderProperties.AgentConfig.class);
        when(props.resolveAgent()).thenReturn(agentConfig);
        when(agentConfig.resolveMaxToolCalls()).thenReturn(5);
        when(agentConfig.resolveTimeoutSeconds()).thenReturn(30);

        ToolEventEmitter emitter = new ToolEventEmitter(props);
        var sink = emitter.newSink();

        Map<String, Object> ctxMap = new HashMap<>();
        ctxMap.put(ToolEventEmitter.CTX_USER_ID, "user-test");
        ctxMap.put(ToolEventEmitter.CTX_EMITTER, emitter);
        ctxMap.put("eventSink", sink);

        toolContext = new ToolContext(ctxMap);
    }

    @Test
    void sendEmail_Success() {
        when(mailSender.send(any(), anyString(), anyString(), anyBoolean())).thenReturn("msg_123456");

        String payload = """
            {
                "to": ["partner@example.com"],
                "subject": "产品合作方案",
                "body": "您好，附件为最新方案...",
                "isHtml": false
            }
        """;

        String resultJson = emailTool.emailTool("SEND", payload, toolContext);

        assertThat(resultJson).contains("SENT");
        assertThat(resultJson).contains("msg_123456");
        verify(repository).save(any(EmailDto.EmailHistoryItem.class));
    }

    @Test
    void draftEmail_Success() {
        String payload = """
            {
                "to": ["client@example.com"],
                "subject": "会议纪要草稿",
                "body": "本次会议重点内容如下..."
            }
        """;

        String resultJson = emailTool.emailTool("DRAFT", payload, toolContext);

        assertThat(resultJson).contains("DRAFT");
        assertThat(resultJson).contains("会议纪要草稿");
    }

    @Test
    void listHistory_Success() {
        var item = new EmailDto.EmailHistoryItem(
                "msg_1", "user-test", List.of("alice@example.com"), "测试主题", "内容摘要", false, "SENT", 1000L);
        when(repository.findByUserId("user-test", 20)).thenReturn(List.of(item));

        String resultJson = emailTool.emailTool("LIST_HISTORY", "{}", toolContext);

        assertThat(resultJson).contains("LIST_HISTORY");
        assertThat(resultJson).contains("alice@example.com");
    }
}
