package xyz.ppmblszdp.ai.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.ppmblszdp.ai.dto.ChatRequest;
import xyz.ppmblszdp.ai.dto.MediaDto;
import xyz.ppmblszdp.ai.intent.IntentResult;
import xyz.ppmblszdp.ai.intent.IntentType;

class IntentClassifierTest {

    private IntentClassifier intentClassifier;

    @BeforeEach
    void setUp() {
        intentClassifier = new IntentClassifier(null);
    }

    @Test
    void testSlashCommandsShortCircuit() {
        ChatRequest codeReq = new ChatRequest("/code 实现二分查找", null, null, null, null, null, null, null, null);
        IntentResult codeResult = intentClassifier.classify(codeReq, null);
        assertEquals(IntentType.CODE, codeResult.intent());
        assertEquals("代码", codeResult.label());
        assertTrue(codeResult.enableTools());

        ChatRequest transReq =
                new ChatRequest("/translate hello world", null, null, null, null, null, null, null, null);
        IntentResult transResult = intentClassifier.classify(transReq, null);
        assertEquals(IntentType.TRANSLATION, transResult.intent());

        ChatRequest mathReq = new ChatRequest("/math 1+1", null, null, null, null, null, null, null, null);
        IntentResult mathResult = intentClassifier.classify(mathReq, null);
        assertEquals(IntentType.MATH, mathResult.intent());

        ChatRequest searchReq = new ChatRequest("/search 最新新闻", null, null, null, null, null, null, null, null);
        IntentResult searchResult = intentClassifier.classify(searchReq, null);
        assertEquals(IntentType.SEARCH, searchResult.intent());
        assertTrue(searchResult.enableRag());
    }

    @Test
    void testMultimodalShortCircuit() {
        MediaDto media = new MediaDto(
                "image/png",
                "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");
        ChatRequest multiReq = new ChatRequest("请分析这张图", null, null, null, null, null, null, List.of(media), null);
        IntentResult result = intentClassifier.classify(multiReq, null);
        assertEquals(IntentType.MULTIMODAL, result.intent());
        assertEquals("多模态", result.label());
    }

    @Test
    void testPatternMatchCode() {
        ChatRequest req1 = new ChatRequest(
                "public class Main { public static void main(String[] args) {} }",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        assertEquals(IntentType.CODE, intentClassifier.classify(req1, null).intent());

        ChatRequest req2 = new ChatRequest("帮我写一段代码实现快速排序", null, null, null, null, null, null, null, null);
        assertEquals(IntentType.CODE, intentClassifier.classify(req2, null).intent());
    }

    @Test
    void testPatternMatchMath() {
        ChatRequest req = new ChatRequest("求解积分 \\int x^2 dx", null, null, null, null, null, null, null, null);
        IntentResult res = intentClassifier.classify(req, null);
        assertEquals(IntentType.MATH, res.intent());
        assertEquals("数学", res.label());
        assertTrue(res.enableTools());
    }

    @Test
    void testPatternMatchTranslation() {
        ChatRequest req = new ChatRequest("翻译：Good morning -> 中文", null, null, null, null, null, null, null, null);
        IntentResult res = intentClassifier.classify(req, null);
        assertEquals(IntentType.TRANSLATION, res.intent());
        assertEquals("翻译", res.label());
        assertFalse(res.enableRag());
    }

    @Test
    void testPatternMatchSearch() {
        ChatRequest req = new ChatRequest("搜索一下 2026年最新AI模型趋势", null, null, null, null, null, null, null, null);
        IntentResult res = intentClassifier.classify(req, null);
        assertEquals(IntentType.SEARCH, res.intent());
        assertTrue(res.enableRag());
    }

    @Test
    void testPatternMatchWriting() {
        ChatRequest req = new ChatRequest("帮我写一篇关于人工智能的周报总结", null, null, null, null, null, null, null, null);
        IntentResult res = intentClassifier.classify(req, null);
        assertEquals(IntentType.WRITING, res.intent());
    }

    @Test
    void testPatternMatchAnalysis() {
        ChatRequest req = new ChatRequest("对比 Postgres 与 Redis 的优缺点", null, null, null, null, null, null, null, null);
        IntentResult res = intentClassifier.classify(req, null);
        assertEquals(IntentType.ANALYSIS, res.intent());
    }

    @Test
    void testDefaultChatFallback() {
        ChatRequest req = new ChatRequest("你好，今天天气真不错啊", null, null, null, null, null, null, null, null);
        IntentResult res = intentClassifier.classify(req, null);
        assertEquals(IntentType.CHAT, res.intent());
        assertEquals("闲聊", res.label());
    }
}
