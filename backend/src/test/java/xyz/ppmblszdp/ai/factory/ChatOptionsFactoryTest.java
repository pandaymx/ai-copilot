package xyz.ppmblszdp.ai.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import xyz.ppmblszdp.ai.registry.ModelDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderDescriptor;
import xyz.ppmblszdp.ai.registry.ResolvedModel;

class ChatOptionsFactoryTest {

    @Test
    void testDeepSeekOptions() {
        ChatOptions options = ChatOptionsFactory.forProvider("deepseek", "deepseek-chat", 0.2);
        assertNotNull(options);
        assertInstanceOf(DeepSeekChatOptions.class, options);
        assertEquals("deepseek-chat", options.getModel());
        assertEquals(0.2, options.getTemperature());
    }

    @Test
    void testOpenAiOptions() {
        ChatOptions options = ChatOptionsFactory.forProvider("openai", "gpt-4o", 0.5);
        assertNotNull(options);
        assertInstanceOf(OpenAiChatOptions.class, options);
        assertEquals("gpt-4o", options.getModel());
        assertEquals(0.5, options.getTemperature());
    }

    @Test
    void testGoogleGenAiOptions() {
        ChatOptions options = ChatOptionsFactory.forProvider("google-gemini", "gemini-1.5-pro", 0.3);
        assertNotNull(options);
        assertInstanceOf(GoogleGenAiChatOptions.class, options);
        assertEquals("gemini-1.5-pro", options.getModel());
        assertEquals(0.3, options.getTemperature());
    }

    @Test
    void testAnthropicOptions() {
        ChatOptions options = ChatOptionsFactory.forProvider("anthropic-claude", "claude-3-5-sonnet", 0.7);
        assertNotNull(options);
        assertInstanceOf(AnthropicChatOptions.class, options);
        assertEquals("claude-3-5-sonnet", options.getModel());
        assertEquals(0.7, options.getTemperature());
    }

    @Test
    void testOllamaOptions() {
        ChatOptions options = ChatOptionsFactory.forProvider("ollama-local", "llama3", 0.1);
        assertNotNull(options);
        assertInstanceOf(OllamaChatOptions.class, options);
        assertEquals("llama3", options.getModel());
        assertEquals(0.1, options.getTemperature());
    }

    @Test
    void testUnknownProviderFallbackToOpenAi() {
        ChatOptions options = ChatOptionsFactory.forProvider("custom-provider", "custom-model", 0.5);
        assertNotNull(options);
        assertInstanceOf(OpenAiChatOptions.class, options);
        assertEquals("custom-model", options.getModel());
        assertEquals(0.5, options.getTemperature());
    }

    @Test
    void testResolvedModelOverload() {
        ChatModel mockModel = mock(ChatModel.class);
        ProviderDescriptor provider = ProviderDescriptor.builder()
                .providerId("deepseek")
                .chatModel(mockModel)
                .build();
        ModelDescriptor model = ModelDescriptor.builder()
                .id("deepseek-coder")
                .modelName("deepseek-coder")
                .build();
        ResolvedModel resolved = new ResolvedModel(mockModel, provider, model);

        ChatOptions options = ChatOptionsFactory.forProvider(resolved, 0.2);
        assertNotNull(options);
        assertInstanceOf(DeepSeekChatOptions.class, options);
        assertEquals("deepseek-coder", options.getModel());
        assertEquals(0.2, options.getTemperature());
    }
}
