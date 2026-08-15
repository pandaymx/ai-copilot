package xyz.ppmblszdp.ai.rag.graph.repository;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import xyz.ppmblszdp.ai.rag.graph.dto.KnowledgeEntity;
import xyz.ppmblszdp.ai.rag.graph.dto.KnowledgeGraphDto;
import xyz.ppmblszdp.ai.rag.graph.dto.KnowledgeGraphDto.GraphStatsDto;
import xyz.ppmblszdp.ai.rag.graph.dto.KnowledgeRelation;

/**
 * 知识图谱实体与关系持久化存储库（支持多租户隔离、文档级联管理与多跳拓扑子图扩散遍历）。
 */
@Repository
public class KnowledgeGraphRepository {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphRepository.class);

    /** 实体索引: key = userId + "::" + normalizedName */
    private final Map<String, KnowledgeEntity> entityStore = new ConcurrentHashMap<>();

    /** 边索引: key = userId + "::" + edgeKey */
    private final Map<String, KnowledgeRelation> relationStore = new ConcurrentHashMap<>();

    public KnowledgeGraphRepository() {
        initDefaultGraph();
    }

    private void initDefaultGraph() {
        // 初始化预置内置知识图谱实体与三元组（用于开箱即用的图谱探索与 GraphRAG 推理）
        saveEntity(new KnowledgeEntity(
                "ent-1", null, "system-seed", "Spring AI", "TECHNOLOGY", "Spring 官方 AI 应用开发框架与客户端统一抽象", 1.0));
        saveEntity(new KnowledgeEntity(
                "ent-2",
                null,
                "system-seed",
                "PgVectorStore",
                "COMPONENT",
                "基于 PostgreSQL pgvector 扩展的向量数据库存储适配器",
                0.95));
        saveEntity(new KnowledgeEntity(
                "ent-3", null, "system-seed", "RAG 知识库", "CONCEPT", "检索增强生成架构，结合向量与图谱检索生成上下文", 1.0));
        saveEntity(new KnowledgeEntity(
                "ent-4", null, "system-seed", "GraphRAG", "CONCEPT", "基于知识图谱多跳推理与实体关系的联合检索增强生成", 1.0));
        saveEntity(new KnowledgeEntity(
                "ent-5", null, "system-seed", "WorkflowEngine", "COMPONENT", "DAG 拓扑调度工作流引擎，支持条件分支与并行扇出", 0.9));
        saveEntity(new KnowledgeEntity(
                "ent-6",
                null,
                "system-seed",
                "EvaluationArena",
                "COMPONENT",
                "基于 LLM-as-Judge 的自动化评测与 A/B 盲测竞技场",
                0.85));

        saveRelation(new KnowledgeRelation(
                "rel-1",
                null,
                "system-seed",
                "Spring AI",
                "INTEGRATES_WITH",
                "PgVectorStore",
                "Spring AI 内置提供 PgVectorStore 向量检索组件",
                1.0));
        saveRelation(new KnowledgeRelation(
                "rel-2",
                null,
                "system-seed",
                "Spring AI",
                "IMPLEMENTS",
                "RAG 知识库",
                "通过 Advisor 链与 VectorStore 实现 RAG 检索管道",
                0.95));
        saveRelation(new KnowledgeRelation(
                "rel-3",
                null,
                "system-seed",
                "RAG 知识库",
                "EXTENDS_TO",
                "GraphRAG",
                "知识图谱关系建模将传统向量 RAG 升级为 GraphRAG",
                1.0));
        saveRelation(new KnowledgeRelation(
                "rel-4",
                null,
                "system-seed",
                "GraphRAG",
                "USES",
                "PgVectorStore",
                "GraphRAG 联合 PgVector 向量检索与实体拓扑遍历",
                0.9));
        saveRelation(new KnowledgeRelation(
                "rel-5",
                null,
                "system-seed",
                "Spring AI",
                "INTEGRATES_WITH",
                "WorkflowEngine",
                "工作流引擎调度 LLM 节点与 Agent 工具节点",
                0.9));
    }

    public KnowledgeEntity saveEntity(KnowledgeEntity entity) {
        if (entity == null || entity.name() == null || entity.name().isBlank()) {
            return null;
        }
        String key = buildEntityKey(entity.userId(), entity.name());
        entityStore.put(key, entity);
        return entity;
    }

    public KnowledgeRelation saveRelation(KnowledgeRelation relation) {
        if (relation == null || relation.sourceEntityName() == null || relation.targetEntityName() == null) {
            return null;
        }
        String key = buildRelationKey(
                relation.userId(), relation.sourceEntityName(), relation.relation(), relation.targetEntityName());
        relationStore.put(key, relation);
        return relation;
    }

    public List<KnowledgeEntity> listEntities(String userId, String documentId) {
        return entityStore.values().stream()
                .filter(e -> matchUser(e.userId(), userId))
                .filter(e -> documentId == null || documentId.isBlank() || documentId.equalsIgnoreCase(e.documentId()))
                .toList();
    }

    public List<KnowledgeRelation> listRelations(String userId, String documentId) {
        return relationStore.values().stream()
                .filter(r -> matchUser(r.userId(), userId))
                .filter(r -> documentId == null || documentId.isBlank() || documentId.equalsIgnoreCase(r.documentId()))
                .toList();
    }

    public int deleteByDocumentId(String documentId, String userId) {
        if (documentId == null || documentId.isBlank()) return 0;
        int count = 0;
        List<String> entitiesToRemove = entityStore.entrySet().stream()
                .filter(e -> matchUser(e.getValue().userId(), userId)
                        && documentId.equalsIgnoreCase(e.getValue().documentId()))
                .map(e -> e.getKey())
                .toList();
        for (String k : entitiesToRemove) {
            entityStore.remove(k);
            count++;
        }

        List<String> relationsToRemove = relationStore.entrySet().stream()
                .filter(e -> matchUser(e.getValue().userId(), userId)
                        && documentId.equalsIgnoreCase(e.getValue().documentId()))
                .map(e -> e.getKey())
                .toList();
        for (String k : relationsToRemove) {
            relationStore.remove(k);
            count++;
        }
        log.info("文档关联知识图谱已删除: documentId={} removedItems={}", documentId, count);
        return count;
    }

    /**
     * 多跳拓扑子图扩散遍历 (Multi-Hop Subgraph Traversal)。
     *
     * @param seedEntityNames 起始种子实体名称集合
     * @param userId 租户过滤
     * @param maxHops 最大跳数（1 ~ 3，默认 2）
     * @param maxNodes 最大节点数防爆炸上限（默认 50）
     * @return 拓扑子图 DTO
     */
    public KnowledgeGraphDto extractSubgraph(List<String> seedEntityNames, String userId, int maxHops, int maxNodes) {
        if (seedEntityNames == null || seedEntityNames.isEmpty()) {
            return getFullGraph(userId, null);
        }

        int hopsLimit = Math.max(1, Math.min(maxHops, 3));
        int nodeCap = maxNodes > 0 ? Math.min(maxNodes, 100) : 50;

        Set<String> visitedEntityNames = new HashSet<>();
        Queue<String> currentLevel = new ArrayDeque<>();

        for (String seed : seedEntityNames) {
            if (seed != null && !seed.isBlank()) {
                String norm = normalizeName(seed);
                visitedEntityNames.add(norm);
                currentLevel.add(norm);
            }
        }

        List<KnowledgeRelation> allRelations = listRelations(userId, null);
        Set<KnowledgeRelation> matchedEdges = new HashSet<>();

        int currentHop = 0;
        while (!currentLevel.isEmpty() && currentHop < hopsLimit && visitedEntityNames.size() < nodeCap) {
            int levelSize = currentLevel.size();
            for (int i = 0; i < levelSize; i++) {
                String currentName = currentLevel.poll();
                for (KnowledgeRelation edge : allRelations) {
                    String srcNorm = normalizeName(edge.sourceEntityName());
                    String tgtNorm = normalizeName(edge.targetEntityName());

                    if (srcNorm.equalsIgnoreCase(currentName)) {
                        matchedEdges.add(edge);
                        if (!visitedEntityNames.contains(tgtNorm) && visitedEntityNames.size() < nodeCap) {
                            visitedEntityNames.add(tgtNorm);
                            currentLevel.add(tgtNorm);
                        }
                    } else if (tgtNorm.equalsIgnoreCase(currentName)) {
                        matchedEdges.add(edge);
                        if (!visitedEntityNames.contains(srcNorm) && visitedEntityNames.size() < nodeCap) {
                            visitedEntityNames.add(srcNorm);
                            currentLevel.add(srcNorm);
                        }
                    }
                }
            }
            currentHop++;
        }

        List<KnowledgeEntity> matchedNodes = visitedEntityNames.stream()
                .map(name -> findEntityByName(name, userId))
                .filter(java.util.Objects::nonNull)
                .toList();

        return new KnowledgeGraphDto(
                matchedNodes, new ArrayList<>(matchedEdges), computeStats(matchedNodes, new ArrayList<>(matchedEdges)));
    }

    public KnowledgeGraphDto getFullGraph(String userId, String documentId) {
        List<KnowledgeEntity> nodes = listEntities(userId, documentId);
        List<KnowledgeRelation> edges = listRelations(userId, documentId);
        return new KnowledgeGraphDto(nodes, edges, computeStats(nodes, edges));
    }

    /**
     * 生成供 GraphRAG 注入 Prompt 的结构化图谱上下文文本。
     */
    public String buildGraphContext(List<String> seedEntityNames, String userId, int maxHops) {
        KnowledgeGraphDto subgraph = extractSubgraph(seedEntityNames, userId, maxHops, 30);
        if (subgraph.nodes().isEmpty() && subgraph.edges().isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【知识图谱关联实体与拓扑关系 (GraphRAG Context)】:\n");

        if (!subgraph.nodes().isEmpty()) {
            sb.append("1. 关键实体列表:\n");
            for (KnowledgeEntity node : subgraph.nodes()) {
                sb.append("   - ")
                        .append(node.name())
                        .append(" [")
                        .append(node.type())
                        .append("]");
                if (node.description() != null && !node.description().isBlank()) {
                    sb.append(": ").append(node.description());
                }
                sb.append("\n");
            }
        }

        if (!subgraph.edges().isEmpty()) {
            sb.append("2. 实体关系三元组:\n");
            for (KnowledgeRelation edge : subgraph.edges()) {
                sb.append("   - (")
                        .append(edge.sourceEntityName())
                        .append(") --[")
                        .append(edge.relation())
                        .append("]--> (")
                        .append(edge.targetEntityName())
                        .append(")");
                if (edge.description() != null && !edge.description().isBlank()) {
                    sb.append(" (说明: ").append(edge.description()).append(")");
                }
                sb.append("\n");
            }
        }

        return sb.toString().trim();
    }

    private KnowledgeEntity findEntityByName(String name, String userId) {
        String key = buildEntityKey(userId, name);
        KnowledgeEntity ent = entityStore.get(key);
        if (ent != null) return ent;

        // 允许全局种子实体
        String seedKey = buildEntityKey(null, name);
        ent = entityStore.get(seedKey);
        if (ent != null) return ent;

        // 容错模糊匹配
        String norm = normalizeName(name);
        return entityStore.values().stream()
                .filter(e ->
                        matchUser(e.userId(), userId) && normalizeName(e.name()).equalsIgnoreCase(norm))
                .findFirst()
                .orElseGet(() ->
                        new KnowledgeEntity("ent-virt-" + norm, userId, "virtual", name, "CONCEPT", "关联概念实体", 0.8));
    }

    private GraphStatsDto computeStats(List<KnowledgeEntity> nodes, List<KnowledgeRelation> edges) {
        Map<String, Integer> nodeTypes =
                nodes.stream().collect(Collectors.groupingBy(n -> n.type(), Collectors.summingInt(x -> 1)));
        Map<String, Integer> relTypes =
                edges.stream().collect(Collectors.groupingBy(r -> r.relation(), Collectors.summingInt(x -> 1)));

        long docCount = nodes.stream()
                .map(n -> n.documentId())
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();

        return new GraphStatsDto(nodes.size(), edges.size(), (int) docCount, nodeTypes, relTypes);
    }

    private String buildEntityKey(String userId, String name) {
        return (userId != null ? userId : "global") + "::" + normalizeName(name);
    }

    private String buildRelationKey(String userId, String src, String rel, String tgt) {
        return (userId != null ? userId : "global") + "::" + normalizeName(src) + "::"
                + (rel != null ? rel.toUpperCase().trim() : "RELATES_TO") + "::" + normalizeName(tgt);
    }

    private String normalizeName(String name) {
        return name != null ? name.trim().toLowerCase() : "";
    }

    private boolean matchUser(String itemUser, String targetUser) {
        if (itemUser == null || itemUser.isBlank() || "global".equalsIgnoreCase(itemUser)) {
            return true; // 全局预置数据对所有租户可见
        }
        return targetUser != null && targetUser.equalsIgnoreCase(itemUser);
    }
}
