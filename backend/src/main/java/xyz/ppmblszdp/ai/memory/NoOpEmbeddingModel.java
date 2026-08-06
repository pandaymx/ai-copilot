package xyz.ppmblszdp.ai.memory;

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
 * 兜底无操作 EmbeddingModel：用于未配置有效 Embedding API Key 且缺少本地 Embedding 时，
 * 提供常数全零向量，防止框架启动/运行崩溃。
 */
public class NoOpEmbeddingModel implements EmbeddingModel {

	private static final int DEFAULT_DIMENSIONS = 1536;

	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {
		List<String> instructions = (request != null && request.getInstructions() != null)
				? request.getInstructions() : List.of("");
		List<Embedding> embeddings = new ArrayList<>();
		for (int i = 0; i < instructions.size(); i++) {
			embeddings.add(new Embedding(new float[DEFAULT_DIMENSIONS], i));
		}
		return new EmbeddingResponse(embeddings, new EmbeddingResponseMetadata());
	}

	@Override
	public float[] embed(String text) {
		return new float[DEFAULT_DIMENSIONS];
	}

	@Override
	public float[] embed(Document document) {
		return new float[DEFAULT_DIMENSIONS];
	}

	@Override
	public List<float[]> embed(List<String> texts) {
		if (texts == null) {
			return Collections.emptyList();
		}
		return texts.stream().map(t -> new float[DEFAULT_DIMENSIONS]).toList();
	}

	@Override
	public int dimensions() {
		return DEFAULT_DIMENSIONS;
	}
}
