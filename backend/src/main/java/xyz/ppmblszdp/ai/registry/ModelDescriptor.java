package xyz.ppmblszdp.ai.registry;

import xyz.ppmblszdp.ai.config.ModelConfig;

import java.math.BigDecimal;
import java.util.List;

/**
 * 模型描述符（不可变），承载「供应商 → 模型」1:N 关系中的 N 端。
 *
 * <p>由 {@link ModelConfig} 解析而来，对外暴露的字段对齐前端 {@code ModelOption} 所需的展示属性
 * （displayName / description / badge / tags），但不包含 icon / accent 这类视觉属性
 * （由前端自行映射，避免后端耦合 UI）。
 */
public final class ModelDescriptor {

	/** 默认兜底价格（每千 Token 价格，单位：RMB 元）。 */
	public static final BigDecimal DEFAULT_INPUT_PRICE = new BigDecimal("0.0015");
	public static final BigDecimal DEFAULT_OUTPUT_PRICE = new BigDecimal("0.0030");

	private final String id;
	private final String modelName;
	private final String displayName;
	private final String description;
	private final String badge;
	private final List<String> tags;
	private final int maxContextTokens;
	private final boolean isDefault;
	private final BigDecimal inputPricePerK;
	private final BigDecimal outputPricePerK;

	private ModelDescriptor(Builder b) {
		this.id = b.id;
		this.modelName = b.modelName;
		this.displayName = b.displayName;
		this.description = b.description;
		this.badge = b.badge;
		this.tags = List.copyOf(b.tags);
		this.maxContextTokens = b.maxContextTokens;
		this.isDefault = b.isDefault;
		this.inputPricePerK = b.inputPricePerK != null ? b.inputPricePerK : DEFAULT_INPUT_PRICE;
		this.outputPricePerK = b.outputPricePerK != null ? b.outputPricePerK : DEFAULT_OUTPUT_PRICE;
	}

	public static ModelDescriptor from(ModelConfig cfg, int fallbackMaxContextTokens) {
		return builder()
				.id(cfg.resolveId())
				.modelName(cfg.resolveName())
				.displayName(cfg.resolveDisplayName())
				.description(cfg.description())
				.badge(cfg.badge())
				.tags(cfg.resolveTags())
				.maxContextTokens(cfg.resolveMaxContextTokens(fallbackMaxContextTokens))
				.isDefault(cfg.isDefaultModel())
				.inputPricePerK(cfg.inputPricePerK())
				.outputPricePerK(cfg.outputPricePerK())
				.build();
	}

	public static Builder builder() {
		return new Builder();
	}

	public String id() {
		return id;
	}

	/** 真正下发给厂商 API 的模型名。 */
	public String modelName() {
		return modelName;
	}

	public String displayName() {
		return displayName;
	}

	public String description() {
		return description;
	}

	public String badge() {
		return badge;
	}

	public List<String> tags() {
		return tags;
	}

	public int maxContextTokens() {
		return maxContextTokens;
	}

	public boolean isDefault() {
		return isDefault;
	}

	public BigDecimal inputPricePerK() {
		return inputPricePerK;
	}

	public BigDecimal outputPricePerK() {
		return outputPricePerK;
	}

	public static final class Builder {
		private String id;
		private String modelName;
		private String displayName;
		private String description;
		private String badge;
		private List<String> tags = List.of();
		private int maxContextTokens;
		private boolean isDefault;
		private BigDecimal inputPricePerK = DEFAULT_INPUT_PRICE;
		private BigDecimal outputPricePerK = DEFAULT_OUTPUT_PRICE;

		public Builder id(String v) {
			this.id = v;
			return this;
		}

		public Builder modelName(String v) {
			this.modelName = v;
			return this;
		}

		public Builder displayName(String v) {
			this.displayName = v;
			return this;
		}

		public Builder description(String v) {
			this.description = v;
			return this;
		}

		public Builder badge(String v) {
			this.badge = v;
			return this;
		}

		public Builder tags(List<String> v) {
			this.tags = v;
			return this;
		}

		public Builder maxContextTokens(int v) {
			this.maxContextTokens = v;
			return this;
		}

		public Builder isDefault(boolean v) {
			this.isDefault = v;
			return this;
		}

		public Builder inputPricePerK(BigDecimal v) {
			if (v != null) {
				this.inputPricePerK = v;
			}
			return this;
		}

		public Builder outputPricePerK(BigDecimal v) {
			if (v != null) {
				this.outputPricePerK = v;
			}
			return this;
		}

		public ModelDescriptor build() {
			if (id == null || id.isBlank()) {
				throw new IllegalStateException("ModelDescriptor 缺少 id");
			}
			return new ModelDescriptor(this);
		}
	}
}
