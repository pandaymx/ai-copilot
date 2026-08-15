package xyz.ppmblszdp.ai.rag.rerank;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

/**
 * RAG Reranker 精排服务接口与默认实现。
 *
 * <p>在双路召回与 RRF 融合之后，可选对候选文档进行重排序（Reranking），以提高 Top-K 的精密相关度。
 */
public interface RagReranker {

    /**
     * 对候选文档列表重排序并截取 Top-K。
     *
     * @param query      查询文本
     * @param candidates 候选文档列表
     * @param topK       目标返回文档数
     * @return 精排后的文档列表
     */
    List<Document> rerank(String query, List<Document> candidates, int topK);

    /**
     * 默认 Reranker 实现：基于词项重合度与 RRF 基础分的轻量重排序。
     */
    @Component
    class DefaultRagReranker implements RagReranker {

        @Override
        public List<Document> rerank(String query, List<Document> candidates, int topK) {
            if (candidates == null || candidates.isEmpty()) {
                return List.of();
            }
            int k = (topK > 0) ? topK : candidates.size();
            if (candidates.size() <= k) {
                return candidates;
            }

            List<DocumentScore> scored = new ArrayList<>();
            String lowerQuery = query.toLowerCase();

            for (Document doc : candidates) {
                double rrfScore = getMetadataDouble(doc, "rrfScore", 0.0);
                double textMatchScore =
                        computeTextMatchScore(lowerQuery, doc.getText().toLowerCase());
                double finalScore = rrfScore * 0.7 + textMatchScore * 0.3;

                Map<String, Object> meta = new java.util.HashMap<>(doc.getMetadata());
                meta.put("rerankScore", finalScore);
                scored.add(new DocumentScore(new Document(doc.getId(), doc.getText(), meta), finalScore));
            }

            scored.sort((a, b) -> Double.compare(b.score(), a.score()));
            return scored.stream().map(s -> s.doc()).limit(k).toList();
        }

        private double computeTextMatchScore(String query, String text) {
            if (query.isBlank() || text.isBlank()) {
                return 0.0;
            }
            String[] tokens = query.split("\\s+");
            int matches = 0;
            for (String t : tokens) {
                if (!t.isBlank() && text.contains(t)) {
                    matches++;
                }
            }
            return (double) matches / Math.max(1, tokens.length);
        }

        private double getMetadataDouble(Document doc, String key, double defaultVal) {
            Object val = doc.getMetadata().get(key);
            if (val instanceof Number) {
                return ((Number) val).doubleValue();
            }
            return defaultVal;
        }

        private record DocumentScore(Document doc, double score) {}
    }
}
