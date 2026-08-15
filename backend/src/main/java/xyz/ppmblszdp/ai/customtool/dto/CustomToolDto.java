package xyz.ppmblszdp.ai.customtool.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import xyz.ppmblszdp.ai.customtool.model.CustomToolType;
import xyz.ppmblszdp.ai.customtool.security.CredentialCipher;

/**
 * 自定义工具数据传输对象（DTO）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CustomToolDto(
        String id,
        String name,
        String displayName,
        String description,
        CustomToolType type,
        Boolean enabled,
        String parametersSchema,
        HttpConfigDto httpConfig,
        ScriptConfigDto scriptConfig,
        PromptConfigDto promptConfig,
        Long createdAt,
        Long updatedAt) {

    /**
     * HTTP API 工具配置。
     *
     * @param url            API 请求完整 URL（支持 {{param}} 变量插值）
     * @param method         HTTP 方法（GET / POST / PUT / DELETE / PATCH）
     * @param headers        静态请求头 Map（支持 {{param}}）
     * @param queryParams    URL 查询参数 Map（自动 URL-encode）
     * @param bodyTemplate   JSON 请求体模板（仅在 POST/PUT/PATCH 时生效，支持 AST 结构化参数替换）
     * @param authType       认证类型（NONE / BEARER / API_KEY / BASIC）
     * @param authHeader     自定义 Auth Header 名称（如 X-API-Key）
     * @param authToken      认证密钥（落库加密，前端展示掩码）
     * @param timeoutSeconds 请求超时时间（秒，默认 30）
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HttpConfigDto(
            String url,
            String method,
            Map<String, String> headers,
            Map<String, String> queryParams,
            String bodyTemplate,
            String authType,
            String authHeader,
            String authToken,
            Integer timeoutSeconds) {

        public HttpConfigDto withMaskedToken() {
            if (authToken == null || authToken.isBlank()) {
                return this;
            }
            return new HttpConfigDto(
                    url,
                    method,
                    headers,
                    queryParams,
                    bodyTemplate,
                    authType,
                    authHeader,
                    CredentialCipher.mask(authToken),
                    timeoutSeconds);
        }

        public HttpConfigDto withEncryptedToken() {
            if (authToken == null || authToken.isBlank() || CredentialCipher.isMasked(authToken)) {
                return this;
            }
            return new HttpConfigDto(
                    url,
                    method,
                    headers,
                    queryParams,
                    bodyTemplate,
                    authType,
                    authHeader,
                    CredentialCipher.encrypt(authToken),
                    timeoutSeconds);
        }

        public HttpConfigDto withDecryptedToken() {
            if (authToken == null || authToken.isBlank()) {
                return this;
            }
            return new HttpConfigDto(
                    url,
                    method,
                    headers,
                    queryParams,
                    bodyTemplate,
                    authType,
                    authHeader,
                    CredentialCipher.decrypt(authToken),
                    timeoutSeconds);
        }
    }

    /**
     * Python / JavaScript 沙箱脚本工具配置。
     *
     * @param language   脚本语言（"python" | "javascript"）
     * @param scriptCode 用户编写的代码片段
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ScriptConfigDto(String language, String scriptCode) {}

    /**
     * Prompt 模板虚拟工具配置。
     *
     * @param systemPrompt   系统预置提示词
     * @param promptTemplate 用户提示词模板（包含 {{param}} 占位符）
     * @param targetProvider 目标供应商 ID（可选，空则使用默认）
     * @param targetModel    目标模型 ID（可选，空则使用默认）
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PromptConfigDto(
            String systemPrompt, String promptTemplate, String targetProvider, String targetModel) {}

    /**
     * 单个工具在线测试运行请求 DTO。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ToolTestRequest(CustomToolDto tool, Map<String, Object> inputParameters) {}

    /**
     * 工具测试运行响应 DTO。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ToolTestResponse(
            String status, String output, long executionTimeMs, boolean isTruncated, String errorMessage) {

        public static ToolTestResponse success(String output, long latencyMs, boolean isTruncated) {
            return new ToolTestResponse("SUCCESS", output, latencyMs, isTruncated, null);
        }

        public static ToolTestResponse failure(String errorMessage, long latencyMs) {
            return new ToolTestResponse("FAILURE", null, latencyMs, false, errorMessage);
        }
    }

    /**
     * 产生一份敏感凭据被脱敏后的副本，用于安全下发前端。
     */
    public CustomToolDto withMaskedSecrets() {
        HttpConfigDto maskedHttp = (httpConfig != null) ? httpConfig.withMaskedToken() : null;
        return new CustomToolDto(
                id,
                name,
                displayName,
                description,
                type,
                enabled,
                parametersSchema,
                maskedHttp,
                scriptConfig,
                promptConfig,
                createdAt,
                updatedAt);
    }
}
