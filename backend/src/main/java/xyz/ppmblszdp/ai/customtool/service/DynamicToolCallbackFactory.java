package xyz.ppmblszdp.ai.customtool.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import xyz.ppmblszdp.ai.customtool.dto.CustomToolDto;
import xyz.ppmblszdp.ai.customtool.dto.CustomToolDto.HttpConfigDto;
import xyz.ppmblszdp.ai.customtool.dto.CustomToolDto.PromptConfigDto;
import xyz.ppmblszdp.ai.customtool.dto.CustomToolDto.ScriptConfigDto;
import xyz.ppmblszdp.ai.rag.security.SsrfGuard;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;
import xyz.ppmblszdp.ai.service.CodeExecutionService;
import xyz.ppmblszdp.ai.service.CodeExecutionService.ExecutionResponse;
import xyz.ppmblszdp.ai.tool.ToolEventEmitter;

/**
 * 动态自定义工具回调工厂：将 {@link CustomToolDto} 编译并转换为 Spring AI 的 {@link ToolCallback}。
 *
 * <p>核心安全与性能特性：
 * <ul>
 *   <li><b>防 JSON/脚本注入</b>：HTTP Body 采用 AST 结构化节点替换，Script 采用预置 JSON 结构注入；</li>
 *   <li><b>SSRF 防护</b>：HTTP 请求强制调用 {@link SsrfGuard#validate(String)}；</li>
 *   <li><b>输出截断防护</b>：硬性限制输出不超过 8KB，防止爆 Token 上下文；</li>
 *   <li><b>可观测事件</b>：无缝集成 {@link ToolEventEmitter}，流式下发工具执行过程。</li>
 * </ul>
 */
@Component
public class DynamicToolCallbackFactory {

