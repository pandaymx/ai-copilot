package xyz.ppmblszdp.ai.customtool.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import xyz.ppmblszdp.ai.customtool.dto.CustomToolDto;
import xyz.ppmblszdp.ai.customtool.dto.CustomToolDto.HttpConfigDto;
import xyz.ppmblszdp.ai.customtool.dto.CustomToolDto.ToolTestRequest;
import xyz.ppmblszdp.ai.customtool.dto.CustomToolDto.ToolTestResponse;
import xyz.ppmblszdp.ai.customtool.repository.CustomToolRepository;
import xyz.ppmblszdp.ai.customtool.security.CredentialCipher;

/**
 * 用户自定义工具核心业务服务。
 *
 * <p>核心职责：
 * <ul>
 *   <li><b>轻量级内存缓存加速</b>：按 {@code userId} 缓存已编译的 {@link ToolCallback} 列表，消除对话期 DB 与 Schema 开销；</li>
 *   <li><b>主动失效（Evict）</b>：在任何增删改与状态切换操作时，精准失效对应用户的工具缓存；</li>
 *   <li><b>安全与冲突防御</b>：校验工具名格式合法性，拦截系统保留字黑名单，凭据落库加密与下发脱敏；</li>
 *   <li><b>在线调试支持</b>：提供独立的单工具即时试运行能力。</li>
 * </ul>
 */
@Service
public class CustomToolService {

    private static final Logger log = LoggerFactory.getLogger(CustomToolService.class);

    private static final Pattern TOOL_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");
    private static final long CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(30);

    /** 系统内置工具保留字黑名单，禁止用户自定义工具重名 */
    public static final Set<String> RESERVED_TOOL_NAMES = Set.of(
            "calculator",
            "code_execution",
            "code_sandbox",
            "http_request",
            "file_operation",
            "knowledge_query",
            "git_operation",
            "code_search",
            "code_review",
            "translation",
            "sub_agent_analysis",
            "sub_agent_code",
            "sub_agent_summary",
            "web_search",
            "custom_tool",
            "image_generation");

    private final CustomToolRepository repository;
    private final DynamicToolCallbackFactory callbackFactory;

