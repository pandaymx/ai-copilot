package xyz.ppmblszdp.ai.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import xyz.ppmblszdp.ai.memory.SafeVectorStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库查询工具：复用现有 RAG 向量库（{@code ragVectorStore}），按 userId 隔离检索当前用户自己的文档。
 *
 * <p>
 * 过滤逻辑与 {@code RagAdvisorConfig} 对齐：一律以 String 比较构造 metadata 过滤表达式，
 * 确保与文档写入时的 String 类型一致，避免 PgVector JSONB 过滤失效。当 RAG 未装配（无向量库）时优雅降级。
 */
@Component
public class KnowledgeQueryTool {

	private static final Logger log = LoggerFactory.getLogger(KnowledgeQueryTool.class);
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final int TOP_K = 5;

	private final VectorStore vectorStore;

	public KnowledgeQueryTool(@Qualifier("ragVectorStore") ObjectProvider<VectorStore> ragVectorStore) {
		VectorStore vs = ragVectorStore.getIfAvailable();
		this.vectorStore = (vs instanceof SafeVectorStore) ? vs : new SafeVectorStore(vs);
	}

	@Tool(description = "知识库检索：从当前用户上传/录入的文档中按语义检索最相关片段")
	public String knowledgeQuery(
			@ToolParam(description = "检索查询文本") String query,
			ToolContext toolContext) {
		String argsJson = toJson(query);
		return ToolEventEmitter.from(toolContext).executeWithEvent("knowledge_query", argsJson, toolContext, () -> {
			String userId = (String) toolContext.getContext().get(ToolEventEmitter.CTX_USER_ID);
			String effectiveUser = (userId != null && !userId.isBlank()) ? userId : "default";
			if (query == null || query.isBlank()) {
				throw new IllegalArgumentException("查询文本不能为空");
			}
			FilterExpressionBuilder feb = new FilterExpressionBuilder();
			Expression filter = feb.eq("userId", effectiveUser).build();
			SearchRequest request = SearchRequest.builder().topK(TOP_K).filterExpression(filter).build();
			List<Document> docs = vectorStore.similaritySearch(request);

			List<Map<String, Object>> out = new ArrayList<>();
			if (docs != null) {
				for (Document d : docs) {
					Map<String, Object> item = new LinkedHashMap<>();
					item.put("content", d.getText());
					item.put("metadata", d.getMetadata() == null ? Map.of() : d.getMetadata());
					out.add(item);
				}
			}
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("count", out.size());
			result.put("documents", out);
			log.debug("知识库检索完成 userId={}, hits={}", effectiveUser, out.size());
			try {
				return MAPPER.writeValueAsString(result);
			} catch (JsonProcessingException e) {
				throw new RuntimeException("知识库结果序列化失败", e);
			}
		});
	}

	private static String toJson(String query) {
		try {
			return MAPPER.writeValueAsString(Map.of("query", query == null ? "" : query));
		} catch (Exception e) {
			return "{\"query\":\"\"}";
		}
	}
}
