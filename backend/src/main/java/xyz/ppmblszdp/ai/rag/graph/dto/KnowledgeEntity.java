package xyz.ppmblszdp.ai.rag.graph.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 知识图谱实体（Node）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KnowledgeEntity(
        String id,
        String userId,
        String documentId,
        String name,
        String type, // CONCEPT | TECHNOLOGY | COMPONENT | ORGANIZATION | PERSON | OTHER
        String description,
        Double weight,
        Long createdAt) {

    public KnowledgeEntity(
            String id, String userId, String documentId, String name, String type, String description, Double weight) {
        this(
                id,
                userId,
                documentId,
                name,
                type,
                description,
                weight != null ? weight : 1.0,
                System.currentTimeMillis());
    }
}
