package xyz.ppmblszdp.ai.rag.graph.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 知识图谱关系边（Edge / Triplet）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KnowledgeRelation(
        String id,
        String userId,
        String documentId,
        String sourceEntityName,
        String relation, // DEPENDS_ON | IMPLEMENTS | USES | PART_OF | INTEGRATES_WITH | RELATES_TO
        String targetEntityName,
        String description,
        Double weight,
        Long createdAt) {

    public KnowledgeRelation(
            String id,
            String userId,
            String documentId,
            String sourceEntityName,
            String relation,
            String targetEntityName,
            String description,
            Double weight) {
        this(
                id,
                userId,
                documentId,
                sourceEntityName,
                relation,
                targetEntityName,
                description,
                weight != null ? weight : 1.0,
                System.currentTimeMillis());
    }
}
