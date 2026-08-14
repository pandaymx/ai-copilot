package xyz.ppmblszdp.ai.rag.graph.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * 完整知识图谱或子图数据传输对象。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KnowledgeGraphDto(List<KnowledgeEntity> nodes, List<KnowledgeRelation> edges, GraphStatsDto stats) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GraphStatsDto(
            int totalNodes,
            int totalEdges,
            int totalDocuments,
            Map<String, Integer> nodeTypeDistribution,
            Map<String, Integer> relationTypeDistribution) {}
}
