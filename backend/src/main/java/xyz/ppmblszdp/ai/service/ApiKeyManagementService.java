package xyz.ppmblszdp.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import xyz.ppmblszdp.ai.config.ApiKeyValidator;
import xyz.ppmblszdp.ai.customtool.security.CredentialCipher;
import xyz.ppmblszdp.ai.dto.ApiKeyDto;
import xyz.ppmblszdp.ai.dto.ApiKeyTestResultDto;
import xyz.ppmblszdp.ai.repository.ApiKeyRepository;
import xyz.ppmblszdp.ai.repository.ApiKeyRepository.ApiKeyEntity;

/**
 * 运行时 API Key 管理服务：负责各模型供应商 API Key 的加密存储、脱敏查询、
 * 在线连通性测试与余额拉取。
 */
@Service
public class ApiKeyManagementService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyManagementService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(10);

    private final ApiKeyRepository repository;
    private final RestClient restClient;

    public ApiKeyManagementService(ApiKeyRepository repository) {
        this.repository = repository;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) TEST_TIMEOUT.toMillis());
        factory.setReadTimeout((int) TEST_TIMEOUT.toMillis());
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public List<ApiKeyDto> list(String userId) {
        return repository.findAllByUserId(userId).stream().map(this::toDto).toList();
    }

    public Optional<ApiKeyDto> getById(String id, String userId) {
        return repository.findById(id, userId).map(this::toDto);
    }

    public String save(String userId, String provider, String plainKey) {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider 不能为空");
        }
        String p = provider.trim().toLowerCase();

        Optional<ApiKeyEntity> existing = repository.findByUserAndProvider(userId, p);
        if (CredentialCipher.isMasked(plainKey) && existing.isPresent()) {
            return existing.get().id();
        }

        if (plainKey == null || plainKey.isBlank() || !ApiKeyValidator.isValid(plainKey)) {
            throw new IllegalArgumentException("无效的 API Key 格式");
        }

        String encrypted = CredentialCipher.encrypt(plainKey.trim());
        return repository.save(userId, p, encrypted);
    }

    public boolean delete(String id, String userId) {
        return repository.delete(id, userId);
    }

    public ApiKeyTestResultDto test(String id, String userId) {
        ApiKeyEntity entity =
                repository.findById(id, userId).orElseThrow(() -> new IllegalArgumentException("未找到对应的 API Key 记录"));

        String decryptedKey = CredentialCipher.decrypt(entity.encryptedKey());
        if (decryptedKey == null || decryptedKey.isBlank()) {
            repository.updateStatus(id, userId, "INVALID", null, "Key 解密失败");
            return new ApiKeyTestResultDto(false, "INVALID", "Key 解密失败", null);
        }

        String provider = entity.provider().toLowerCase();
        try {
            VerificationResult res = verifyProviderKey(provider, decryptedKey);
            String status = res.valid ? "ACTIVE" : "INVALID";
            repository.updateStatus(id, userId, status, res.balance, res.errorMessage);
            return new ApiKeyTestResultDto(
                    res.valid, status, res.errorMessage != null ? res.errorMessage : "连接验证成功", res.balance);
        } catch (Exception e) {
            log.warn("API Key 连通性测试异常 [provider={}]: {}", provider, e.getMessage());
            String errorMsg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            repository.updateStatus(id, userId, "INVALID", null, errorMsg);
            return new ApiKeyTestResultDto(false, "INVALID", "测试连接失败: " + errorMsg, null);
        }
    }

    /**
     * 获取指定用户、供应商的运行时明文 Key（解密后），供调用链路优先级覆盖。
     */
    public Optional<String> getDecryptedKey(String userId, String provider) {
        if (userId == null || provider == null) {
            return Optional.empty();
        }
        return repository
                .findByUserAndProvider(userId, provider)
                .map(ApiKeyEntity::encryptedKey)
                .map(CredentialCipher::decrypt)
                .filter(k -> k != null && !k.isBlank());
    }

    private record VerificationResult(boolean valid, String balance, String errorMessage) {}

    VerificationResult verifyProviderKey(String provider, String apiKey) {
        return switch (provider) {
            case "openai" -> verifyOpenAi(apiKey);
            case "deepseek" -> verifyDeepSeek(apiKey);
            case "anthropic" -> verifyAnthropic(apiKey);
            case "google" -> verifyGoogle(apiKey);
            case "qwen" -> verifyQwen(apiKey);
            default -> verifyGenericOpenAiCompatible(provider, apiKey);
        };
    }

    private VerificationResult verifyOpenAi(String apiKey) {
        try {
            var resp = restClient
                    .get()
                    .uri("https://api.openai.com/v1/models")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .retrieve()
                    .toBodilessEntity();
            if (resp.getStatusCode().is2xxSuccessful()) {
                return new VerificationResult(true, null, null);
            }
            return new VerificationResult(false, null, "OpenAI API 响应状态码: " + resp.getStatusCode());
        } catch (Exception e) {
            return new VerificationResult(false, null, "OpenAI 校验失败: " + e.getMessage());
        }
    }

    private VerificationResult verifyDeepSeek(String apiKey) {
        try {
            String balance = null;
            try {
                String balanceJson = restClient
                        .get()
                        .uri("https://api.deepseek.com/user/balance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .retrieve()
                        .body(String.class);
                if (balanceJson != null && !balanceJson.isBlank()) {
                    JsonNode node = MAPPER.readTree(balanceJson);
                    JsonNode info = node.path("balance_infos");
                    if (info.isArray() && !info.isEmpty()) {
                        String total = info.get(0).path("total_balance").asText("");
                        String curr = info.get(0).path("currency").asText("CNY");
                        if (!total.isBlank()) {
                            balance = (curr.equalsIgnoreCase("CNY") ? "¥" : "$") + total;
                        }
                    }
                }
            } catch (Exception ignored) {
                // 余额接口失败不影响基础可用性校验
            }

            var resp = restClient
                    .get()
                    .uri("https://api.deepseek.com/models")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .retrieve()
                    .toBodilessEntity();
            if (resp.getStatusCode().is2xxSuccessful()) {
                return new VerificationResult(true, balance, null);
            }
            return new VerificationResult(false, null, "DeepSeek API 响应状态码: " + resp.getStatusCode());
        } catch (Exception e) {
            return new VerificationResult(false, null, "DeepSeek 校验失败: " + e.getMessage());
        }
    }

    private VerificationResult verifyAnthropic(String apiKey) {
        try {
            var resp = restClient
                    .get()
                    .uri("https://api.anthropic.com/v1/models")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .retrieve()
                    .toBodilessEntity();
            if (resp.getStatusCode().is2xxSuccessful()) {
                return new VerificationResult(true, null, null);
            }
            return new VerificationResult(false, null, "Anthropic API 响应: " + resp.getStatusCode());
        } catch (Exception e) {
            return new VerificationResult(false, null, "Anthropic 校验失败: " + e.getMessage());
        }
    }

    private VerificationResult verifyGoogle(String apiKey) {
        try {
            var resp = restClient
                    .get()
                    .uri("https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey)
                    .retrieve()
                    .toBodilessEntity();
            if (resp.getStatusCode().is2xxSuccessful()) {
                return new VerificationResult(true, null, null);
            }
            return new VerificationResult(false, null, "Google Gemini API 响应: " + resp.getStatusCode());
        } catch (Exception e) {
            return new VerificationResult(false, null, "Google 校验失败: " + e.getMessage());
        }
    }

    private VerificationResult verifyQwen(String apiKey) {
        try {
            var resp = restClient
                    .get()
                    .uri("https://dashscope.aliyuncs.com/compatible-mode/v1/models")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .retrieve()
                    .toBodilessEntity();
            if (resp.getStatusCode().is2xxSuccessful()) {
                return new VerificationResult(true, null, null);
            }
            return new VerificationResult(false, null, "通义千问 API 响应: " + resp.getStatusCode());
        } catch (Exception e) {
            return new VerificationResult(false, null, "通义千问校验失败: " + e.getMessage());
        }
    }

    private VerificationResult verifyGenericOpenAiCompatible(String provider, String apiKey) {
        if (ApiKeyValidator.isValid(apiKey)) {
            return new VerificationResult(true, null, null);
        }
        return new VerificationResult(false, null, "非法的 Key 格式");
    }

    private ApiKeyDto toDto(ApiKeyEntity entity) {
        String plain = CredentialCipher.decrypt(entity.encryptedKey());
        String masked = CredentialCipher.mask(plain);
        return new ApiKeyDto(
                entity.id(),
                entity.userId(),
                entity.provider(),
                masked,
                entity.status(),
                entity.balance(),
                entity.errorMessage(),
                entity.createdAt(),
                entity.updatedAt());
    }
}
