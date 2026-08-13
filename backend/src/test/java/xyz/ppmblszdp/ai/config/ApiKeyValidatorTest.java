package xyz.ppmblszdp.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiKeyValidatorTest {

    @Test
    void isPlaceholder_DetectsYourPrefixAndPlaceholderPattern() {
        assertThat(ApiKeyValidator.isPlaceholder("your_deepseek_api_key_here")).isTrue();
        assertThat(ApiKeyValidator.isPlaceholder("YOUR_OPENAI_API_KEY_HERE")).isTrue();
        assertThat(ApiKeyValidator.isPlaceholder("your_custom_key")).isTrue();

        assertThat(ApiKeyValidator.isPlaceholder("sk-1234567890abcdef")).isFalse();
        assertThat(ApiKeyValidator.isPlaceholder(null)).isFalse();
        assertThat(ApiKeyValidator.isPlaceholder("")).isFalse();
    }

    @Test
    void isValid_RejectsBlankPlaceholderAndShortKeys() {
        assertThat(ApiKeyValidator.isValid("your_deepseek_api_key_here")).isFalse();
        assertThat(ApiKeyValidator.isValid(null)).isFalse();
        assertThat(ApiKeyValidator.isValid("")).isFalse();
        assertThat(ApiKeyValidator.isValid("short")).isFalse();

        assertThat(ApiKeyValidator.isValid("sk-proj-valid-api-key-12345")).isTrue();
    }
}
