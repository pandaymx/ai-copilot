package xyz.ppmblszdp.ai.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 长期记忆管理 DTO。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MemoryDto {

	/** 记忆唯一 ID（与 pgvector 表主键一致） */
	private String id;

	/** 记忆文本内容（原子化陈述句） */
	private String content;

	/** 事实分类，如：技术栈偏好 / 项目状态 / 关键决策 / 个人背景 / 其他 */
	private String category;

	/** 抽取置信度 0.0 ~ 1.0（可空，旧纯文本记录无此字段） */
	private Double confidence;

	/** 更新时间戳（ISO-8601 字符串） */
	private String updatedAt;

	/** 列表响应：总数（用于前端分页/徽标） */
	private Long total;

	public MemoryDto() {
	}

	public MemoryDto(String id, String content, String category, Double confidence, String updatedAt) {
		this.id = id;
		this.content = content;
		this.category = category;
		this.confidence = confidence;
		this.updatedAt = updatedAt;
	}

	/** 编辑请求体 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class UpdateRequest {
		private String content;
		private String category;

		public UpdateRequest() {
		}

		public String getContent() {
			return content;
		}

		public void setContent(String content) {
			this.content = content;
		}

		public String getCategory() {
			return category;
		}

		public void setCategory(String category) {
			this.category = category;
		}
	}

	/** 列表分页响应 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class ListResponse {
		private java.util.List<MemoryDto> items;
		private long total;

		public ListResponse() {
		}

		public ListResponse(List<MemoryDto> items, long total) {
			this.items = items;
			this.total = total;
		}

		public List<MemoryDto> getItems() {
			return items;
		}

		public void setItems(List<MemoryDto> items) {
			this.items = items;
		}

		public long getTotal() {
			return total;
		}

		public void setTotal(long total) {
			this.total = total;
		}
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public Double getConfidence() {
		return confidence;
	}

	public void setConfidence(Double confidence) {
		this.confidence = confidence;
	}

	public String getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(String updatedAt) {
		this.updatedAt = updatedAt;
	}

	public Long getTotal() {
		return total;
	}

	public void setTotal(Long total) {
		this.total = total;
	}
}
