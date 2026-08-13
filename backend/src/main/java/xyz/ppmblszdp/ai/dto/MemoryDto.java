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

	/** 记忆基础优先级权重 (0.1 ~ 2.0，默认 1.0) */
	private Double priority;

	/** 访问/命中累计次数 */
	private Integer accessCount;

	/** 最近访问/命中时间戳 */
	private String lastAccessedAt;

	/** 结合时间衰减与访问频次后的实时优先级评分 (0.0 ~ 2.0) */
	private Double priorityScore;

	/** 是否已归档 */
	private Boolean archived;

	/** 列表响应：总数（用于前端分页/徽标） */
	private Long total;

	public MemoryDto() {
	}

	public MemoryDto(String id, String content, String category, Double confidence, String updatedAt) {
		this(id, content, category, confidence, updatedAt, 1.0, 0, updatedAt, 1.0, false);
	}

	public MemoryDto(String id, String content, String category, Double confidence, String updatedAt,
			Double priority, Integer accessCount, String lastAccessedAt, Double priorityScore, Boolean archived) {
		this.id = id;
		this.content = content;
		this.category = category;
		this.confidence = confidence;
		this.updatedAt = updatedAt;
		this.priority = priority;
		this.accessCount = accessCount;
		this.lastAccessedAt = lastAccessedAt;
		this.priorityScore = priorityScore;
		this.archived = archived;
	}

	/** 编辑/状态更新请求体 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class UpdateRequest {
		private String content;
		private String category;
		private Double priority;
		private Boolean archived;

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

		public Double getPriority() {
			return priority;
		}

		public void setPriority(Double priority) {
			this.priority = priority;
		}

		public Boolean getArchived() {
			return archived;
		}

		public void setArchived(Boolean archived) {
			this.archived = archived;
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

	public Double getPriority() {
		return priority;
	}

	public void setPriority(Double priority) {
		this.priority = priority;
	}

	public Integer getAccessCount() {
		return accessCount;
	}

	public void setAccessCount(Integer accessCount) {
		this.accessCount = accessCount;
	}

	public String getLastAccessedAt() {
		return lastAccessedAt;
	}

	public void setLastAccessedAt(String lastAccessedAt) {
		this.lastAccessedAt = lastAccessedAt;
	}

	public Double getPriorityScore() {
		return priorityScore;
	}

	public void setPriorityScore(Double priorityScore) {
		this.priorityScore = priorityScore;
	}

	public Boolean getArchived() {
		return archived;
	}

	public void setArchived(Boolean archived) {
		this.archived = archived;
	}

	public Long getTotal() {
		return total;
	}

	public void setTotal(Long total) {
		this.total = total;
	}
}