    private static final Logger log = LoggerFactory.getLogger(DynamicToolCallbackFactory.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 结果输出最大字符数上限（8KB） */
    public static final int MAX_OUTPUT_CHARS = 8192;

    private static final Pattern PARAM_PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    private final CodeExecutionService codeExecutionService;
    private final ProviderRegistry providerRegistry;

    public DynamicToolCallbackFactory(CodeExecutionService codeExecutionService, ProviderRegistry providerRegistry) {
        this.codeExecutionService = codeExecutionService;
        this.providerRegistry = providerRegistry;
    }

    private static SimpleClientHttpRequestFactory createRequestFactory(int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int ms = Math.max(1000, timeoutSeconds * 1000);
        factory.setConnectTimeout(ms);
        factory.setReadTimeout(ms);
        return factory;
    }

    /**
     * 将 CustomToolDto 编译为可直接供 ChatClient 消费的 ToolCallback。
     */
    public ToolCallback createToolCallback(CustomToolDto tool) {
        String schema = tool.parametersSchema();
        if (schema == null || schema.isBlank()) {
            schema = "{\"type\":\"object\",\"properties\":{}}";
        }

        ToolDefinition definition = ToolDefinition.builder()
                .name(tool.name())
                .description(tool.description() != null ? tool.description() : tool.displayName())
                .inputSchema(schema)
                .build();

        return new DynamicCustomToolCallback(tool, definition);
    }

    /**
     * 内部动态 ToolCallback 实现类。
     */
    private final class DynamicCustomToolCallback implements ToolCallback {

        private final CustomToolDto tool;
        private final ToolDefinition definition;

        public DynamicCustomToolCallback(CustomToolDto tool, ToolDefinition definition) {
            this.tool = tool;
            this.definition = definition;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public String call(String toolInput) {
            return call(toolInput, null);
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            return ToolEventEmitter.from(toolContext).executeWithEvent(tool.name(), toolInput, toolContext, () -> {
                Map<String, Object> params = parseToolInput(toolInput);
                String rawResult = executeByToolType(tool, params);
                return truncateOutputIfNeeded(rawResult);
            });
        }
    }

    /**
     * 执行测试或直接调用工具的具体分发逻辑。
     */
    public String executeDirect(CustomToolDto tool, Map<String, Object> inputParams) {
        String raw = executeByToolType(tool, inputParams != null ? inputParams : Map.of());
        return truncateOutputIfNeeded(raw);
    }

    private String executeByToolType(CustomToolDto tool, Map<String, Object> params) {
        return switch (tool.type()) {
            case HTTP -> executeHttpTool(tool, params);
            case SCRIPT -> executeScriptTool(tool, params);
            case PROMPT -> executePromptTool(tool, params);
        };
    }

    // ====================== 1. HTTP 工具执行 ======================

    private String executeHttpTool(CustomToolDto tool, Map<String, Object> params) {
        HttpConfigDto config = tool.httpConfig();
        if (config == null || config.url() == null || config.url().isBlank()) {
            throw new IllegalArgumentException("HTTP 自定义工具缺少目标 URL 配置");
        }

        // 1. URL 变量插值与 Query 参数组装
        String rawUrl = interpolateString(config.url(), params, true);
        if (!rawUrl.matches("^https?://.*")) {
            throw new IllegalArgumentException("URL 必须以 http:// 或 https:// 开头");
        }

        StringBuilder urlBuilder = new StringBuilder(rawUrl);
        if (config.queryParams() != null && !config.queryParams().isEmpty()) {
            boolean hasQuery = rawUrl.contains("?");
            for (Map.Entry<String, String> entry : config.queryParams().entrySet()) {
                String k = entry.getKey();
                String v = interpolateString(entry.getValue(), params, true);
                urlBuilder
                        .append(hasQuery ? "&" : "?")
                        .append(URLEncoder.encode(k, StandardCharsets.UTF_8))
                        .append("=")
                        .append(URLEncoder.encode(v, StandardCharsets.UTF_8));
                hasQuery = true;
            }
        }
        String finalUrl = urlBuilder.toString();

        // 2. SSRF 安全校验
        SsrfGuard.validate(finalUrl);

        // 3. 构建 RestClient
        int timeoutSec =
                (config.timeoutSeconds() != null && config.timeoutSeconds() > 0) ? config.timeoutSeconds() : 30;
        RestClient client = RestClient.builder()
                .requestFactory(createRequestFactory(timeoutSec))
                .build();

        String method = (config.method() != null && !config.method().isBlank())
                ? config.method().toUpperCase()
                : "GET";
        RestClient.RequestBodyUriSpec uriSpec = client.method(HttpMethod.valueOf(method));
        RestClient.RequestHeadersSpec<?> headersSpec = uriSpec.uri(finalUrl);

        // 4. 请求头插值与注入
        if (config.headers() != null) {
            for (Map.Entry<String, String> entry : config.headers().entrySet()) {
                String val = interpolateString(entry.getValue(), params, false);
                headersSpec.header(entry.getKey(), val);
            }
        }

        // 5. 认证头处理（先解密 Token）
        HttpConfigDto decryptedConfig = config.withDecryptedToken();
        String authToken = decryptedConfig.authToken();
        if (authToken != null && !authToken.isBlank() && config.authType() != null) {
            switch (config.authType().toUpperCase()) {
                case "BEARER" -> headersSpec.header("Authorization", "Bearer " + authToken);
                case "API_KEY" -> {
                    String headerName =
                            (config.authHeader() != null && !config.authHeader().isBlank())
                                    ? config.authHeader()
                                    : "X-API-Key";
                    headersSpec.header(headerName, authToken);
                }
                case "BASIC" -> {
                    String encoded = Base64.getEncoder().encodeToString(authToken.getBytes(StandardCharsets.UTF_8));
                    headersSpec.header("Authorization", "Basic " + encoded);
                }
            }
        }

        // 6. 请求体（POST / PUT / PATCH）
        if (("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method))
                && config.bodyTemplate() != null
                && !config.bodyTemplate().isBlank()) {
            String processedBody = interpolateJsonBody(config.bodyTemplate(), params);
            headersSpec = ((RestClient.RequestBodySpec) headersSpec)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(processedBody);
        }

        try {
            String respBody = headersSpec.accept(MediaType.ALL).retrieve().body(String.class);
            if (respBody == null) respBody = "";
            return respBody;
        } catch (Exception e) {
            log.warn("HTTP 自定义工具 [{}] 执行失败 (url={}): {}", tool.name(), finalUrl, e.getMessage());
            return "{\"status\":\"error\",\"message\":" + toJsonString(e.getMessage()) + "}";
        }
    }

    /**
     * 采用 Jackson JsonNode 树形递归替换节点值，防止破坏 JSON 结构。
     */
    private String interpolateJsonBody(String bodyTemplate, Map<String, Object> params) {
        if (bodyTemplate == null || bodyTemplate.isBlank()) {
            return "{}";
        }
        try {
            JsonNode root = MAPPER.readTree(bodyTemplate);
            JsonNode replaced = replaceNodeValues(root, params);
            return MAPPER.writeValueAsString(replaced);
        } catch (Exception e) {
            // 如果不是有效 JSON，退化为安全占位替换
            return interpolateString(bodyTemplate, params, false);
        }
    }

    private JsonNode replaceNodeValues(JsonNode node, Map<String, Object> params) {
        if (node.isTextual()) {
            String text = node.asText();
            // 如果精确匹配 "{{key}}" 且 params 中是复杂类型，直接替换为对应 JsonNode
            Matcher m = Pattern.compile("^\\{\\{(\\w+)\\}\\}$").matcher(text);
            if (m.matches()) {
                String key = m.group(1);
                if (params.containsKey(key)) {
                    return MAPPER.valueToTree(params.get(key));
                }
            }
            // 否则执行字符串插值
            String replaced = interpolateString(text, params, false);
            return new TextNode(replaced);
        } else if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = obj.fields();
            Map<String, JsonNode> updates = new HashMap<>();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                updates.put(field.getKey(), replaceNodeValues(field.getValue(), params));
            }
            updates.forEach(obj::set);
            return obj;
        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                arr.set(i, replaceNodeValues(arr.get(i), params));
            }
            return arr;
        }
        return node;
    }

