package xyz.ppmblszdp.ai.customtool.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CredentialCipherTest {

    @Test
    void testEncryptAndDecrypt() {
        String secret = "sk-ant-api03-abcdef1234567890-test";
        String cipher = CredentialCipher.encrypt(secret);

        assertNotNull(cipher);
        assertNotEquals(secret, cipher);

        String decrypted = CredentialCipher.decrypt(cipher);
        assertEquals(secret, decrypted);
    }

    @Test
    void testMasking() {
        String secret = "sk-1234567890abcdef";
        String masked = CredentialCipher.mask(secret);

        assertTrue(CredentialCipher.isMasked(masked));
        assertTrue(masked.contains("****"));

        // 幂等性：掩码后再掩码保持不变
        assertEquals(masked, CredentialCipher.mask(masked));
    }

    @Test
    void testEmptyAndNullHandling() {
        assertEquals("", CredentialCipher.mask(""));
        assertEquals("", CredentialCipher.mask(null));
        assertEquals(null, CredentialCipher.encrypt(null));
        assertEquals(null, CredentialCipher.decrypt(null));
        assertFalse(CredentialCipher.isMasked(null));
        assertFalse(CredentialCipher.isMasked("plain_key"));
    }
}
