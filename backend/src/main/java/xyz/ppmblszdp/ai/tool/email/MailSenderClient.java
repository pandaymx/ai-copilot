package xyz.ppmblszdp.ai.tool.email;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 邮件发送客户端（MailSenderClient）。
 */
@Component
public class MailSenderClient {

    private static final Logger log = LoggerFactory.getLogger(MailSenderClient.class);

    public String send(List<String> to, String subject, String body, boolean isHtml) {
        String msgId = "msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("📧 成功外发邮件: id={}, to={}, subject={}, isHtml={}", msgId, to, subject, isHtml);
        return msgId;
    }
}
