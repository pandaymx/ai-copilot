package xyz.ppmblszdp.ai.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.ppmblszdp.ai.dto.ModelCatalogResponse;
import xyz.ppmblszdp.ai.registry.ModelDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型清单接口：{@code GET /api/models}。
 *
 * <p>返回 Provider → Models 的 1:N 结构，仅包含已成功注册（密钥有效且启用）的供应商与模型，
 * 供前端动态渲染模型选择器。
 */
@RestController
@RequestMapping("/api/models")
public class ModelCatalogController {

	private final ProviderRegistry registry;

	public ModelCatalogController(ProviderRegistry registry) {
		this.registry = registry;
	}

	@GetMapping
	public ModelCatalogResponse list() {
		List<ModelCatalogResponse.ProviderEntry> providers = new ArrayList<>();
		for (ProviderDescriptor pd : registry.providers().values()) {
			List<ModelCatalogResponse.ModelEntry> models = new ArrayList<>();
			for (ModelDescriptor md : pd.models().values()) {
				models.add(new ModelCatalogResponse.ModelEntry(
						md.id(),
						md.displayName(),
						md.description(),
						md.badge(),
						md.tags(),
						md.maxContextTokens()));
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
}