    /** 内部轻量缓存条目 */
    private record CacheEntry(List<ToolCallback> tools, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    /** 基于 userId 的已编译 ToolCallback 实例缓存（30 分钟过期） */
    private final ConcurrentHashMap<String, CacheEntry> compiledToolCache = new ConcurrentHashMap<>();

    public CustomToolService(CustomToolRepository repository, DynamicToolCallbackFactory callbackFactory) {
        this.repository = repository;
        this.callbackFactory = callbackFactory;
    }

    // ====================== 1. 运行时 Agent 工具获取与缓存 ======================

    /**
     * 获取指定用户当前已启用的全部自定义工具回调列表（优先走本地内存缓存）。
     */
    public List<ToolCallback> getCompiledTools(String userId) {
        String uid = (userId != null && !userId.isBlank()) ? userId : "anonymous";
        CacheEntry entry = compiledToolCache.get(uid);
        if (entry != null && !entry.isExpired()) {
            return entry.tools();
        }

        List<CustomToolDto> tools = repository.findByUserIdAndEnabledTrue(uid);
        log.debug("从 DB 加载用户 [{}] 的自定义工具并编译缓存 (数量={})", uid, tools.size());
        List<ToolCallback> compiled =
                tools.stream().map(callbackFactory::createToolCallback).toList();

        compiledToolCache.put(uid, new CacheEntry(compiled, System.currentTimeMillis() + CACHE_TTL_MS));
        return compiled;
    }

    /**
     * 主动驱逐指定用户的工具编译缓存。
     */
    public void evictCache(String userId) {
        String uid = (userId != null && !userId.isBlank()) ? userId : "anonymous";
        compiledToolCache.remove(uid);
        log.debug("已驱逐用户 [{}] 的自定义工具缓存", uid);
    }

    // ====================== 2. CRUD 业务管理 ======================

    /**
     * 查询指定用户的所有工具列表（敏感字段脱敏）。
     */
    public List<CustomToolDto> listTools(String userId) {
        return repository.findByUserId(userId).stream()
                .map(CustomToolDto::withMaskedSecrets)
                .toList();
    }

    /**
     * 查询工具详情（敏感字段脱敏）。
     */
    public Optional<CustomToolDto> getTool(String id, String userId) {
        return repository.findByIdAndUserId(id, userId).map(CustomToolDto::withMaskedSecrets);
    }

    /**
     * 创建自定义工具。
     */
    public CustomToolDto createTool(CustomToolDto dto, String userId) {
        validateTool(dto, userId, null);

        String id = (dto.id() != null && !dto.id().isBlank())
                ? dto.id()
                : "tool-" + UUID.randomUUID().toString().substring(0, 8);

        // 加密敏感凭据
        CustomToolDto toSave = processCredentialsForSave(dto, id, userId, null);
        toSave = new CustomToolDto(
                id,
                toSave.name(),
                toSave.displayName() != null ? toSave.displayName() : toSave.name(),
                toSave.description(),
                toSave.type(),
                toSave.enabled() != null ? toSave.enabled() : true,
                toSave.parametersSchema(),
                toSave.httpConfig(),
                toSave.scriptConfig(),
                toSave.promptConfig(),
                System.currentTimeMillis(),
                System.currentTimeMillis());

        repository.save(toSave, userId);
        evictCache(userId);
        log.info("创建自定义工具成功: id={}, name={}, user={}", id, toSave.name(), userId);
        return toSave.withMaskedSecrets();
    }

    /**
     * 更新自定义工具。
     */
    public CustomToolDto updateTool(String id, CustomToolDto dto, String userId) {
        Optional<CustomToolDto> existingOpt = repository.findByIdAndUserId(id, userId);
        if (existingOpt.isEmpty()) {
            throw new IllegalArgumentException("自定义工具不存在或无权操作: id=" + id);
        }
        CustomToolDto existing = existingOpt.get();
        validateTool(dto, userId, id);

        // 处理敏感凭据（若前端回传的是原掩码，保留旧密文）
        CustomToolDto toSave = processCredentialsForSave(dto, id, userId, existing);
        toSave = new CustomToolDto(
                id,
                toSave.name(),
                toSave.displayName() != null ? toSave.displayName() : toSave.name(),
                toSave.description(),
                toSave.type(),
                dto.enabled() != null ? dto.enabled() : existing.enabled(),
                toSave.parametersSchema(),
                toSave.httpConfig(),
                toSave.scriptConfig(),
                toSave.promptConfig(),
                existing.createdAt(),
                System.currentTimeMillis());

        repository.save(toSave, userId);
        evictCache(userId);
        log.info("更新自定义工具成功: id={}, name={}, user={}", id, toSave.name(), userId);
        return toSave.withMaskedSecrets();
    }

    /**
     * 快速切换工具启用状态。
     */
    public boolean toggleTool(String id, String userId) {
        boolean success = repository.toggleEnabled(id, userId);
        if (success) {
            evictCache(userId);
        }
        return success;
    }

    /**
     * 删除指定工具。
     */
    public boolean deleteTool(String id, String userId) {
        boolean success = repository.deleteByIdAndUserId(id, userId);
        if (success) {
            evictCache(userId);
        }
        return success;
    }

    // ====================== 3. 在线试运行与调试 ======================

    /**
     * 执行单次工具测试运行。
     */
    public ToolTestResponse testTool(ToolTestRequest request, String userId) {
        if (request == null || request.tool() == null) {
            return ToolTestResponse.failure("缺少测试工具参数定义", 0L);
        }
        CustomToolDto tool = request.tool();
        long start = System.currentTimeMillis();

        try {
            CustomToolDto effectiveTool = tool;
            // 如果 tool 传入了已保存的 ID 且凭据为掩码，从 DB 加载真实密文进行测试
            if (tool.id() != null
                    && tool.httpConfig() != null
                    && CredentialCipher.isMasked(tool.httpConfig().authToken())) {
                Optional<CustomToolDto> savedOpt = repository.findByIdAndUserId(tool.id(), userId);
                if (savedOpt.isPresent() && savedOpt.get().httpConfig() != null) {
                    CustomToolDto saved = savedOpt.get();
                    effectiveTool = new CustomToolDto(
                            tool.id(),
                            tool.name(),
                            tool.displayName(),
                            tool.description(),
                            tool.type(),
                            tool.enabled(),
                            tool.parametersSchema(),
                            new HttpConfigDto(
                                    tool.httpConfig().url(),
                                    tool.httpConfig().method(),
                                    tool.httpConfig().headers(),
                                    tool.httpConfig().queryParams(),
                                    tool.httpConfig().bodyTemplate(),
                                    tool.httpConfig().authType(),
                                    tool.httpConfig().authHeader(),
                                    saved.httpConfig().authToken(), // 原始密文
                                    tool.httpConfig().timeoutSeconds()),
                            tool.scriptConfig(),
                            tool.promptConfig(),
                            tool.createdAt(),
                            tool.updatedAt());
                }
            }

            Map<String, Object> inputParams = request.inputParameters() != null ? request.inputParameters() : Map.of();

            String output = callbackFactory.executeDirect(effectiveTool, inputParams);
            long latencyMs = System.currentTimeMillis() - start;
            boolean isTruncated = output.contains("[输出过长已截断");

            return ToolTestResponse.success(output, latencyMs, isTruncated);
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - start;
            log.warn("自定义工具测试运行异常 (name={}): {}", tool.name(), e.getMessage());
            return ToolTestResponse.failure(e.getMessage(), latencyMs);
        }
    }

    // ====================== 4. 校验与凭据处理 ======================

    private void validateTool(CustomToolDto tool, String userId, String excludeId) {
        if (tool == null) {
            throw new IllegalArgumentException("自定义工具定义不能为空");
        }
        if (tool.name() == null || tool.name().isBlank()) {
            throw new IllegalArgumentException("工具函数名 (name) 不能为空");
        }
        String cleanName = tool.name().trim();
        if (!TOOL_NAME_PATTERN.matcher(cleanName).matches()) {
            throw new IllegalArgumentException("工具函数名必须由 1~64 位字母、数字、下划线或中划线组成");
        }
        if (RESERVED_TOOL_NAMES.contains(cleanName.toLowerCase())) {
            throw new IllegalArgumentException("工具名称 '" + cleanName + "' 为系统保留关键字，请使用其他名称");
        }
        if (repository.existsByNameAndUserId(cleanName, userId, excludeId)) {
            throw new IllegalArgumentException("工具名称 '" + cleanName + "' 已存在，不可重复");
        }
        if (tool.type() == null) {
            throw new IllegalArgumentException("必须指定工具类型 (HTTP / SCRIPT / PROMPT)");
        }
    }

    private CustomToolDto processCredentialsForSave(
            CustomToolDto dto, String id, String userId, CustomToolDto existing) {
        if (dto.httpConfig() == null) {
            return dto;
        }

        HttpConfigDto http = dto.httpConfig();
        String inputToken = http.authToken();

        // 1. 若回传的是掩码，且已有旧数据，则复用旧密文
        if (CredentialCipher.isMasked(inputToken) && existing != null && existing.httpConfig() != null) {
            HttpConfigDto updatedHttp = new HttpConfigDto(
                    http.url(),
                    http.method(),
                    http.headers(),
                    http.queryParams(),
                    http.bodyTemplate(),
                    http.authType(),
                    http.authHeader(),
                    existing.httpConfig().authToken(), // 保持已有密文
                    http.timeoutSeconds());
            return new CustomToolDto(
                    id,
                    dto.name(),
                    dto.displayName(),
                    dto.description(),
                    dto.type(),
                    dto.enabled(),
                    dto.parametersSchema(),
                    updatedHttp,
                    dto.scriptConfig(),
                    dto.promptConfig(),
                    dto.createdAt(),
                    dto.updatedAt());
        }

        // 2. 若输入了新的明文，进行加密
        if (inputToken != null && !inputToken.isBlank() && !CredentialCipher.isMasked(inputToken)) {
            HttpConfigDto encryptedHttp = http.withEncryptedToken();
            return new CustomToolDto(
                    id,
                    dto.name(),
                    dto.displayName(),
                    dto.description(),
                    dto.type(),
                    dto.enabled(),
                    dto.parametersSchema(),
                    encryptedHttp,
                    dto.scriptConfig(),
                    dto.promptConfig(),
                    dto.createdAt(),
                    dto.updatedAt());
        }

        return dto;
    }
}
