package xyz.ppmblszdp.ai.tool.email;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 邮件发送安全网关：校验收件人格式、白名单以及请求合规性。
 */
@Component
public class EmailSecurityGate {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public void validate(List<String> to, String subject, String body) {
        if (to == null || to.isEmpty()) {
            throw new IllegalArgumentException("收件人列表不能为空");
        }
        if (to.size() > 10) {
            throw new IllegalArgumentException("单次最多发送给 10 位收件人");
        }
        for (String addr : to) {
            if (addr == null || !EMAIL_PATTERN.matcher(addr.trim()).matches()) {
                throw new IllegalArgumentException("收件人邮箱地址格式无效: " + addr);
            }
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("邮件主题不能为空");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("邮件正文不能为空");
        }
    }
}
