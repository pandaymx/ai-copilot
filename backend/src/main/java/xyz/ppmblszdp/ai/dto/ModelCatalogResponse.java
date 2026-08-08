package xyz.ppmblszdp.ai.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@code GET /api/models} 的响应：承载 Provider → Models 的 1:N 嵌套结构。
 * 字段命名对齐前端 ModelOption 所需展示属性（不含 icon/accent 视觉字段）。
 */
public record ModelCatalogResponse(
		List<ProviderEntry> providers,
		String defaultProvider,
		String defaultModel
) {

	public record ProviderEntry(
			String id,
			String displayName,
			String tier,
			String protocol,
			String defaultModelId,
			List<ModelEntry> models
	) {
	}

	public record ModelEntry(
			String id,
			String displayName,
			String description,
			String badge,
			List<String> tags,
			int maxContextTokens,
			BigDecimal inputPricePerK,
			BigDecimal outputPricePerK
	) {
	}
}
