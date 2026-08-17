package xyz.ppmblszdp.ai.registry;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import xyz.ppmblszdp.ai.config.AiProviderProperties;

/**
 * 跨供应商任务级模型路由（5-#1）。
 *
 * <p>
 * 把原先散落在多个 service 的私有 {@code selectLowCostModel} 收敛为单一共享组件。
 * 配置驱动：从 {@code app.ai.routing.task-tiers} 读取每个任务的候选模型梯队，按降级
 * 优先级遍历候选，经 {@link ProviderRegistry} 校验注册、{@link ModelHealthTracker}
 * 校验非熔断后返回首个可用模型。
 *
 * <p>
 * 降级语义：路由关闭、任务无梯队、全部候选不可用或 embedding 失败时，一律回退到
 * 调用方传入的「用户指定模型」，保证降级链不阻断对话主链路。
 */
@Component
public class TaskModelRouter {

    private static final Logger log = LoggerFactory.getLogger(TaskModelRouter.class);

    private final ProviderRegistry providerRegistry;
    private final ModelHealthTracker healthTracker;
    private final AiProviderProperties properties;

    public TaskModelRouter(
            ProviderRegistry providerRegistry, ModelHealthTracker healthTracker, AiProviderProperties properties) {
        this.providerRegistry = providerRegistry;
        this.healthTracker = healthTracker;
        this.properties = properties;
    }

    /**
     * 解析某任务的可用模型。
     *
     * @param taskKey      任务键（与 {@code app.ai.routing.task-tiers[].key} 对齐）
     * @param userResolved 用户/请求指定的模型（作为终极降级回退）
     * @return 经注册与健康校验后的首个可用候选；全部不可用时回退 {@code userResolved}
     */
    public ResolvedModel resolve(@Nullable String taskKey, @Nullable ResolvedModel userResolved) {
        var routing = properties.resolveRouting();
        if (routing == null || !routing.isEnabled()) {
            return userResolved;
        }
        List<String> candidates = routing.resolveProvidersFor(taskKey);
        if (candidates.isEmpty()) {
            return userResolved;
        }

        for (String candidate : candidates) {
            ResolvedModel resolved = tryResolve(candidate);
            if (resolved != null) {
                return resolved;
            }
        }
        log.debug("任务 {} 的全部候选模型均不可用，回退用户指定模型", taskKey);
        return userResolved;
    }

    @Nullable
    private ResolvedModel tryResolve(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        int sep = candidate.indexOf('/');
        if (sep <= 0 || sep == candidate.length() - 1) {
            log.debug("候选格式非法（应为 providerId/modelId）：{}", candidate);
            return null;
        }
        String providerId = candidate.substring(0, sep).trim();
        String modelId = candidate.substring(sep + 1).trim();

        if (!providerRegistry.isRegistered(providerId)) {
            log.debug("候选供应商未注册，跳过：{}", providerId);
            return null;
        }
        HealthStatus status = healthTracker.getStatus(providerId, modelId);
        if (status == HealthStatus.DOWN) {
            log.debug("候选模型已熔断，跳过：{}", candidate);
            return null;
        }
        ResolvedModel resolved = providerRegistry.resolve(providerId, modelId);
        if (resolved == null) {
            log.debug("候选模型无法解析（可能未暴露），跳过：{}", candidate);
            return null;
        }
        return resolved;
    }
}
