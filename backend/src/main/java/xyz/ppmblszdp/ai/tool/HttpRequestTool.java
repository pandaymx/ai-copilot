package xyz.ppmblszdp.ai.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * HTTP 请求工具：基于 Spring {@link RestClient} 发起 GET/POST，带连接/读取超时（30s）与响应大小上限（1MB），
 * 防止大响应体或挂死耗尽资源。
 */
@Component
public class HttpRequestTool {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final int MAX_RESPONSE_BYTES = 1_048_576; // 1MB

	private final RestClient restClient;

	public HttpRequestTool() {
		this.restClient = RestClient.builder()
				.requestFactory(requestFactoryWithTimeout())
				.build();
	}

	private static SimpleClientHttpRequestFactory requestFactoryWithTimeout() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		long ms = ToolEventEmitter.DEFAULT_TOOL_TIMEOUT.toMillis();
		factory.setConnectTimeout((int) ms);
		factory.setReadTimeout((int) ms);
		return factory;
	}

	@Tool(description = "发起 HTTP 请求获取外部数据：支持 GET/POST，返回状态码与响应体（上限 1MB）")
	public String httpRequest(
			@ToolParam(description = "HTTP 方法，仅允许 GET 或 POST") String method,
			@ToolParam(description = "完整 URL，须以 http:// 或 https:// 开头") String url,
			@ToolParam(description = "可选请求体（POST 时），可为空字符串") String body,
			ToolContext toolContext) {
		String argsJson = toJson(method, url, body);
		return ToolEventEmitter.from(toolContext).executeWithEvent("http_request", argsJson, toolContext, () -> {
			String m = (method == null ? "GET" : method.toUpperCase());
			if (!"GET".equals(m) && !"POST".equals(m)) {
				throw new IllegalArgumentException("仅支持 GET / POST 方法");
			}
			if (url == null || !url.matches("^https?://.*")) {
				throw new IllegalArgumentException("URL 须以 http:// 或 https:// 开头");
			}
			RestClient.RequestHeadersSpec<?> spec;
			if ("POST".equals(m) && body != null && !body.isBlank()) {
				spec = restClient.method(HttpMethod.valueOf(m)).uri(url)
						.contentType(MediaType.APPLICATION_JSON).body(body);
			} else {
				spec = restClient.method(HttpMethod.valueOf(m)).uri(url);
			}
			String raw = spec.accept(MediaType.ALL).retrieve().body(String.class);
			if (raw == null)
				raw = "";
			if (raw.length() > MAX_RESPONSE_BYTES) {
				raw = raw.substring(0, MAX_RESPONSE_BYTES) + "\n...[响应截断至 1MB]";
			}
			String bodyJson;
			try {
				bodyJson = MAPPER.writeValueAsString(raw);
			} catch (JsonProcessingException e) {
				bodyJson = "\"\"";
			}
			return "{\"status\":\"ok\",\"length\":" + raw.length() + ",\"body\":" + bodyJson + "}";
		});
	}

	private static String toJson(String method, String url, String body) {
		try {
			return MAPPER.writeValueAsString(Map.of(
					"method", method == null ? "GET" : method,
					"url", url == null ? "" : url,
					"body", body == null ? "" : body));
		} catch (Exception e) {
			return "{\"method\":\"GET\"}";
		}
	}
}
