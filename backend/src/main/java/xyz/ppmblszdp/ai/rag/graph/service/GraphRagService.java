package xyz.ppmblszdp.ai.rag.graph.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import xyz.ppmblszdp.ai.rag.graph.dto.KnowledgeEntity;
import xyz.ppmblszdp.ai.rag.graph.dto.KnowledgeGraphDto;
import xyz.ppmblszdp.ai.rag.graph.dto.KnowledgeRelation;
import xyz.ppmblszdp.ai.rag.graph.repository.KnowledgeGraphRepository;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;

/**
 * 知识图谱抽取与 GraphRAG 联合检索核心服务。
 */
@Service
public class GraphRagService {

    private static final Logger log = LoggerFactory.getLogger(GraphRagService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern JSON_BLOCK_PATTERN =
            Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);

    private static final String GRAPH_EXTRACTION_SYSTEM_PROMPT = """
			你是一个专业的知识图谱实体与关系三元组抽取引擎（Knowledge Graph Extraction Engine）。
			请仔细分析给定的文本内容，提取其中的核心知识实体以及实体之间的拓扑关系三元组。

			提取规则：
			1. 实体类型 (type): 必须为 CONCEPT（核心概念）、TECHNOLOGY（技术栈/语言/框架）、COMPONENT（组件/模块）、ORGANIZATION（机构/团队）、PERSON（人物）、OTHER（其他）之一。
			2. 关系谓词 (relation): 必须为 DEPENDS_ON（依赖）、IMPLEMENTS（实现）、USES（使用/调用）、PART_OF（组成部分）、INTEGRATES_WITH（集成关联）、RELATES_TO（业务关联）之一。

			请严格输出标准 JSON 格式：
			{
			  "entities": [
			    {"name": "实体名称", "type": "TECHNOLOGY", "description": "实体描述与定位"}
			  ],
			  "relations": [
			    {"source": "头实体", "relation": "INTEGRATES_WITH", "target": "尾实体", "description": "关系上下文简述"}
			  ]
			}
			""";

    private final KnowledgeGraphRepository graphRepository;
    private final ProviderRegistry providerRegistry;

    public GraphRagService(KnowledgeGraphRepository graphRepository, ProviderRegistry providerRegistry) {
        this.graphRepository = graphRepository;
        this.providerRegistry = providerRegistry;
    }

    /**
     * 对文本执行知识图谱实体与三元组抽取并自动入库。
     */
    public KnowledgeGraphDto extractAndIndex(String rawText, String documentId, String userId) {
        if (rawText == null || rawText.isBlank()) {
            return new KnowledgeGraphDto(List.of(), List.of(), null);
        }

        String docId = (documentId != null && !documentId.isBlank())
                ? documentId
                : "doc-" + UUID.randomUUID().toString().substring(0, 8);

        try {
            ResolvedModel resolved = providerRegistry.resolve(null, null);
            ChatClient client = resolved.chatClient();

            String rawJson = client.prompt()
                    .system(GRAPH_EXTRACTION_SYSTEM_PROMPT)
                    .user("【待抽取文本内容】:\n" + rawText)
                    .call()
                    .content();

            GraphExtractionPayload payload = parsePayload(rawJson);
            if (payload == null) {
                return new KnowledgeGraphDto(List.of(), List.of(), null);
            }

            List<KnowledgeEntity> savedEntities = new ArrayList<>();
            if (payload.entities != null) {
                for (ExtractedEntity e : payload.entities) {
                    if (e.name != null && !e.name.isBlank()) {
                        KnowledgeEntity entity = new KnowledgeEntity(
                                "ent-" + UUID.randomUUID().toString().substring(0, 8),
                                userId,
                                docId,
                                e.name.trim(),
                                e.type != null ? e.type : "CONCEPT",
                                e.description,
                                1.0);
                        graphRepository.saveEntity(entity);
                        savedEntities.add(entity);
                    }
                }
            }

            List<KnowledgeRelation> savedRelations = new ArrayList<>();
            if (payload.relations != null) {
                for (ExtractedRelation r : payload.relations) {
                    if (r.source != null && r.target != null && !r.source.isBlank() && !r.target.isBlank()) {
                        KnowledgeRelation relation = new KnowledgeRelation(
                                "rel-" + UUID.randomUUID().toString().substring(0, 8),
                                userId,
                                docId,
                                r.source.trim(),
                                r.relation != null ? r.relation : "RELATES_TO",
                                r.target.trim(),
                                r.description,
                                1.0);
                        graphRepository.saveRelation(relation);
                        savedRelations.add(relation);
                    }
                }
            }

            log.info(
                    "知识图谱抽取与入库完成: docId={} entities={} relations={}",
                    docId,
                    savedEntities.size(),
                    savedRelations.size());

            return new KnowledgeGraphDto(savedEntities, savedRelations, null);
        } catch (Exception e) {
            log.error("知识图谱抽取异常: {}", e.getMessage(), e);
            return new KnowledgeGraphDto(List.of(), List.of(), null);
        }
    }

