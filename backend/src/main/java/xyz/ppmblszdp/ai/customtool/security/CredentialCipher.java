package xyz.ppmblszdp.ai.customtool.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 敏感凭据对称加解密工具类（AES-256-GCM 工业级标准）。
 *
 * <p>用于对 HTTP 自定义工具的 Bearer Token / API Key 等敏感信息在落盘存储前加密，
 * 防止数据库泄露导致凭据外泄。
 */
public final class CredentialCipher {

    private static final Logger log = LoggerFactory.getLogger(CredentialCipher.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96-bit IV
    private static final int GCM_TAG_LENGTH = 128; // 128-bit authentication tag
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** 掩码特征标记（用于识别前端回传的是否为未修改的掩码） */
    public static final String MASK_PATTERN = "****";

    /**
     * 默认派生主密钥种子（若环境变量未设置时使用保底种子，生产环境可通过 APP_TOOL_CIPHER_SECRET 覆盖）。
     */
    private static final byte[] MASTER_KEY_BYTES = initMasterKey();

    private CredentialCipher() {}

    private static byte[] initMasterKey() {
        try {
            String envKey = System.getenv("APP_TOOL_CIPHER_SECRET");
            String seed = (envKey != null && !envKey.isBlank()) ? envKey : "ai-copilot-custom-tool-gcm-secret-seed";
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            return md.digest(seed.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            byte[] fallback = new byte[32];
            for (int i = 0; i < 32; i++) fallback[i] = (byte) (i + 42);
            return fallback;
        }
    }

    /**
     * 加密明文敏感字符串。
     *
     * @param plainText 待加密明文（如 "sk-1234567890abcdef"）
     * @return Base64 编码的密文（包含 IV + CipherText + Tag），若输入为空则返回空
     */
    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return plainText;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);

            SecretKey secretKey = new SecretKeySpec(MASTER_KEY_BYTES, "AES");
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherBytes, 0, combined, iv.length, cipherBytes.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("敏感凭据加密异常: {}", e.getMessage(), e);
            throw new IllegalStateException("凭据加密失败", e);
        }
    }

    /**
     * 解密 Base64 密文字符串。
     *
     * @param cipherText Base64 密文
     * @return 解密后的原始明文字符串
     */
    public static String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) {
            return cipherText;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(cipherText);
            if (combined.length < GCM_IV_LENGTH) {
                return cipherText; // 非合法密文直接返回
            }

            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherBytes = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, cipherBytes, 0, cipherBytes.length);

            SecretKey secretKey = new SecretKeySpec(MASTER_KEY_BYTES, "AES");
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            byte[] plainBytes = cipher.doFinal(cipherBytes);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("敏感凭据解密失败或输入为明文: {}", e.getMessage());
            return cipherText; // 容错回退
        }
    }

    /**
     * 对敏感字符串进行脱敏掩码处理（下发给前端展示）。
     *
     * <p>例如:
     * <ul>
     *   <li>"sk-1234567890abcdef" -> "sk-12****cdef"</li>
     *   <li>"short" -> "s****t"</li>
     *   <li>空字符串 -> ""</li>
     * </ul>
     */
    public static String mask(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        if (raw.contains(MASK_PATTERN)) {
            return raw; // 已经是掩码
        }
        int len = raw.length();
        if (len <= 6) {
            return raw.charAt(0) + MASK_PATTERN + raw.charAt(len - 1);
        }
        String prefix = raw.substring(0, Math.min(5, len / 3));
        String suffix = raw.substring(len - Math.min(4, len / 3));
        return prefix + MASK_PATTERN + suffix;
    }

    /**
     * 判断传入字符串是否为已掩码形态。
     */
    public static boolean isMasked(String text) {
        return text != null && text.contains(MASK_PATTERN);
    }
}
