package xyz.ppmblszdp.ai.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.ppmblszdp.ai.dto.ModelCatalogResponse;
import xyz.ppmblszdp.ai.registry.HealthStatus;
import xyz.ppmblszdp.ai.registry.ModelDescriptor;
import xyz.ppmblszdp.ai.registry.ModelHealthTracker;
import xyz.ppmblszdp.ai.registry.ProviderDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型清单接口：{@code GET /api/models} 与健康诊断 {@code GET /api/models/health}。
 */
@RestController
@RequestMapping("/api/models")
public class ModelCatalogController {

	private final ProviderRegistry registry;
	private final ModelHealthTracker healthTracker;

	public ModelCatalogController(ProviderRegistry registry, ModelHealthTracker healthTracker) {
		this.registry = registry;
		this.healthTracker = healthTracker;
	}

	@GetMapping
	public ModelCatalogResponse list() {
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
					pd.providerId(),
					pd.displayName(),
					pd.tier().name(),
					pd.protocol(),
					pd.defaultModelId(),
					models));
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
				healthMap.put(key, healthTracker.getStatus(pd.providerId(), md.id()).name());
			}
		}
		res.put("models", healthMap);
		return res;
	}
}
