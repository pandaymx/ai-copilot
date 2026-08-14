package xyz.ppmblszdp.ai.clarification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

class ClarificationAdvisorTest {

    private ClarificationProperties properties;
    private ClarificationEngine engine;
    private ClarificationAdvisor advisor;

    @BeforeEach
    void setUp() {
        properties = new ClarificationProperties();
        properties.setEnabled(true);
        properties.setDefaultMode(ClarificationMode.SOFT);
        properties.setAgentMode(ClarificationMode.STRICT);
        engine = new ClarificationEngine(properties);
        advisor = new ClarificationAdvisor(engine, properties);
    }

    @Test
    @DisplayName("STRICT 模式 CallAdvisor - 模糊提问直接短路返回，不触发下游调用")
    void testStrictCallShortCircuit() {
        CallAdvisorChain chain = mock(CallAdvisorChain.class);

        Prompt prompt = new Prompt(List.of(new UserMessage("帮我写个脚本")));
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(prompt)
                .context(Map.of(ClarificationAdvisor.CTX_CLARIFICATION_MODE, ClarificationMode.STRICT))
                .build();

        ChatClientResponse response = advisor.adviseCall(request, chain);

        // 验证没有调用 downstream
        verify(chain, never()).nextCall(any());

        assertThat(response).isNotNull();
        assertThat(response.chatResponse()).isNotNull();
        String output = response.chatResponse().getResult().getOutput().getText();
        assertThat(output).contains("为了更准确地为您提供高质量解答");
        assertThat(output).contains(ClarificationAssessment.CLARIFICATION_MARKER);
    }

    @Test
    @DisplayName("STRICT 模式 StreamAdvisor - 模糊提问直接流式短路返回，不触发下游流")
    void testStrictStreamShortCircuit() {
        StreamAdvisorChain chain = mock(StreamAdvisorChain.class);

        Prompt prompt = new Prompt(List.of(new UserMessage("报错了怎么解决")));
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(prompt)
                .context(Map.of(ClarificationAdvisor.CTX_CLARIFICATION_MODE, ClarificationMode.STRICT))
                .build();

        Flux<ChatClientResponse> stream = advisor.adviseStream(request, chain);
        List<ChatClientResponse> responses = stream.collectList().block();

        verify(chain, never()).nextStream(any());
        assertThat(responses).hasSize(1);
        String output = responses.get(0).chatResponse().getResult().getOutput().getText();
        assertThat(output).contains("为了更准确地为您提供高质量解答");
        assertThat(output).contains("报错信息");
    }

    @Test
    @DisplayName("SOFT 模式 CallAdvisor - 模糊提问增强系统提示词并放行给下游")
    void testSoftCallAugmentation() {
        CallAdvisorChain chain = mock(CallAdvisorChain.class);

        ChatClientResponse mockResponse = new ChatClientResponse(
                new ChatResponse(List.of(new Generation(new AssistantMessage("这是一个初步回答")))), Map.of());
        when(chain.nextCall(any(ChatClientRequest.class))).thenReturn(mockResponse);

        Prompt prompt = new Prompt(List.of(new SystemMessage("你是助手"), new UserMessage("帮我写个脚本")));
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(prompt)
                .context(Map.of(ClarificationAdvisor.CTX_CLARIFICATION_MODE, ClarificationMode.SOFT))
                .build();

        ChatClientResponse response = advisor.adviseCall(request, chain);

        assertThat(response).isNotNull();
        // 验证 downstream 被调用，且请求中的系统消息被增强
        verify(chain).nextCall(org.mockito.ArgumentMatchers.argThat(req -> {
            List<Message> instructions = req.prompt().getInstructions();
            for (Message m : instructions) {
                if (m instanceof SystemMessage sm && sm.getText().contains("【主动澄清与追问指引】")) {
                    return true;
                }
            }
            return false;
        }));
    }

    @Test
    @DisplayName("清晰提问直接放行 - 不修改 Prompt")
    void testClearQuestionPassThrough() {
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        ChatClientResponse mockResponse = new ChatClientResponse(
                new ChatResponse(List.of(new Generation(new AssistantMessage("正常回答")))), Map.of());
        when(chain.nextCall(any(ChatClientRequest.class))).thenReturn(mockResponse);

        Prompt prompt = new Prompt(List.of(new UserMessage("请用 Python 3.11 写一个计算斐波那契数列的函数")));
        ChatClientRequest request = ChatClientRequest.builder().prompt(prompt).build();

        ChatClientResponse response = advisor.adviseCall(request, chain);

        assertThat(response).isEqualTo(mockResponse);
        verify(chain).nextCall(request);
    }
}
