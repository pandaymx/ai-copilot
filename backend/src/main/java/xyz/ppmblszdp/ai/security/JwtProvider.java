package xyz.ppmblszdp.ai.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 轻量级无依赖 JWT 令牌签发与校验器（HMAC-SHA256）。
 */
@Component
public class JwtProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final byte[] secretKey;
    private final long accessValidityMs;
    private final long refreshValidityMs;

    public record Claims(String userId, String username, String role, long exp, long iat) {}

    public JwtProvider(
            @Value("${app.auth.jwt.secret:ai-copilot-secure-jwt-signing-secret-key-32bytes-minimum}") String secret,
            @Value("${app.auth.jwt.access-expiry-seconds:900}") long accessExpirySec,
            @Value("${app.auth.jwt.refresh-expiry-seconds:604800}") long refreshExpirySec) {
        this.secretKey = secret.getBytes(StandardCharsets.UTF_8);
        this.accessValidityMs = accessExpirySec * 1000L;
        this.refreshValidityMs = refreshExpirySec * 1000L;
    }

    public String generateAccessToken(String userId, String username, String role) {
        return generateToken(userId, username, role, accessValidityMs);
    }

    public String generateRefreshToken(String userId, String username, String role) {
        return generateToken(userId, username, role, refreshValidityMs);
    }

    public long getAccessValiditySeconds() {
        return accessValidityMs / 1000L;
    }

    private String generateToken(String userId, String username, String role, long validityMs) {
        try {
            long now = System.currentTimeMillis();
            long exp = now + validityMs;

            Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
            Map<String, Object> payload = Map.of(
                    "sub", userId,
                    "username", username,
                    "role", role,
                    "iat", now / 1000L,
                    "exp", exp / 1000L);

            String encodedHeader =
                    Base64.getUrlEncoder().withoutPadding().encodeToString(MAPPER.writeValueAsBytes(header));
            String encodedPayload =
                    Base64.getUrlEncoder().withoutPadding().encodeToString(MAPPER.writeValueAsBytes(payload));

            String dataToSign = encodedHeader + "." + encodedPayload;
            String signature = sign(dataToSign);

            return dataToSign + "." + signature;
        } catch (Exception e) {
            throw new IllegalStateException("生成 JWT 令牌失败", e);
        }
    }

    public Claims validateAndParse(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String[] parts = token.trim().split("\\.");
        if (parts.length != 3) {
            return null;
        }

        String dataToSign = parts[0] + "." + parts[1];
        String expectedSig = sign(dataToSign);
        if (!MessageDigest.isEqual(
                expectedSig.getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
            log.warn("JWT 签名校验不通过");
            return null;
        }

        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> map = MAPPER.readValue(payloadBytes, new TypeReference<Map<String, Object>>() {});
            long exp = ((Number) map.get("exp")).longValue();
            long nowSec = System.currentTimeMillis() / 1000L;
            if (nowSec > exp) {
                log.debug("JWT 令牌已过期");
                return null;
            }

            String userId = (String) map.get("sub");
            String username = (String) map.get("username");
            String role = (String) map.get("role");
            long iat = ((Number) map.get("iat")).longValue();

            return new Claims(userId, username, role, exp, iat);
        } catch (Exception e) {
            log.warn("解析 JWT Payload 失败: {}", e.getMessage());
            return null;
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
            byte[] sigBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC SHA256 签名异常", e);
        }
    }
}
