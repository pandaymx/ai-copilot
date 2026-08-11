package xyz.ppmblszdp.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import xyz.ppmblszdp.ai.dto.MemoryDto;
import xyz.ppmblszdp.ai.dto.MemoryDto.ListResponse;
import xyz.ppmblszdp.ai.memory.SafeVectorStore;

import org.postgresql.util.PGobject;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 长期记忆管理业务层。
 *
 * <p>
 * 列出走 {@code JdbcTemplate} 直查 pgvector 表（PgVectorStore 不支持按 metadata 全量列出）；
 * 编辑/删除走 {@link SafeVectorStore}（容错降级，不抛 5xx）。
 *
 * <p>
 * 关键约束：PostgreSQL 的 {@code metadata} 列类型为 jsonb，{@code JdbcTemplate} 通过
 * {@code ResultSet.getObject} 返回的是 {@link PGobject} 而非 String/Map，直接强转会抛
 * {@link ClassCastException}。此处统一用 {@link PGobject#getValue()} + Jackson 解析为
 * Map，异常时降级空 Map。
 */
@Service
public class MemoryService {

	private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

	/** 与项目其他工具类一致：本地维护一个 ObjectMapper，不依赖容器中是否注册了 Jackson bean */
	private static final ObjectMapper MAPPER = new ObjectMapper();

	/** 长期记忆 pgvector 表名（与 Spring AI autoconfigure 默认 VectorStore 指向一致） */
	private static final String MEMORY_TABLE = "ai_long_term_memory";

	private final JdbcTemplate jdbcTemplate;
	private final SafeVectorStore vectorStore;

	public MemoryService(JdbcTemplate jdbcTemplate, ObjectProvider<VectorStore> vectorStoreProvider) {
		this.jdbcTemplate = jdbcTemplate;
		// 与 LongTermMemoryConfig 一致：用 SafeVectorStore 容错包装底层 pgvector
		VectorStore vs = vectorStoreProvider.getIfAvailable();
		this.vectorStore = (vs != null) ? new SafeVectorStore(vs) : new SafeVectorStore(null);
	}

	/**
	 * 安全解析 jsonb metadata：PostgreSQL 驱动返回 PGobject，需经 Jackson 反序列化。
	 * 缺失或异常时返回空 Map（单条脏数据不拖垮整页列举）。
	 */
	private Map<String, Object> parseMetadata(Object metaObj) {
		if (metaObj == null) {
			return Collections.emptyMap();
		}
		if (metaObj instanceof PGobject pg) {
			try {
				String value = pg.getValue();
				if (value == null || value.isBlank()) {
					return Collections.emptyMap();
				}
				Map<String, Object> map = MAPPER.readValue(value, new TypeReference<Map<String, Object>>() {
				});
				return (map != null) ? map : Collections.emptyMap();
			} catch (Exception e) {
				log.warn("解析记忆 metadata(jsonb)失败，降级空 Map: {}", e.getMessage());
				return Collections.emptyMap();
			}
		}
		if (metaObj instanceof Map<?, ?> map) {
			@SuppressWarnings("unchecked")
			Map<String, Object> casted = (Map<String, Object>) map;
			return casted;
		}
		return Collections.emptyMap();
	}

	/**
	 * 列出用户全部长期记忆（按 updated_at 倒序，分页 + 关键字过滤）。
	 *
	 * @param userId  当前用户（严格来自身份解析，非请求体）
	 * @param keyword 可选关键字（模糊匹配 content）
	 * @param limit   分页大小
	 * @param offset  偏移量
	 */
	public ListResponse listMemories(String userId, String keyword, int limit, int offset) {
		StringBuilder where = new StringBuilder("WHERE metadata->>'userId' = ?");
		List<Object> args = new java.util.ArrayList<>();
		args.add(userId);
		if (keyword != null && !keyword.isBlank()) {
			where.append(" AND content ILIKE ?");
			args.add("%" + keyword.trim() + "%");
		}

		// 总数
		String countSql = "SELECT COUNT(*) FROM " + MEMORY_TABLE + " " + where;
		long total = jdbcTemplate.queryForObject(countSql, Long.class, args.toArray());

		// 分页列表：按 updated_at 文本倒序（ISO-8601 同格式可直接比较）
		String listSql = "SELECT id, content, metadata FROM " + MEMORY_TABLE + " " + where
				+ " ORDER BY (metadata->>'updated_at') DESC NULLS LAST LIMIT ? OFFSET ?";
		List<Object> listArgs = new java.util.ArrayList<>(args);
		listArgs.add(limit);
		listArgs.add(offset);

		RowMapper<MemoryDto> mapper = (rs, rowNum) -> {
			String id = rs.getString("id");
			String content = rs.getString("content");
			Map<String, Object> meta = parseMetadataInstance(rs.getObject("metadata"));
			String category = (meta.get("category") instanceof String c) ? c : null;
			Double confidence = (meta.get("confidence") instanceof Number n) ? n.doubleValue() : null;
			String updatedAt = (meta.get("updated_at") instanceof String u) ? u : null;
			return new MemoryDto(id, content, category, confidence, updatedAt);
		};

		List<MemoryDto> items = jdbcTemplate.query(listSql, mapper, listArgs.toArray());
		return new MemoryDto.ListResponse(items, total);
	}

	private Map<String, Object> parseMetadataInstance(Object metaObj) {
		return parseMetadata(metaObj);
	}

	/**
	 * 归属校验：查询指定 id 的记忆是否存在且属于该用户。
	 */
	public Optional<MemoryDto> findByIdAndUser(String id, String userId) {
		String sql = "SELECT id, content, metadata FROM " + MEMORY_TABLE + " WHERE id = ? AND metadata->>'userId' = ?";
		List<MemoryDto> list = jdbcTemplate.query(sql, (rs, rowNum) -> {
			String content = rs.getString("content");
			Map<String, Object> meta = parseMetadataInstance(rs.getObject("metadata"));
			String category = (meta.get("category") instanceof String c) ? c : null;
			Double confidence = (meta.get("confidence") instanceof Number n) ? n.doubleValue() : null;
			String updatedAt = (meta.get("updated_at") instanceof String u) ? u : null;
			return new MemoryDto(rs.getString("id"), content, category, confidence, updatedAt);
		}, id, userId);
		return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
	}

	/**
	 * 编辑记忆：先删旧（复用原 id）再写新 Document，确保主键不变、向量重算。
	 * 编辑实时生效——下次对话即读取新内容/分类。
	 *
	 * @return 编辑后的记忆视图；不存在或不属于用户返回空 Optional
	 */
	public Optional<MemoryDto> updateMemory(String id, String userId, String content, String category) {
		Optional<MemoryDto> existing = findByIdAndUser(id, userId);
		if (existing.isEmpty()) {
			return Optional.empty();
		}
		if (content == null || content.isBlank()) {
			return Optional.empty();
		}

		String newContent = content.trim();
		String newCategory = (category != null) ? category.trim() : existing.get().getCategory();

		try {
			// 修正点 3：复用原 id，避免主键变更导致前端列表 key 抖动
			vectorStore.delete(List.of(id));

			Map<String, Object> metadata = new HashMap<>();
			metadata.put("userId", userId);
			metadata.put("updated_at", Instant.now().toString());
			metadata.put("sourceType", "long_term_memory");
			if (newCategory != null && !newCategory.isBlank()) {
				metadata.put("category", newCategory);
			}
			Double oldConfidence = existing.get().getConfidence();
			if (oldConfidence != null) {
				metadata.put("confidence", oldConfidence);
			}

			Document updated = new Document(id, newContent, metadata);
			vectorStore.add(List.of(updated));
			log.info("长期记忆已编辑（复用 id）: id={}, userId={}, category={}", id, userId, newCategory);
		} catch (Exception e) {
			log.warn("编辑长期记忆失败（已降级）: {}", e.getMessage());
			return Optional.empty();
		}

		return Optional.of(new MemoryDto(id, newContent, newCategory, existing.get().getConfidence(),
				(String) existing.get().getUpdatedAt()));
	}

	/**
	 * 删除记忆：按 id 从 pgvector 移除，实时生效。
	 *
	 * @return true 表示删除成功（存在且属于用户）；false 表示不存在/不属于用户
	 */
	public boolean deleteMemory(String id, String userId) {
		// 归属校验：仅当用户拥有该记忆时才删除
		if (findByIdAndUser(id, userId).isEmpty()) {
			return false;
		}
		try {
			vectorStore.delete(List.of(id));
			log.info("长期记忆已删除: id={}, userId={}", id, userId);
			return true;
		} catch (Exception e) {
			log.warn("删除长期记忆失败（已降级）: {}", e.getMessage());
			return false;
		}
	}
}
