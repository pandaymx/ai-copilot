package xyz.ppmblszdp.ai.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.Collections;
import java.util.List;

/**
 * 容错向量数据库装饰器：当 Embedding API 异常（如 404/401/超时）或 VectorStore 数据库挂掉时，
 * 捕获所有异常并静默降级（检索返回空列表，写入/删除记录日志），防止长期记忆异常打断整个 LLM 会话。
 */
public class SafeVectorStore implements VectorStore {

	private static final Logger log = LoggerFactory.getLogger(SafeVectorStore.class);

	private final VectorStore delegate;

	public SafeVectorStore(VectorStore delegate) {
		this.delegate = delegate;
	}

	@Override
	public void add(List<Document> documents) {
		if (delegate == null) {
			return;
		}
		try {
			delegate.add(documents);
		} catch (Exception e) {
			log.warn("写入长期记忆向量数据库异常（已降级跳过）: {}", e.getMessage());
		}
	}

	@Override
	public void delete(List<String> idList) {
		if (delegate == null) {
			return;
		}
		try {
			delegate.delete(idList);
		} catch (Exception e) {
			log.warn("删除长期记忆向量数据库记录异常（已降级跳过）: {}", e.getMessage());
		}
	}

	@Override
	public void delete(Filter.Expression filterExpression) {
		if (delegate == null) {
			return;
		}
		try {
			delegate.delete(filterExpression);
		} catch (Exception e) {
			log.warn("按条件删除长期记忆向量数据库记录异常（已降级跳过）: {}", e.getMessage());
		}
	}

	@Override
	public List<Document> similaritySearch(String query) {
		if (delegate == null) {
			return Collections.emptyList();
		}
		try {
			return delegate.similaritySearch(query);
		} catch (Exception e) {
			log.warn("长期记忆向量检索异常（已降级为空结果）: {}", e.getMessage());
			return Collections.emptyList();
		}
	}

	@Override
	public List<Document> similaritySearch(SearchRequest request) {
		if (delegate == null) {
			return Collections.emptyList();
		}
		try {
			return delegate.similaritySearch(request);
		} catch (Exception e) {
			log.warn("长期记忆向量检索异常（已降级为空结果）: {}", e.getMessage());
			return Collections.emptyList();
		}
	}

	@Override
	public String getName() {
		return (delegate != null) ? delegate.getName() : "SafeVectorStore";
	}
}
