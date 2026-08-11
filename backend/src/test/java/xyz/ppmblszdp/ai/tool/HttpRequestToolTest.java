package xyz.ppmblszdp.ai.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ToolContext;
import reactor.core.publisher.Sinks;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.dto.ChatChunkDto;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpRequestToolTest {

	private HttpRequestTool httpRequestTool;
	private ToolContext toolContext;
	private Sinks.Many<ChatChunkDto> sink;

	@BeforeEach
	void setUp() {
		httpRequestTool = new HttpRequestTool();

		AiProviderProperties props = mock(AiProviderProperties.class);
		AiProviderProperties.AgentConfig agentConfig = mock(AiProviderProperties.AgentConfig.class);
		when(props.resolveAgent()).thenReturn(agentConfig);
		when(agentConfig.resolveMaxToolCalls()).thenReturn(5);
		when(agentConfig.resolveTimeoutSeconds()).thenReturn(30);

		ToolEventEmitter emitter = new ToolEventEmitter(props);
		sink = emitter.newSink();

		Map<String, Object> ctxMap = new HashMap<>();
		ctxMap.put(ToolEventEmitter.CTX_EMITTER, emitter);
		ctxMap.put("eventSink", sink);
		toolContext = new ToolContext(ctxMap);
	}

	@Test
	@DisplayName("云元数据端点 169.254.169.254 应该被 SSRF 防护安全拦截")
	void shouldBlockMetadataEndpoint() {
		String result = httpRequestTool.httpRequest("GET", "http://169.254.169.254/latest/meta-data/", "", toolContext);
		assertThat(result).isEqualTo("{\"output\":\"工具执行失败\"}");

		// 校验 SSE 事件发出的错误帧
		ChatChunkDto resultChunk = sink.asFlux().skip(1).blockFirst();
		assertThat(resultChunk).isNotNull();
		assertThat(resultChunk.type()).isEqualTo("tool_result");
		assertThat(resultChunk.isError()).isTrue();
		assertThat(resultChunk.result()).contains("169.254.169.254");
	}

	@Test
	@DisplayName("回环地址 127.0.0.1 应该被 SSRF 防护安全拦截")
	void shouldBlockLoopbackAddress() {
		String result = httpRequestTool.httpRequest("GET", "http://127.0.0.1:8080/admin", "", toolContext);
		assertThat(result).isEqualTo("{\"output\":\"工具执行失败\"}");

		ChatChunkDto resultChunk = sink.asFlux().skip(1).blockFirst();
		assertThat(resultChunk).isNotNull();
		assertThat(resultChunk.isError()).isTrue();
		assertThat(resultChunk.result()).contains("回环地址");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"http://10.0.0.1/api",
			"http://192.168.1.1:3000",
			"https://172.16.0.1/metrics"
	})
	@DisplayName("私网 IP 地址（10.x, 192.168.x, 172.16.x）应该被 SSRF 防护拦截")
	void shouldBlockPrivateIpAddresses(String url) {
		String result = httpRequestTool.httpRequest("GET", url, "", toolContext);
		assertThat(result).isEqualTo("{\"output\":\"工具执行失败\"}");

		ChatChunkDto resultChunk = sink.asFlux().skip(1).blockFirst();
		assertThat(resultChunk).isNotNull();
		assertThat(resultChunk.isError()).isTrue();
		assertThat(resultChunk.result()).contains("内网地址");
	}

	@Test
	@DisplayName("非 HTTP/HTTPS 协议应该被校验拦截")
	void shouldBlockNonHttpProtocol() {
		String result = httpRequestTool.httpRequest("GET", "ftp://example.com/file", "", toolContext);
		assertThat(result).isEqualTo("{\"output\":\"工具执行失败\"}");

		ChatChunkDto resultChunk = sink.asFlux().skip(1).blockFirst();
		assertThat(resultChunk).isNotNull();
		assertThat(resultChunk.isError()).isTrue();
		assertThat(resultChunk.result()).contains("URL 须以 http:// 或 https:// 开头");
	}
}
