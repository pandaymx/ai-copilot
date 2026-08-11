package xyz.ppmblszdp.ai.memory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 长期记忆原子事实的强类型表示。
 * 供 Spring AI {@code BeanOutputConverter} 绑定 LLM 结构化输出，
 * 并以 {@code category}/{@code confidence} 形式持久化到 Document metadata，便于编辑与去重。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MemoryFact {

	/** 事实分类，例如：技术栈偏好 / 项目状态 / 关键决策 / 个人背景 */
	private String category;

	/** 原子化、无上下文依赖的独立陈述句 */
	private String content;

	/** LLM 抽取置信度，取值范围 0.0 ~ 1.0 */
	private Double confidence;

	public MemoryFact() {
	}

	public MemoryFact(String category, String content, Double confidence) {
		this.category = category;
		this.content = content;
		this.confidence = confidence;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public Double getConfidence() {
		return confidence;
	}

	public void setConfidence(Double confidence) {
		this.confidence = confidence;
	}
}