    // ====================== 2. Script 工具执行 ======================

    private String executeScriptTool(CustomToolDto tool, Map<String, Object> params) {
        ScriptConfigDto config = tool.scriptConfig();
        if (config == null || config.scriptCode() == null || config.scriptCode().isBlank()) {
            throw new IllegalArgumentException("Script 自定义工具缺少源代码");
        }

        String lang = config.language() != null ? config.language().toLowerCase() : "python";
        String paramsJson;
        try {
            paramsJson = MAPPER.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            paramsJson = "{}";
        }

        // 构造安全注入头（严禁拼接用户代码）
        String wrappedScript = wrapScriptWithParams(lang, config.scriptCode(), paramsJson);

        ExecutionResponse response = codeExecutionService.execute(lang, wrappedScript);
        if ("error".equalsIgnoreCase(response.status()) || response.exitCode() != 0) {
            String err = (response.stderr() != null && !response.stderr().isBlank())
                    ? response.stderr()
                    : "执行失败 (exit=" + response.exitCode() + ")";
            return "{\"status\":\"error\",\"stderr\":" + toJsonString(err) + ",\"stdout\":"
                    + toJsonString(response.stdout()) + "}";
        }

        String stdout = response.stdout() != null ? response.stdout().trim() : "";
        if (stdout.isBlank()) {
            return "{\"status\":\"success\",\"output\":\"[脚本执行成功，无标准输出]\"}";
        }
        return stdout;
    }

    private String wrapScriptWithParams(String lang, String userCode, String paramsJson) {
        if ("javascript".equals(lang) || "nodejs".equals(lang) || "js".equals(lang)) {
            // JS 注入
            String escapedJson = toJsonString(paramsJson);
            return "const params = JSON.parse(" + escapedJson + ");\n" + userCode;
        } else {
            // Python 注入
            String escapedJson = toJsonString(paramsJson);
            return "import json\nparams = json.loads(" + escapedJson + ")\n" + userCode;
        }
    }

    // ====================== 3. Prompt 工具执行 ======================

    private String executePromptTool(CustomToolDto tool, Map<String, Object> params) {
        PromptConfigDto config = tool.promptConfig();
        if (config == null
                || config.promptTemplate() == null
                || config.promptTemplate().isBlank()) {
            throw new IllegalArgumentException("Prompt 自定义工具缺少 Prompt 模板配置");
        }

        ResolvedModel resolved = providerRegistry.resolve(config.targetProvider(), config.targetModel());
        ChatClient chatClient = resolved.chatClient();

        String userPrompt = interpolateString(config.promptTemplate(), params, false);
        String systemPrompt =
                (config.systemPrompt() != null && !config.systemPrompt().isBlank())
                        ? interpolateString(config.systemPrompt(), params, false)
                        : "你是一个专业的轻量级工具执行助手，请直接输出严谨的结果。";

        try {
            return chatClient
                    .prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("Prompt 自定义工具 [{}] 执行异常: {}", tool.name(), e.getMessage());
            return "{\"status\":\"error\",\"message\":" + toJsonString(e.getMessage()) + "}";
        }
    }

    // ====================== 辅助工具方法 ======================

    private Map<String, Object> parseToolInput(String toolInput) {
        if (toolInput == null || toolInput.isBlank()) {
            return new HashMap<>();
        }
        try {
            return MAPPER.readValue(toolInput, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("解析 toolInput 为 Map 失败，按空参处理: {}", toolInput);
            return new HashMap<>();
        }
    }

    /**
     * 字符串占位符插值（支持 {{param}}）。
     *
     * @param template    原始模板
     * @param params      参数字典
     * @param urlEncode   是否对值执行 UTF-8 URL 编码
     */
    public static String interpolateString(String template, Map<String, Object> params, boolean urlEncode) {
        if (template == null || template.isBlank() || params == null || params.isEmpty()) {
            return template != null ? template : "";
        }
        Matcher matcher = PARAM_PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object val = params.get(key);
            String replacement = val != null ? String.valueOf(val) : "";
            if (urlEncode) {
                replacement = URLEncoder.encode(replacement, StandardCharsets.UTF_8);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 结果长度截断保护（防止击穿 LLM 上下文）。
     */
    private String truncateOutputIfNeeded(String output) {
        if (output == null) {
            return "";
        }
        if (output.length() > MAX_OUTPUT_CHARS) {
            return output.substring(0, MAX_OUTPUT_CHARS) + "\n\n...[输出过长已截断，保留前 8KB 字符]";
        }
        return output;
    }

    private static String toJsonString(String str) {
        try {
            return MAPPER.writeValueAsString(str != null ? str : "");
        } catch (Exception e) {
            return "\"\"";
        }
    }
}
