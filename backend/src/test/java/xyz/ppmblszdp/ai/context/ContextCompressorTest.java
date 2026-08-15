package xyz.ppmblszdp.ai.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.ObjectProvider;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.config.AiProviderProperties.ContextCompressionConfig;
import xyz.ppmblszdp.ai.config.AiProviderProperties.ContextConfig;
import xyz.ppmblszdp.ai.dto.ChatMessageDto;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;

class ContextCompressorTest {

    private final TokenEstimator estimator = new JTokkitTokenEstimator();

    @Test
    void testCompressHistoryUnderProtectedTurnsNotCompressed() {
        AiProviderProperties props = new AiProviderProperties(
                null, null, null, null, null,
                new ContextConfig(null, null, null, null, new ContextCompressionConfig(true, null, null, "LIGHT", 3, 5000L)),
                null, null, null, null, null, null);

        @SuppressWarnings("unchecked")
        ObjectProvider<ProviderRegistry> registryProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatClient> clientProvider = mock(ObjectProvider.class);

        ContextCompressor compressor = new ContextCompressor(estimator, props, registryProvider, clientProvider);

        List<ChatMessageDto> shortHistory = List.of(
                ChatMessageDto.user("Hello"),
                ChatMessageDto.assistant("Hi there"),
                ChatMessageDto.user("How are you?"),
                ChatMessageDto.assistant("I am fine!"));

        ContextCompressor.CompressResult result = compressor.compress(shortHistory, 50, ContextCompressor.Level.LIGHT);
        assertNotNull(result);
        assertEquals(4, result.messages().size());
        // 元数据为 null 表示历史未超保护区，无需压缩
        assertEquals(null, result.metadata());
    }

    @Test
    void testCompressWithMockChatClientReturnsCompressedSummaryAndProtectsWorkingMemory() {
        AiProviderProperties props = new AiProviderProperties(
                null, null, null, null, null,
                new ContextConfig(null, null, null, null, new ContextCompressionConfig(true, null, null, "LIGHT", 2, 5000L)),
                null, null, null, null, null, null);

        ChatClient mockClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec mockSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec mockCallSpec = mock(ChatClient.CallResponseSpec.class);

        when(mockClient.prompt()).thenReturn(mockSpec);
        when(mockSpec.user(anyString())).thenReturn(mockSpec);
        when(mockSpec.call()).thenReturn(mockCallSpec);

        ChatResponse mockResponse = new ChatResponse(List.of(new Generation(new org.springframework.ai.chat.messages.AssistantMessage("用户与助手讨论了架构设计和数据库选型。"))));
        when(mockCallSpec.chatResponse()).thenReturn(mockResponse);

        @SuppressWarnings("unchecked")
        ObjectProvider<ProviderRegistry> registryProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatClient> clientProvider = mock(ObjectProvider.class);
        when(clientProvider.getIfAvailable()).thenReturn(mockClient);

        ContextCompressor compressor = new ContextCompressor(estimator, props, registryProvider, clientProvider);

        // 构造包含 5 轮（10条消息）的长历史
        List<ChatMessageDto> longHistory = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            longHistory.add(ChatMessageDto.user("User question round " + i + " with some detailed description"));
            longHistory.add(ChatMessageDto.assistant("Assistant detailed answer round " + i + " with code and explanation"));
        }

        // protectedTurns = 2 (最后 4 条消息保留)，前面 3 轮（6 条消息）被压缩
        ContextCompressor.CompressResult result = compressor.compress(longHistory, 1000, ContextCompressor.Level.LIGHT);

        assertNotNull(result);
        assertNotNull(result.metadata());
        assertFalse(result.metadata().fallback());
        assertEquals(3, result.metadata().compressedTurnCount());
        assertEquals(ContextCompressor.Level.LIGHT, result.metadata().level());

        // 结果应该为：1 条压缩摘要消息 + 4 条保护区消息 = 5 条消息
        assertEquals(5, result.messages().size());
        assertTrue(result.messages().get(0).content().startsWith("[COMPRESSED:3 turns]"));
        assertEquals("assistant", result.messages().get(0).role());

        // 验证保护区最后一条依然是 round 5 的消息
        assertEquals("User question round 4 with some detailed description", result.messages().get(1).content());
        assertEquals("Assistant detailed answer round 5 with code and explanation", result.messages().get(4).content());
    }

    @Test
    void testCompressFallbackToHardDeleteWhenClientFails() {
        AiProviderProperties props = new AiProviderProperties(
                null, null, null, null, null,
                new ContextConfig(null, null, null, null, new ContextCompressionConfig(true, null, null, "LIGHT", 2, 5000L)),
                null, null, null, null, null, null);

        @SuppressWarnings("unchecked")
        ObjectProvider<ProviderRegistry> registryProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatClient> clientProvider = mock(ObjectProvider.class);
        when(clientProvider.getIfAvailable()).thenReturn(null); // 无可用模型
        when(registryProvider.getIfAvailable()).thenReturn(null);

        ContextCompressor compressor = new ContextCompressor(estimator, props, registryProvider, clientProvider);

        List<ChatMessageDto> longHistory = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            longHistory.add(ChatMessageDto.user("User " + i));
            longHistory.add(ChatMessageDto.assistant("Assistant " + i));
        }

        ContextCompressor.CompressResult result = compressor.compress(longHistory, 1000, ContextCompressor.Level.LIGHT);

        assertNotNull(result);
        assertNotNull(result.metadata());
        assertTrue(result.metadata().fallback()); // 标记为降级
        // 仅保留 protectedTurns = 2 (4 条消息)
        assertEquals(4, result.messages().size());
        assertEquals("User 4", result.messages().get(0).content());
    }

    @Test
    void testContextAssemblerIntegratesWithCompressor() {
        AiProviderProperties props = new AiProviderProperties(
                null, null, null, null, "System prompt",
                new ContextConfig(100, 0.5, 500, null, new ContextCompressionConfig(true, null, null, "LIGHT", 2, 5000L)),
                null, null, null, null, null, null);

        ContextAssembler assembler = new ContextAssembler(props, estimator);

        ChatClient mockClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec mockSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec mockCallSpec = mock(ChatClient.CallResponseSpec.class);

        when(mockClient.prompt()).thenReturn(mockSpec);
        when(mockSpec.user(anyString())).thenReturn(mockSpec);
        when(mockSpec.call()).thenReturn(mockCallSpec);
        when(mockCallSpec.chatResponse()).thenReturn(new ChatResponse(List.of(new Generation(new org.springframework.ai.chat.messages.AssistantMessage("已压缩前面讨论")))));

        @SuppressWarnings("unchecked")
        ObjectProvider<ProviderRegistry> registryProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatClient> clientProvider = mock(ObjectProvider.class);
        when(clientProvider.getIfAvailable()).thenReturn(mockClient);

        ContextCompressor compressor = new ContextCompressor(estimator, props, registryProvider, clientProvider);

        List<ChatMessageDto> longHistory = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            longHistory.add(ChatMessageDto.user("Large user content for round " + i + " repeated text to consume tokens"));
            longHistory.add(ChatMessageDto.assistant("Large assistant reply for round " + i + " repeated text to consume tokens"));
        }

        // maxContextTokens = 200, historyRatio = 0.5, budget 很小，必然触发压缩
        AssembleResult result = assembler.assembleWithResult(
                "Current question", longHistory, null, null, 200, List.of(), compressor);

        assertNotNull(result);
        assertTrue(result.hasCompression());
        assertNotNull(result.compressionMetadata());
        assertFalse(result.messages().isEmpty());
    }
}
