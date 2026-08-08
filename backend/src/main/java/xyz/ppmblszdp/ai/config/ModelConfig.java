package xyz.ppmblszdp.ai.config;

import jakarta.annotation.Nullable;
import org.springframework.boot.context.properties.bind.Name;

import java.math.BigDecimal;
import java.util.List;

/**
 * 单个模型的配置项，承载「供应商 → 模型」1:N 关系中的 N 端。
 *
 * <p>{@code id} 是该模型在其所属供应商下的对外唯一标识（前端传入的 {@code model} 字段），
 * 而 {@code name} 是真正下发给厂商 API 的模型名。两者分离的意义在于：
 * 同一个厂商模型可以用更友好的 id 暴露给前端，也便于在不影响前端的前提下
 * 切换底层模型版本（例如把 {@code qwen-max} 指向 {@code qwen-max-2025-01-25}）。
 *
 * @param id              对外唯一标识；缺省时回落为 {@code name}
 * @param name            下发给厂商 API 的真实模型名；缺省时回落为 {@code id}
 * @param displayName     前端展示名；缺省时回落为 {@code id}
 * @param description     模型描述，用于前端模型选择器
 * @param badge           前端角标文案，例如「推理」「全能」
 * @param tags            能力标签，例如 {@code [vision, tools]}
 * @param maxContextTokens 上下文窗口大小，用于 Token 预算滑动窗口裁剪；
 *                        缺省时回落到全局 {@code app.ai.context.default-max-context-tokens}
 * @param enabled         是否启用；为 {@code false} 时不会被注册，也不会出现在模型列表中
 * @param defaultModel    是否为所属供应商的默认模型（YAML 中写作 {@code default: true}）
 * @param inputPricePerK  每千输入 Token 价格（单位：RMB 元）
 * @param outputPricePerK 每千输出 Token 价格（单位：RMB 元）
 */
public record ModelConfig(
		@Nullable String id,
		@Nullable String name,
		@Nullable String displayName,
		@Nullable String description,
		@Nullable String badge,
		@Nullable List<String> tags,
		@Nullable Integer maxContextTokens,
		@Nullable Boolean enabled,
		@Name("default") @Nullable Boolean defaultModel,
		@Name("input-price-per-k") @Nullable BigDecimal inputPricePerK,
		@Name("output-price-per-k") @Nullable BigDecimal outputPricePerK
) {

	/**
	 * 解析出对外唯一标识：优先 {@code id}，否则回落到 {@code name}。
	 */
	public String resolveId() {
		if (id != null && !id.isBlank()) {
			return id.trim();
		}
		if (name != null && !name.isBlank()) {
			return name.trim();
		}
		throw new IllegalArgumentException("模型配置的 id 与 name 不能同时为空");
	}

	/**
	 * 解析出下发给厂商的真实模型名：优先 {@code name}，否则回落到 {@code id}。
	 */
	public String resolveName() {
		if (name != null && !name.isBlank()) {
			return name.trim();
		}
		return resolveId();
	}

	/**
	 * 解析出前端展示名，缺省回落到对外标识。
	 */
	public String resolveDisplayName() {
		return (displayName != null && !displayName.isBlank()) ? displayName.trim() : resolveId();
	}

	/**
	 * 是否启用，缺省视为启用。
	 */
	public boolean isEnabled() {
		return enabled == null || enabled;
	}

	/**
	 * 是否为所属供应商的默认模型，缺省视为否。
	 */
	public boolean isDefaultModel() {
		return Boolean.TRUE.equals(defaultModel);
	}

	/**
	 * 解析上下文窗口大小，非正数或缺省时回落到传入的兜底值。
	 */
	public int resolveMaxContextTokens(int fallback) {
		return (maxContextTokens != null && maxContextTokens > 0) ? maxContextTokens : fallback;
	}

	/**
	 * 标签列表，永不为 {@code null}。
	 */
	public List<String> resolveTags() {
		return tags == null ? List.of() : List.copyOf(tags);
	}
}
