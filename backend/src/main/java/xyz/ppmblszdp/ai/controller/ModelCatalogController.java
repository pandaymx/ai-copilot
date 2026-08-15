package xyz.ppmblszdp.ai.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import xyz.ppmblszdp.ai.dto.ModelCatalogResponse;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;
import xyz.ppmblszdp.ai.registry.HealthStatus;
import xyz.ppmblszdp.ai.registry.ModelDescriptor;
import xyz.ppmblszdp.ai.registry.ModelHealthTracker;
import xyz.ppmblszdp.ai.registry.ModelPerformanceTracker;
import xyz.ppmblszdp.ai.registry.ProviderDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;

/**
 * 模型清单接口：{@code GET /api/models}、健康诊断 {@code GET /api/models/health} 与流式性能指标 {@code GET /api/models/metrics}。
 *
 * <p>主清单接口需经身份解析（strict 缺 {@code X-User-Id} Header 抛 401）；
 * /health 与 /metrics 为诊断/监控端点，保持公开。
 */
@RestController
@RequestMapping("/api/models")
public class ModelCatalogController {

    private final ProviderRegistry registry;
    private final ModelHealthTracker healthTracker;
    private final ModelPerformanceTracker performanceTracker;
    private final AuthProperties authProperties;

    @org.springframework.beans.factory.annotation.Autowired
    public ModelCatalogController(
            ProviderRegistry registry,
            ModelHealthTracker healthTracker,
            ModelPerformanceTracker performanceTracker,
            AuthProperties authProperties) {
        this.registry = registry;
        this.healthTracker = healthTracker;
        this.performanceTracker = performanceTracker != null ? performanceTracker : new ModelPerformanceTracker();
        this.authProperties = authProperties;
    }

    public ModelCatalogController(
            ProviderRegistry registry, ModelHealthTracker healthTracker, AuthProperties authProperties) {
        this(registry, healthTracker, new ModelPerformanceTracker(), authProperties);
    }

    @GetMapping
    public ModelCatalogResponse list(ServerWebExchange exchange) {
        // strict 模式缺受信任 Header 抛 401；dev 模式匿名放行
        UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        List<ModelCatalogResponse.ProviderEntry> providers = new ArrayList<>();
        for (ProviderDescriptor pd : registry.providers().values()) {
            List<ModelCatalogResponse.ModelEntry> models = new ArrayList<>();
            for (ModelDescriptor md : pd.models().values()) {
                HealthStatus status = healthTracker.getStatus(pd.providerId(), md.id());
                models.add(new ModelCatalogResponse.ModelEntry(
                        md.id(),
                        md.displayName(),
                        md.description(),
                        md.badge(),
                        md.tags(),
                        md.maxContextTokens(),
                        md.inputPricePerK(),
                        md.outputPricePerK(),
                        status.name(),
                        status != HealthStatus.DOWN));
            }
            providers.add(new ModelCatalogResponse.ProviderEntry(
                    pd.providerId(), pd.displayName(), pd.tier().name(), pd.protocol(), pd.defaultModelId(), models));
        }
        return new ModelCatalogResponse(providers, registry.defaultProviderId(), registry.defaultModelId());
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> res = new HashMap<>();
        res.put("timestamp", System.currentTimeMillis());
        Map<String, String> healthMap = new HashMap<>();
        for (ProviderDescriptor pd : registry.providers().values()) {
            for (ModelDescriptor md : pd.models().values()) {
                String key = pd.providerId() + ":" + md.id();
                healthMap.put(
                        key, healthTracker.getStatus(pd.providerId(), md.id()).name());
            }
        }
        res.put("models", healthMap);
        return res;
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        Map<String, Object> res = new HashMap<>();
        res.put("timestamp", System.currentTimeMillis());
        List<ModelPerformanceTracker.ModelPerformanceSummaryDto> summaries = new ArrayList<>();
        for (ProviderDescriptor pd : registry.providers().values()) {
            for (ModelDescriptor md : pd.models().values()) {
                summaries.add(performanceTracker.getSummary(pd.providerId(), md.id()));
            }
        }
        res.put("models", summaries);
        res.put("defaultProvider", registry.defaultProviderId());
        res.put("defaultModel", registry.defaultModelId());
        return res;
    }
}
