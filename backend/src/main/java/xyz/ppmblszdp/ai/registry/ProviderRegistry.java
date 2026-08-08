package xyz.ppmblszdp.ai.registry;

import xyz.ppmblszdp.ai.exception.ModelNotFoundException;
import xyz.ppmblszdp.ai.exception.ProviderNotFoundException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 核心注册表：启动期由两个 Registrar 填充，填充完成后转为不可变 Map（{@code Map.copyOf}），
 * 运行期为只读、无锁并发安全的 O(1) 哈希查找。
 *
 * <p>路由规则：
 * <ul>
 *   <li>provider 与 model 都给出 → 直接定位；</li>
 *   <li>provider 为空 → 回落全局默认 provider（由构造时传入）；</li>
 *   <li>model 为空 → 取该 provider 的默认模型；</li>
 *   <li>provider 为空但 model 非空 → 反查该 model 唯一归属的 provider，若多个 provider 都有同 id 则报错。</li>
 * </ul>
 */
public final class ProviderRegistry {

	private final Map<String, ProviderDescriptor> providers;
	private final String defaultProviderId;
	private final String defaultModelId;

	private ProviderRegistry(Builder b) {
		this.providers = Map.copyOf(b.providers);
		this.defaultProviderId = b.defaultProviderId;
		this.defaultModelId = b.defaultModelId;
	}

	public static Builder builder() {
		return new Builder();
	}

	/** 已注册的供应商 id 列表（用于错误信息与排障）。 */
	public List<String> availableProviderIds() {
		return new ArrayList<>(providers.keySet());
	}

	/**
	 * 路由解析：返回调用所需全部信息。
	 *
	 * @param providerId 可为 null（回落默认 provider）
	 * @param modelId    可为 null（回落该 provider 默认模型）
	 */
	public ResolvedModel resolve(String providerId, String modelId) {
		String pid = (providerId == null || providerId.isBlank()) ? defaultProviderId : providerId.trim();
		ProviderDescriptor provider = providers.get(pid);
		if (provider == null) {
			throw new ProviderNotFoundException(pid, false, availableProviderIds());
		}

		if (modelId == null || modelId.isBlank()) {
			String dm = (provider.defaultModelId() != null) ? provider.defaultModelId() : defaultModelId;
			modelId = dm;
		}

		if (modelId != null && !modelId.isBlank()) {
			ModelDescriptor model = provider.models().get(modelId.trim());
			if (model == null) {
				// 支持自定义模型：provider 已注册但 model 不在预设清单中时，
				// 以传入的 modelId 作为下发给厂商 API 的模型名，便于用户自由指定任意模型。
				model = ModelDescriptor.builder()
						.id(modelId.trim())
						.modelName(modelId.trim())
						.displayName(modelId.trim())
						.description("自定义模型")
						.isDefault(false)
						.build();
			}
			return new ResolvedModel(provider.chatModel(), provider, model);
		}

		// provider 为空、model 非空 → 反查唯一归属
		if ((providerId == null || providerId.isBlank()) && modelId != null && !modelId.isBlank()) {
			List<ProviderDescriptor> owners = new ArrayList<>();
			for (ProviderDescriptor p : providers.values()) {
				if (p.models().containsKey(modelId.trim())) {
					owners.add(p);
				}
			}
			if (owners.isEmpty()) {
				throw new ModelNotFoundException(null, modelId, Collections.emptyList());
			}
			if (owners.size() > 1) {
				List<String> ownerIds = owners.stream().map(p -> p.providerId()).toList();
				throw new ProviderNotFoundException(modelId, true, ownerIds);
			}
			ProviderDescriptor owner = owners.get(0);
			return new ResolvedModel(owner.chatModel(), owner, owner.models().get(modelId.trim()));
		}

		throw new ModelNotFoundException(providerId, modelId, new ArrayList<>(provider.models().keySet()));
	}

	/**
	 * 解析降级备用模型。
	 *
	 * @param currentProviderId 当前使用的供应商 ID（降级供应商必须与当前供应商不同）
	 * @param fallbackProviderId 明确配置的降级供应商 ID（可选）
	 * @param fallbackModelId 明确配置的降级模型 ID（可选）
	 * @return 备用 ResolvedModel，若没有可用的不同备用供应商则返回 null
	 */
	public ResolvedModel resolveFallback(String currentProviderId, String fallbackProviderId, String fallbackModelId) {
		String primaryPid = (currentProviderId == null || currentProviderId.isBlank()) ? defaultProviderId : currentProviderId.trim();

		if (fallbackProviderId != null && !fallbackProviderId.isBlank() && !fallbackProviderId.trim().equalsIgnoreCase(primaryPid)) {
			try {
				return resolve(fallbackProviderId.trim(), fallbackModelId);
			} catch (Exception ex) {
				// 忽略无效降级配置
			}
		}

		if (defaultProviderId != null && !defaultProviderId.equalsIgnoreCase(primaryPid) && providers.containsKey(defaultProviderId)) {
			try {
				return resolve(defaultProviderId, fallbackModelId != null ? fallbackModelId : defaultModelId);
			} catch (Exception ex) {
				// 忽略
			}
		}

		return null;
	}

	/**
	 * 返回 Provider → Models 的 1:N 结构，供 {@code GET /api/models} 下发。
	 * 仅包含已成功注册的供应商与模型。
	 */
	public Map<String, ProviderDescriptor> providers() {
		return providers;
	}

	public String defaultProviderId() {
		return defaultProviderId;
	}

	public String defaultModelId() {
		return defaultModelId;
	}

	public static final class Builder {
		private final Map<String, ProviderDescriptor> providers = new LinkedHashMap<>();
		private String defaultProviderId;
		private String defaultModelId;

		public Builder register(ProviderDescriptor descriptor) {
			providers.put(descriptor.providerId(), descriptor);
			return this;
		}

		public Builder defaultProviderId(String v) {
			this.defaultProviderId = v;
			return this;
		}

		public Builder defaultModelId(String v) {
			this.defaultModelId = v;
			return this;
		}

		public ProviderRegistry build() {
			if (defaultProviderId == null || defaultProviderId.isBlank()) {
				if (!providers.isEmpty()) {
					defaultProviderId = providers.keySet().iterator().next();
				}
			}
			return new ProviderRegistry(this);
		}
	}
}
