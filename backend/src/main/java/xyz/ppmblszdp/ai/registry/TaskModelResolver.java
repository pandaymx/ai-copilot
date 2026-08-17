package xyz.ppmblszdp.ai.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 任务级模型选型（5-#2）。
 *
 * <p>
 * 把「命名任务键」解析为可用的 {@link ResolvedModel}，复用 5-#1 的 {@link TaskModelRouter}
 * 与共享的 {@code app.ai.routing.task-tiers} 配置。各后台服务把原先的
 * {@code providerRegistry.resolve(null, null)}（默认模型、成本高/质量错配）替换为
 * {@code taskModelResolver.resolve(TaskKey.XXX)}，实现按任务语义的显式选型与降级/升级。
 *
 * <p>
 * 降级语义：路由未启用、任务无梯队、所有候选不可用或注册表为空时，回退到
 * {@link ProviderRegistry#resolve(String, String)} 的默认供应商行为，保证任务不中断、
 * 不抛异常（与原 {@code resolve(null, null)} 调用处契约一致）。
 */
@Component
public class TaskModelResolver {

    private static final Logger log = LoggerFactory.getLogger(TaskModelResolver.class);

    private final TaskModelRouter taskModelRouter;
    private final ProviderRegistry providerRegistry;

    public TaskModelResolver(TaskModelRouter taskModelRouter, ProviderRegistry providerRegistry) {
        this.taskModelRouter = taskModelRouter;
        this.providerRegistry = providerRegistry;
    }

    /**
     * 解析某任务的可用模型。
     *
     * @param key 任务键（枚举名即 {@code task-tiers[].key}）
     * @return 经路由选定的模型；全部不可用时回退默认供应商模型（可能为 null，由调用方兜底）
     */
    public ResolvedModel resolve(TaskKey key) {
        ResolvedModel routed = taskModelRouter.resolve(key.tierKey(), null);
        if (routed != null) {
            return routed;
        }
        log.debug("任务 {} 未路由到可用模型，回退默认供应商", key);
        return providerRegistry.resolve(null, null);
    }
}
