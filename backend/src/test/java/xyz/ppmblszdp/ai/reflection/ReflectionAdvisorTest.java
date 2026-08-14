package xyz.ppmblszdp.ai.reflection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class ReflectionAdvisorTest {

    private ReflectionEngine mockEngine;
    private ReflectionProperties properties;
    private ReflectionAdvisor advisor;

    @BeforeEach
    void setUp() {
        mockEngine = mock(ReflectionEngine.class);
        properties = new ReflectionProperties();
        properties.setEnabled(true);
        properties.setAutoCorrectionEnabled(true);
        properties.setMinContentLength(20);

        advisor = new ReflectionAdvisor(mockEngine, properties);
    }

    @Test
    @DisplayName("非流式回答判定需要纠错时，自动向回复追加结构化纠偏块")
    void testAdviseCallAutoCorrection() {
        String originalReply = "这是对 Java 25 虚拟线程的简短介绍，可以随意开辟数百万线程而无任何内存问题。";
        Prompt prompt = new Prompt(List.of(new UserMessage("请讲解虚拟线程并注意内存限制")));
        ChatClientRequest request = ChatClientRequest.builder().prompt(prompt).build();

        Generation gen = new Generation(new AssistantMessage(originalReply));
        ChatClientResponse mockResp = new ChatClientResponse(new ChatResponse(List.of(gen)), Map.of());

        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any())).thenReturn(mockResp);

        ReflectionAssessment needsCorrection = ReflectionAssessment.needsCorrection(
                0.7,
                0.8,
                List.of("未提及虚拟线程单栈占用与大量堆上对象的堆内存开销"),
                "应明确虚拟线程仍受 JVM 堆大小限制",
                "请注意：虽然虚拟线程轻量，但仍需合理配置 -Xmx 并避免无限堆积大量活跃阻塞任务。");
        when(mockEngine.evaluate(any(), any(), any())).thenReturn(needsCorrection);

        ChatClientResponse finalResp = advisor.adviseCall(request, chain);

        assertThat(finalResp).isNotNull();
        String outputText = finalResp.chatResponse().getResult().getOutput().getText();
        assertThat(outputText).contains("AI 自我纠错与补充");
        assertThat(outputText).contains("未提及虚拟线程单栈占用");
        assertThat(outputText).contains("JVM 堆大小限制");
    }

    @Test
    @DisplayName("非流式回答通过自检时，原样下发不增加任何额外内容")
    void testAdviseCallPassed() {
        String originalReply = "这是标准的 Spring Boot 4.1 初始化配置，无任何错误。";
        Prompt prompt = new Prompt(List.of(new UserMessage("如何初始化？")));
        ChatClientRequest request = ChatClientRequest.builder().prompt(prompt).build();

        Generation gen = new Generation(new AssistantMessage(originalReply));
        ChatClientResponse mockResp = new ChatClientResponse(new ChatResponse(List.of(gen)), Map.of());

        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any())).thenReturn(mockResp);

        when(mockEngine.evaluate(any(), any(), any())).thenReturn(ReflectionAssessment.ofPassed());

        ChatClientResponse finalResp = advisor.adviseCall(request, chain);

        assertThat(finalResp).isNotNull();
        String outputText = finalResp.chatResponse().getResult().getOutput().getText();
        assertThat(outputText).isEqualTo(originalReply);
        assertThat(outputText).doesNotContain("AI 自我纠错与补充");
    }
}