    /**
     * GraphRAG 增强上下文检索：识别 query 中涉及的实体，执行多跳子图扩散，生成增强文本。
     */
    public String retrieveGraphContext(String query, String userId, int maxHops) {
        if (query == null || query.isBlank()) {
            return "";
        }

        List<KnowledgeEntity> allEntities = graphRepository.listEntities(userId, null);
        List<String> matchedSeeds = new ArrayList<>();

        for (KnowledgeEntity ent : allEntities) {
            if (query.toLowerCase().contains(ent.name().toLowerCase())) {
                matchedSeeds.add(ent.name());
            }
        }

        if (matchedSeeds.isEmpty()) {
            // 尝试词级别启发式提取
            String[] tokens = query.split("[\\s,，。！？!?;；、]+");
            for (String tok : tokens) {
                if (tok.length() >= 2) {
                    for (KnowledgeEntity ent : allEntities) {
                        if (ent.name().equalsIgnoreCase(tok)) {
                            matchedSeeds.add(ent.name());
                        }
                    }
                }
            }
        }

        if (matchedSeeds.isEmpty()) {
            return "";
        }

        return graphRepository.buildGraphContext(matchedSeeds, userId, maxHops > 0 ? maxHops : 2);
    }

    /**
     * 生成包含图谱上下文的虚拟 Document 供 RAG Advisor 链注入。
     */
    public List<Document> retrieveGraphDocuments(String query, String userId, int maxHops) {
        String graphContext = retrieveGraphContext(query, userId, maxHops);
        if (graphContext.isBlank()) {
            return Collections.emptyList();
        }

        Document graphDoc = new Document(
                "graph-rag-" + UUID.randomUUID().toString().substring(0, 8),
                graphContext,
                Map.of(
                        "sourceType", "KNOWLEDGE_GRAPH",
                        "source", "GraphRAG Multi-Hop Inference",
                        "title", "知识图谱实体拓扑",
                        "userId", userId != null ? userId : "global"));
        return List.of(graphDoc);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class GraphExtractionPayload {
        @JsonProperty("entities")
        public List<ExtractedEntity> entities;

        @JsonProperty("relations")
        public List<ExtractedRelation> relations;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ExtractedEntity {
        @JsonProperty("name")
        public String name;

        @JsonProperty("type")
        public String type;

        @JsonProperty("description")
        public String description;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ExtractedRelation {
        @JsonProperty("source")
        public String source;

        @JsonProperty("relation")
        public String relation;

        @JsonProperty("target")
        public String target;

        @JsonProperty("description")
        public String description;
    }

    private GraphExtractionPayload parsePayload(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            String clean = extractJson(raw);
            return MAPPER.readValue(clean, GraphExtractionPayload.class);
        } catch (Exception e) {
            log.warn("解析图谱抽取 JSON 失败: {}", e.getMessage());
            return null;
        }
    }

    private String extractJson(String raw) {
        Matcher m = JSON_BLOCK_PATTERN.matcher(raw);
        if (m.find()) {
            return m.group(1).trim();
        }
        int start = raw.indexOf("{");
        int end = raw.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1).trim();
        }
        return raw.trim();
    }
}
