package xyz.ppmblszdp.ai.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 容错 EmbeddingModel 装饰器：在底层 Embedding API（如 OpenAI 404/401、网络异常等）失败时，
 * 捕获所有异常并静默降级返回全零向量，防止导致 VectorStore 崩溃进而中断 LLM 聊天。
 */
public class SafeEmbeddingModel implements EmbeddingModel {

	private static final Logger log = LoggerFactory.getLogger(SafeEmbeddingModel.class);
	private static final int DEFAULT_DIMENSIONS = 1536;

	private final EmbeddingModel delegate;

	public SafeEmbeddingModel(EmbeddingModel delegate) {
		this.delegate = delegate;
	}

	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {
		if (delegate == null) {
			return createFallbackResponse(request);
		}
		try {
			return delegate.call(request);
		} catch (Exception e) {
			log.warn("EmbeddingModel 调用异常（如 API Key 未配置/404/网络错误），降级返回全零向量: {}", e.getMessage());
			return createFallbackResponse(request);
		}
	}

	@Override
	public float[] embed(String text) {
		if (delegate == null) {
			return new float[DEFAULT_DIMENSIONS];
		}
		try {
			return delegate.embed(text);
		} catch (Exception e) {
			log.warn("EmbeddingModel.embed(text) 异常，降级返回全零向量: {}", e.getMessage());
			return new float[DEFAULT_DIMENSIONS];
		}
	}

	@Override
	public float[] embed(Document document) {
		if (delegate == null) {
			return new float[DEFAULT_DIMENSIONS];
		}
		try {
			return delegate.embed(document);
		} catch (Exception e) {
			log.warn("EmbeddingModel.embed(document) 异常，降级返回全零向量: {}", e.getMessage());
			return new float[DEFAULT_DIMENSIONS];
		}
	}

	@Override
	public List<float[]> embed(List<String> texts) {
		if (delegate == null || texts == null) {
			return (texts == null) ? Collections.emptyList()
					: texts.stream().map(t -> new float[DEFAULT_DIMENSIONS]).toList();
		}
		try {
			return delegate.embed(texts);
		} catch (Exception e) {
			log.warn("EmbeddingModel.embed(List<String>) 异常，降级返回全零向量: {}", e.getMessage());
			return texts.stream().map(t -> new float[DEFAULT_DIMENSIONS]).toList();
		}
	}

	@Override
	public int dimensions() {
		if (delegate == null) {
			return DEFAULT_DIMENSIONS;
		}
		try {
			return delegate.dimensions();
		} catch (Exception e) {
			return DEFAULT_DIMENSIONS;
		}
	}

	private EmbeddingResponse createFallbackResponse(EmbeddingRequest request) {
		List<String> instructions = (request != null && request.getInstructions() != null)
				? request.getInstructions() : List.of("");
		int dims = dimensions();
		List<Embedding> embeddings = new ArrayList<>();
		for (int i = 0; i < instructions.size(); i++) {
			embeddings.add(new Embedding(new float[dims], i));
		}
		return new EmbeddingResponse(embeddings, new EmbeddingResponseMetadata());
	}
}
